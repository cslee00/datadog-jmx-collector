package org.jmx;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jmx.config.ConfigurationLoader;
import org.jmx.config.JmxAttribute;
import org.jmx.config.JvmInstanceConfiguration;
import org.jmx.config.MetricQuery;
import org.jmx.config.MetricType;
import org.jmx.connection.JmxConnectionOperations;
import org.jmx.connection.JmxConnectionStateResolver;
import org.jmx.connection.UnableToAttachException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.sun.tools.attach.VirtualMachineDescriptor;
import com.timgroup.statsd.StatsDClient;

import sun.tools.attach.WindowsAttachProvider;

public final class JmxCollector implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger( getClass() );
    private final TaskScheduler taskScheduler;
    private final TaskExecutor taskExecutor;
    private final JmxConnectionStateResolver jmxConnectionStateResolver;
    private final int rateMs;
    private final StatsDClient statsDClient;
    private final ObjectMapper objectMapper;
    private final String configFile;
    private final MetricsMXBean collectorMetrics;

    public JmxCollector( MetricsMXBean metricsMXBean, String configFile, TaskScheduler taskScheduler, TaskExecutor taskExecutor, int rateMs,
      JmxConnectionStateResolver jmxConnectionStateResolver, StatsDClient statsDClient, ObjectMapper objectMapper ) {
        this.taskScheduler = checkNotNull( taskScheduler, "taskScheduler is required" );
        this.taskExecutor = checkNotNull( taskExecutor, "taskExecutor is required" );
        this.jmxConnectionStateResolver = checkNotNull( jmxConnectionStateResolver, "jmxConnectionStateResolver is required" );
        this.statsDClient = checkNotNull( statsDClient, "statsDClient is required" );
        checkArgument( rateMs > 0, "rateMs must be > 0: %s", rateMs );
        this.rateMs = rateMs;
        this.objectMapper = checkNotNull( objectMapper, "objectMapper is required" );
        this.configFile = checkNotNull( configFile, "configFile is required" );
        this.collectorMetrics = checkNotNull( metricsMXBean, "metricsMXBean is required" );
    }

    @Override
    public void run( ApplicationArguments args ) throws Exception {
        System.setProperty( "sun.tools.attach.attachTimeout", "100" );

        logger.info( "Non-option args: {}", args.getNonOptionArgs() );
        logger.info( "Option names: {}", args.getOptionNames() );
        logger.info( "JVM home: {}", System.getProperty( "java.home" ) );

        if( args.getNonOptionArgs().contains( "list" ) ) {
            try {
                doList();
            } catch( Throwable e ) {
                logger.error( "Error listing JVMs", e );
            }
            System.exit(0);
        }

        ConfigurationLoader configurationLoader = new ConfigurationLoader( objectMapper );

        List<JvmInstanceConfiguration> jvmInstanceConfigurations = configurationLoader.loadConfiguration( new FileSystemResource( configFile ) );

        logger.info( "Starting up; polling JVMs every {}ms", rateMs );
        taskScheduler.scheduleAtFixedRate( () -> {
            List<VirtualMachineDescriptor> descriptors = com.sun.tools.attach.VirtualMachine.list();
            descriptors.stream().forEach( descriptor -> {
                taskExecutor.execute( () -> pollMetrics( descriptor, jvmInstanceConfigurations ) );
            } );
        }, rateMs );
    }

    private List<String> gatherAttributes( final MBeanServerConnection mBeanServer, final ObjectName objectName )
      throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
        MBeanInfo info = mBeanServer.getMBeanInfo( objectName );
        MBeanAttributeInfo[] attrInfo = info.getAttributes();

        List<String> attributes = new ArrayList<>();
        for( MBeanAttributeInfo attr : attrInfo ) {
            try {
                Object obj = mBeanServer.getAttribute( objectName, attr.getName() );
                if( obj instanceof Number ) {
                    attributes.add( String.format( "%s=%s", attr.getName(), obj ) );
                }
                if( obj instanceof CompositeDataSupport ) {

                    CompositeDataSupport cd = (CompositeDataSupport) obj;
                    for( String item : cd.getCompositeType().keySet() ) {
                        Object obj1 = cd.get( item );
                        if( obj1 instanceof Number ) {
                            attributes.add( String.format( "%s.%s=%s", attr.getName(), item, obj1 ) );
                        }
                    }
                }
            } catch( Exception e ) {
                attributes.add( String.format( "%s=(error: %s)", attr.getName(), e.getMessage() ) );
            }
        }

        return ImmutableList.copyOf( attributes );
    }

    private void doList() {
        logger.info( "Listing JVMs..." );
        List<VirtualMachineDescriptor> descriptors = com.sun.tools.attach.VirtualMachine.list();
        for( VirtualMachineDescriptor vmd : descriptors ) {
            try {
                com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach( vmd );
                String connectorAddress = vm.getAgentProperties().getProperty( JmxUtils.CONNECTOR_ADDRESS );
                //If jmx agent is not running in VM, load it
                if( connectorAddress == null ) {
                    logger.info( "Loading JMX agent for JVM {}", vmd.id() );
                    JmxUtils.loadJMXAgent( vm );

                    // agent is started, get the connector address
                    connectorAddress = vm.getAgentProperties().getProperty( JmxUtils.CONNECTOR_ADDRESS );
                }

                JMXServiceURL jmxUrl = new JMXServiceURL( connectorAddress );
                logger.info( "Connecting to JVM {} via {}", vmd, jmxUrl );

                try( JMXConnector connector = JMXConnectorFactory.connect( jmxUrl ) ) {
                    final MBeanServerConnection mbeanServerConnection = connector.getMBeanServerConnection();

                    logger.info("#appArgs={}",vm.getAgentProperties().getProperty( "sun.java.command" ) );
                    logger.info("#systemProps={}",vm.getSystemProperties());

                    Set<ObjectName> objectNames = new TreeSet<>( mbeanServerConnection.queryNames( null, null ) );
                    for( ObjectName objectName : objectNames ) {
                        List<String> attributes = gatherAttributes( mbeanServerConnection, objectName );
                        System.out.printf( "\t%s\n", objectName );
                        for( String attribute : attributes ) {
                            System.out.printf( "\t\t%s\n", attribute );
                        }
                    }
                }
            } catch( Exception e ) {
                logger.warn( "Unable to connect to JVM {} {} : {}", vmd.id(), vmd.displayName(), e.getMessage() );
            }
        }
    }

    private final Cache<String, Boolean> failedAttachCache = CacheBuilder.newBuilder().expireAfterWrite( 30, TimeUnit.MINUTES ).maximumSize( 2000 ).build();

    private void pollMetrics( VirtualMachineDescriptor descriptor, List<JvmInstanceConfiguration> jvmInstanceConfigurations ) {

        try {
            if( failedAttachCache.getIfPresent( descriptor.id() ) != null ) {
                logger.debug( "Skipping due to previous failures / configuration: {}", descriptor );
                return;
            }

            logger.debug( "Checking connectivity to {}", descriptor.displayName() );
            JmxConnectionOperations jmxConnectionOperations = jmxConnectionStateResolver.resolveJmxConnectionState( descriptor, jvmInstanceConfigurations );
            collectorMetrics.incrementServerPolls();

            Stopwatch metricPollStopwatch = Stopwatch.createStarted();
            int metricsPolled = 0;
            for( MetricQuery metricQuery : jmxConnectionOperations.getJvmInstanceConfiguration().getMetricSet() ) {
                metricsPolled += resolveMetricQuery( metricQuery, jmxConnectionOperations );
            }

            metricPollStopwatch.stop();
            logger.info( "Polled {} metrics in {}ms {}", metricsPolled, metricPollStopwatch.elapsed( TimeUnit.MILLISECONDS ), jmxConnectionOperations.getJvmInstanceTags() );
        } catch( UnableToAttachException ignored ) {
            logger.debug( "Unable to attach to {} {}", descriptor.displayName(), ignored );
            failedAttachCache.put( descriptor.id(), true );
        } catch( IOException | InstanceNotFoundException | ReflectionException e ) {
            logger.error( "Error attaching to {}", e );
            failedAttachCache.put( descriptor.id(), true );
        }
    }

    private int resolveMetricQuery( MetricQuery metricQuery, JmxConnectionOperations jmxConnectionOperations )
      throws IOException, ReflectionException, InstanceNotFoundException {
        int metricsPolled = 0;
        Set<ObjectName> matchingObjectNames = jmxConnectionOperations.queryNames( metricQuery.getPattern() );

        for( ObjectName objectName : matchingObjectNames ) {
            AttributeList list = jmxConnectionOperations.getAttributes( objectName, metricQuery.getUniqueAttributeNames() );
            metricsPolled++;
            for( Object attr : list ) {
                try {
                    processAttribute( metricQuery, objectName, (Attribute) attr, jmxConnectionOperations.getJvmInstanceTags() );
                } catch( Exception e ) {
                    logger.error( "Error processing attribute {}", attr, e );
                }
            }
        }
        return metricsPolled;
    }

    private void processAttribute( MetricQuery metricQuery, ObjectName objectName, Attribute attr, String[] tags ) {
        Collection<JmxAttribute> jmxAttributes = metricQuery.findAttributesFor( attr.getName() );
        Object value = attr.getValue();
        if( value instanceof CompositeData ) {
            CompositeData data = (CompositeData) value;
            // pull out each metric from composite object
            for( JmxAttribute jmxAttribute : jmxAttributes ) {
                sendMetric( jmxAttribute, objectName, data.get( jmxAttribute.getNestedName() ), tags );
            }
        } else {
            if( value instanceof Number ) {
                JmxAttribute jmxAttribute = Iterables.getFirst( jmxAttributes, null );
                sendMetric( jmxAttribute, objectName, attr.getValue(), tags );
            } else {
                throw new RuntimeException( "Unknown attribute type: " + value.getClass() );
            }
        }
        collectorMetrics.getMetricsCollected();
    }

    private void sendMetric( JmxAttribute jmxAttribute, ObjectName objectName, Object value, String... tags ) {
        String alias = resolveAlias( jmxAttribute, objectName );

        Double v = null;
        if( value instanceof Number ) {
            v = ((Number) value).doubleValue();
        } else {
            logger.warn( "Skipping unknown metric type {}", value.getClass() );
            return;
        }

        if( jmxAttribute.getType() == MetricType.GAUGE ) {
            statsDClient.gauge( alias, v, tags );
        } else {
            statsDClient.count( alias, v.longValue(), tags );
        }

        logger.debug( "{} -> {}={} {}", objectName, alias, value, tags );
    }

    private static final Pattern ALIAS_PATTERN = Pattern.compile( "#\\{(.*?)\\}" );

    private Cache<String, String> resolvedAliasCache = CacheBuilder.newBuilder().maximumSize( 1000 ).build();

    private String resolveAlias( JmxAttribute jmxAttribute, ObjectName objectName ) {

        String key = jmxAttribute.getAlias() + objectName + jmxAttribute.getName() + jmxAttribute.getNestedName();
        String resolvedAlias = resolvedAliasCache.getIfPresent( key );
        if( resolvedAlias == null ) {
            Matcher m = ALIAS_PATTERN.matcher( jmxAttribute.getAlias() );

            StringBuffer sb = new StringBuffer( jmxAttribute.getAlias().length() + 10 );
            while( m.find() ) {
                switch( m.group( 1 ) ) {
                    case "name":
                    case "type": {
                        String replacement = objectName.getKeyProperty( m.group( 1 ) );
                        replacement = replacement.replaceAll( "[^a-zA-Z0-9]", "_" );

                        replacement = replacement.replaceAll( "([a-z])([A-Z])", "$1_$2" );
                        m.appendReplacement( sb, replacement.toLowerCase() );

                        break;
                    }
                    case "attr": {
                        String replacement = jmxAttribute.getName();
                        if( jmxAttribute.getNestedName() != null ) {
                            replacement = String.format( "%s.%s", replacement, jmxAttribute.getNestedName() );
                        }
                        replacement = replacement.replaceAll( "([a-z])([A-Z])", "$1_$2" );
                        m.appendReplacement( sb, replacement.toLowerCase() );
                        break;
                    }
                    default:
                        break;
                }
            }
            m.appendTail( sb );
            resolvedAlias = sb.toString();
            logger.debug( "Resolved alias {} with object name {} -> {}", jmxAttribute.getAlias(), objectName, resolvedAlias );
            resolvedAliasCache.put( key, resolvedAlias );
        }
        return resolvedAlias;
    }
}
