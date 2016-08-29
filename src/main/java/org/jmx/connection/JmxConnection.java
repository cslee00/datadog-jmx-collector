package org.jmx.connection;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;

import org.jmx.config.JvmInstanceConfiguration;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public final class JmxConnection implements AutoCloseable {

    private final LoadingCache<ObjectName, Set<ObjectName>> queryNamesCache = CacheBuilder.newBuilder().maximumSize( 1000 ).build(
      new CacheLoader<ObjectName, Set<ObjectName>>() {
          @Override
          public Set<ObjectName> load( ObjectName pattern ) throws Exception {
              return mbeanServerConnection.queryNames( pattern, null );
          }
      } );

    JMXConnector getConnector() {
        return connector;
    }

    private final JMXConnector connector;

    public ConnectionMetaData getConnectionMetaData() {
        return connectionMetaData;
    }

    private final ConnectionMetaData connectionMetaData;

    public Set<ObjectName> queryNames( ObjectName pattern ) throws IOException {
        try {
            return queryNamesCache.get( pattern );
        } catch( ExecutionException e ) {
            if( e.getCause() instanceof IOException ) {
                throw (IOException) e.getCause();
            }
            throw Throwables.propagate( e );
        }
    }

    public AttributeList getAttributes( ObjectName objectName, String[] attributes ) throws InstanceNotFoundException, IOException, ReflectionException {
        return mbeanServerConnection.getAttributes( objectName, attributes );
    }

    public MBeanServerConnection getMbeanServerConnection() {
        return mbeanServerConnection;
    }

    private final MBeanServerConnection mbeanServerConnection;

    public JmxConnection( JMXConnector connector, MBeanServerConnection mbeanServerConnection, ConnectionMetaData connectionMetaData ) {
        this.connectionMetaData = connectionMetaData;
        this.connector = checkNotNull( connector, "connector is required" );
        this.mbeanServerConnection = checkNotNull( mbeanServerConnection, "mbeanServerConnection is required" );
    }

    @Override
    public void close() throws Exception {
        connector.close();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper( this ).add( "connector", connector ).toString();
    }
}
