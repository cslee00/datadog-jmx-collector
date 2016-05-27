package org.jmx.connection;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;

import org.jmx.config.JvmInstanceConfiguration;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public final class JmxConnectionOperations {

    private final LoadingCache<ObjectName, Set<ObjectName>> queryNamesCache = CacheBuilder.newBuilder().maximumSize( 1000 ).build(
      new CacheLoader<ObjectName, Set<ObjectName>>() {
          @Override
          public Set<ObjectName> load( ObjectName pattern ) throws Exception {
              return mbeanServerConnection.queryNames( pattern, null );
          }
      } );



    private final String[] jvmInstanceTags;

    public JvmInstanceConfiguration getJvmInstanceConfiguration() {
        return jvmInstanceConfiguration;
    }

    private final JvmInstanceConfiguration jvmInstanceConfiguration;

    JMXConnector getConnector() {
        return connector;
    }

    private final JMXConnector connector;

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

    private final MBeanServerConnection mbeanServerConnection;

    public JmxConnectionOperations( JMXConnector connector, MBeanServerConnection mbeanServerConnection, JvmInstanceConfiguration jvmInstanceConfiguration,
      String jvmInstanceName ) {
        this.connector = checkNotNull( connector, "connector is required" );
        this.mbeanServerConnection = checkNotNull( mbeanServerConnection, "mbeanServerConnection is required" );
        this.jvmInstanceConfiguration = checkNotNull( jvmInstanceConfiguration, "jvmInstanceConfiguration is required" );

        List<String> tags = new ArrayList<>();
        tags.addAll(  jvmInstanceConfiguration.getTags()  );
        tags.add( "jvm-instance:" + jvmInstanceName );
        this.jvmInstanceTags = tags.toArray( new String[ tags.size() ] );
    }

    public String[] getJvmInstanceTags() {
        return jvmInstanceTags;
    }
}
