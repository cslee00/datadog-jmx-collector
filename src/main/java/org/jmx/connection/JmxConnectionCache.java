package org.jmx.connection;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

public final class JmxConnectionCache {
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final LoadingCache<VirtualMachineConnector, JmxConnection> cache;

    public JmxConnectionCache( int pollRateMs ) {
        cache = CacheBuilder.newBuilder().expireAfterAccess( pollRateMs * 3, TimeUnit.MILLISECONDS ).removalListener(
          new RemovalListener<VirtualMachineConnector, JmxConnection>() {
              @Override
              public void onRemoval( RemovalNotification<VirtualMachineConnector, JmxConnection> notification ) {
                  try {
                      if( notification.getValue() != null ) {
                          logger.info( "Removing idle connection to {}", notification.getKey() );
                          notification.getValue().getConnector().close();
                      }
                  } catch( IOException e ) {
                      logger.error( "Error closing connection to {}", notification.getKey(), e );
                  }
              }
          } ).build( new CacheLoader<VirtualMachineConnector, JmxConnection>() {
            @Override
            public JmxConnection load( VirtualMachineConnector key ) throws Exception {
                return key.connect();
            }
        } );
    }

    public JmxConnection lookup( VirtualMachineConnector connector ) throws UnableToAttachException {
        try {
            return cache.get( connector );
        } catch( ExecutionException e ) {
            throw new UnableToAttachException( String.format( "Unable to attach to JVM '%s'", connector ), e.getCause() );
        }
    }
}
