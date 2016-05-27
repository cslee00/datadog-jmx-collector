package org.jmx.connection;

import static com.google.common.base.Preconditions.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jmx.JmxUtils;
import org.jmx.config.JvmInstanceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachineDescriptor;

public final class JmxConnectionStateResolver {
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final LoadingCache<CacheKey, JmxConnectionOperations> cache;

    public JmxConnectionStateResolver( int pollRateMs ) {
        cache = CacheBuilder.newBuilder().expireAfterAccess( pollRateMs * 3, TimeUnit.MILLISECONDS ).removalListener(
          new RemovalListener<CacheKey, JmxConnectionOperations>() {
              @Override
              public void onRemoval( RemovalNotification<CacheKey, JmxConnectionOperations> notification ) {
                  try {
                      if( notification.getValue() != null ) {
                          logger.info( "Removing idle connection to {}", notification.getKey().vmd );
                          notification.getValue().getConnector().close();
                      }
                  } catch( IOException e ) {
                      logger.error( "Error closing connection to {}", notification.getKey(), e );
                  }
              }
          } ).build( new CacheLoader<CacheKey, JmxConnectionOperations>() {
            @Override
            public JmxConnectionOperations load( CacheKey key ) throws Exception {
                return establishConnection( key.vmd, key.jvmInstanceConfigurations );
            }
        } );
    }

    private static class CacheKey {
        private final VirtualMachineDescriptor vmd;
        private final List<JvmInstanceConfiguration> jvmInstanceConfigurations;

        private CacheKey( VirtualMachineDescriptor vmd, List<JvmInstanceConfiguration> jvmInstanceConfigurations ) {
            this.vmd = checkNotNull( vmd, "vmd is required" );
            this.jvmInstanceConfigurations = checkNotNull( jvmInstanceConfigurations, "jvmInstanceConfigurations is required" );
        }

        @Override
        public boolean equals( Object o ) {
            if( this == o ) {
                return true;
            }
            if( o == null || getClass() != o.getClass() ) {
                return false;
            }
            CacheKey cacheKey = (CacheKey) o;
            return Objects.equal( vmd, cacheKey.vmd );
        }

        @Override
        public int hashCode() {
            return Objects.hashCode( vmd );
        }
    }

    public JmxConnectionOperations resolveJmxConnectionState( VirtualMachineDescriptor vmd, List<JvmInstanceConfiguration> jvmInstanceConfigurations )
      throws UnableToAttachException {
        try {
            return cache.get( new CacheKey( vmd, jvmInstanceConfigurations ) );
        } catch( ExecutionException e ) {
            throw new UnableToAttachException( String.format( "Unable to attach to JVM '%s' (id=%s)", vmd.displayName(), vmd.id() ), e.getCause() );
        }
    }



    private JmxConnectionOperations establishConnection( VirtualMachineDescriptor vmd, List<JvmInstanceConfiguration> jvmInstanceConfigurations )
      throws AgentLoadException, AgentInitializationException, IOException, AttachNotSupportedException, UnableToAttachException {
        logger.debug( "Attaching to JVM {}", vmd.displayName() );

        com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach( vmd );
        EvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable( "appArgs", vm.getAgentProperties().getProperty( "sun.java.command" ) );
        ctx.setVariable( "systemProps", vm.getSystemProperties() );
        JvmInstanceConfiguration jvmInstanceConfiguration = resolveConfiguration( vmd, jvmInstanceConfigurations, ctx );
        if( jvmInstanceConfiguration != null ) {

            String jvmInstanceName = jvmInstanceConfiguration.getJvmNameExtractor().getValue( ctx, String.class );

            // TODO - add hostname to jvm instance name

            String connectorAddress = vm.getAgentProperties().getProperty( JmxUtils.CONNECTOR_ADDRESS );
            //If jmx agent is not running in VM, load it
            if( connectorAddress == null ) {
                logger.info( "Loading JMX agent for {}", vmd );
                JmxUtils.loadJMXAgent( vm );

                // agent is started, get the connector address
                connectorAddress = vm.getAgentProperties().getProperty( JmxUtils.CONNECTOR_ADDRESS );
            }

            JMXServiceURL jmxUrl = new JMXServiceURL( connectorAddress );
            logger.info( "Connecting to '{}' via {}", vmd, jmxUrl );

            Stopwatch sw = Stopwatch.createStarted();
            // TODO - timeout
            // https://community.oracle.com/blogs/emcmanus/2007/05/23/making-jmx-connection-timeout
            JMXConnector connector = JMXConnectorFactory.connect( jmxUrl );

            final MBeanServerConnection mbeanServerConnection = connector.getMBeanServerConnection();

            sw.stop();
            logger.info( "Connected to '{}' in {}ms", vmd, sw.elapsed( TimeUnit.MILLISECONDS ) );
            return new JmxConnectionOperations( connector, mbeanServerConnection, jvmInstanceConfiguration, jvmInstanceName );
        }
        logger.debug( "Did not match: {} {}", vmd.id(), vm.getAgentProperties() );
        throw new UnableToAttachException( "Skipped" );
    }

    private JvmInstanceConfiguration resolveConfiguration( VirtualMachineDescriptor vmd, List<JvmInstanceConfiguration> jvmInstanceConfigurations, EvaluationContext ctx ) {
        for( JvmInstanceConfiguration jvmInstanceConfiguration : jvmInstanceConfigurations ) {
            logger.debug( "Evaluating {}", jvmInstanceConfiguration.getJvmSelector().getExpressionString() );
            if( jvmInstanceConfiguration.getJvmSelector().getValue( ctx, Boolean.class ) ) {
                logger.info( "Matched {} to {}", jvmInstanceConfiguration.getJvmSelector().getExpressionString(), vmd );
                return jvmInstanceConfiguration;
            }
        }
        logger.debug( "No match for {}", vmd );
        return null;
    }
}
