package org.jmx.connection;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public final class AttachApiConnector implements VirtualMachineConnector {
    private final VirtualMachineDescriptor descriptor;
    private final Logger logger = LoggerFactory.getLogger( getClass() );
    private final List<JvmInstanceConfiguration> jvmInstanceConfigurations;

    public AttachApiConnector( VirtualMachineDescriptor descriptor, List<JvmInstanceConfiguration> jvmInstanceConfigurations ) {
        this.descriptor = checkNotNull( descriptor, "descriptor is required" );
        this.jvmInstanceConfigurations = checkNotNull( jvmInstanceConfigurations, "jvmInstanceConfigurations is required" );
    }

    private JvmInstanceConfiguration resolveConfiguration( VirtualMachine vm, EvaluationContext ctx ) {
        for( JvmInstanceConfiguration jvmInstanceConfiguration : jvmInstanceConfigurations ) {
            if( jvmInstanceConfiguration.getJvmSelector() == null ) {
                continue;
            }
            logger.debug( "Evaluating {}", jvmInstanceConfiguration.getJvmSelector().getExpressionString() );
            if( jvmInstanceConfiguration.getJvmSelector().getValue( ctx, Boolean.class ) ) {
                logger.info( "Matched {} to {}", jvmInstanceConfiguration.getJvmSelector().getExpressionString(), vm );
                return jvmInstanceConfiguration;
            }
        }
        logger.debug( "No match for {}", vm );
        return null;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper( this ).add( "pid", descriptor.id() ).add("displayName", descriptor.displayName()).toString();
    }

    @Override
    public boolean equals( Object o ) {
        if( this == o ) {
            return true;
        }
        if( o == null || getClass() != o.getClass() ) {
            return false;
        }
        AttachApiConnector that = (AttachApiConnector) o;
        return Objects.equal( descriptor, that.descriptor );
    }

    @Override
    public int hashCode() {
        return Objects.hashCode( descriptor );
    }

    @Override
    public JmxConnection connect() {
        try {
            com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach( descriptor );

            Stopwatch sw = Stopwatch.createStarted();
            // TODO - timeout
            // https://community.oracle.com/blogs/emcmanus/2007/05/23/making-jmx-connection-timeout

            JMXServiceURL jmxUrl = JmxUtils.determineServiceUrl( vm );
            logger.info( "Connecting to JVM {} via {}", descriptor, jmxUrl );

            JMXConnector connector = JMXConnectorFactory.connect( jmxUrl );
            final MBeanServerConnection mbeanServerConnection = connector.getMBeanServerConnection();

            sw.stop();
            logger.info( "Connected to '{}' in {}ms", descriptor, sw.elapsed( TimeUnit.MILLISECONDS ) );


            return new JmxConnection( connector, mbeanServerConnection, createConnectionMetaData( vm ) );
        } catch( Exception e ) {
            throw Throwables.propagate( e );
        }
    }

    private ConnectionMetaData createConnectionMetaData( VirtualMachine vm ) throws IOException {
        String appArgs = vm.getAgentProperties().getProperty( "sun.java.command" );
        Properties systemProperties = vm.getSystemProperties();
        EvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable( "appArgs", appArgs );
        ctx.setVariable( "systemProps",systemProperties );

        JvmInstanceConfiguration jvmInstanceConfiguration = resolveConfiguration( vm, ctx  );

        String jvmInstanceName = null;
        if( jvmInstanceConfiguration != null ) {
            jvmInstanceName = jvmInstanceConfiguration.getJvmNameExtractor().getValue( ctx, String.class );
        }

        return new ConnectionMetaData( appArgs, systemProperties, ctx, jvmInstanceConfiguration, jvmInstanceName );
    }
}
