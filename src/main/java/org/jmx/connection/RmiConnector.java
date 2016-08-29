package org.jmx.connection;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jmx.config.JvmInstanceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;

public final class RmiConnector implements VirtualMachineConnector {
    private final Logger logger = LoggerFactory.getLogger( getClass() );
    private final JvmInstanceConfiguration jvmInstanceConfiguration;
    private final int jmxPort;

    public RmiConnector( int port, JvmInstanceConfiguration jvmInstanceConfiguration ) {
        checkNotNull( jvmInstanceConfiguration, "jvmInstanceConfiguration is required" );
        checkNotNull( jvmInstanceConfiguration.getJmxPortRange(), "jvmInstanceConfiguration.getJmxPortRange() is required" );
        checkArgument( port > 1024 && port <= 65535, "port %s out of range", port );
        this.jvmInstanceConfiguration = jvmInstanceConfiguration;
        this.jmxPort = port;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper( this ).add( "jmxPort", jmxPort ).toString();
    }

    @Override
    public boolean equals( Object o ) {
        if( this == o ) {
            return true;
        }
        if( o == null || getClass() != o.getClass() ) {
            return false;
        }
        RmiConnector that = (RmiConnector) o;
        return Objects.equal( jmxPort, that.jmxPort );
    }

    @Override
    public int hashCode() {
        return Objects.hashCode( jmxPort );
    }

    @Override
    public JmxConnection connect() {
        try {
            Stopwatch sw = Stopwatch.createStarted();
            // TODO - timeout
            // https://community.oracle.com/blogs/emcmanus/2007/05/23/making-jmx-connection-timeout

            JMXServiceURL jmxUrl = new JMXServiceURL( String.format( "service:jmx:rmi:///jndi/rmi://:%d/jmxrmi", jmxPort ) );
            logger.info( "Connecting to JVM via {}", jmxUrl );

            JMXConnector connector = JMXConnectorFactory.connect( jmxUrl );
            final MBeanServerConnection mbeanServerConnection = connector.getMBeanServerConnection();

            sw.stop();
            logger.info( "Connected to '{}' in {}ms", jmxUrl, sw.elapsed( TimeUnit.MILLISECONDS ) );

            return new JmxConnection( connector, mbeanServerConnection, createConnectionMetaData( jmxPort ) );
        } catch( Exception e ) {
            throw Throwables.propagate( e );
        }
    }

    private ConnectionMetaData createConnectionMetaData( int port ) throws IOException {

        EvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable( "port", port );
        String jvmInstanceName = null;
        if( jvmInstanceConfiguration != null ) {
             jvmInstanceName = jvmInstanceConfiguration.getJvmNameExtractor().getValue( ctx, String.class );
        }

        return new ConnectionMetaData( null, null, null, jvmInstanceConfiguration, jvmInstanceName );
    }
}
