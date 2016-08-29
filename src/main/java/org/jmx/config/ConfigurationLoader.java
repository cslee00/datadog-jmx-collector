package org.jmx.config;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Uninterruptibles;

public class ConfigurationLoader {
    private final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    public ConfigurationLoader( ObjectMapper objectMapper ) {
        this.objectMapper = checkNotNull( objectMapper, "objectMapper is required" );
    }

    public List<JvmInstanceConfiguration> loadConfiguration( Resource primaryConfig ) {
        try {
            if( !primaryConfig.exists() ) {
                throw new RuntimeException( primaryConfig + " does not exist" );
            }

            List<MetricQuery> coreJvmMetrics = loadMetricSet( new ClassPathResource( "/jvm-core-metrics.json" ) );

            List<JvmInstanceConfiguration> allConfigurations = new ArrayList<>( );
            List<JvmInstanceConfiguration> jvmInstanceConfigurations = loadJvmInstanceConfig( primaryConfig );
            List<JvmInstanceConfiguration> jmxCollectorConfig = loadJvmInstanceConfig( new ClassPathResource("/jmx-collector-config.json") );
            allConfigurations.addAll( jvmInstanceConfigurations );
            allConfigurations.addAll( jmxCollectorConfig );

            resolveMetricSetRefs( allConfigurations, primaryConfig, coreJvmMetrics );

            return allConfigurations;
        } catch( IOException e ) {
            throw Throwables.propagate( e );
        }
    }

    private List<JvmInstanceConfiguration> loadJvmInstanceConfig( Resource res ) throws IOException {
        logger.info( "Loading JVM monitoring configuration from {}", res );
        try( InputStream is = res.getInputStream() ) {
            return objectMapper.readValue( is, new TypeReference<List<JvmInstanceConfiguration>>() {
            } );
        }
    }

    private void resolveMetricSetRefs( List<JvmInstanceConfiguration> jvmInstanceConfigurations, Resource primaryConfig, List<MetricQuery> coreJvmMetrics )
      throws IOException {

        for( JvmInstanceConfiguration jvmInstanceConfiguration : jvmInstanceConfigurations ) {
            List<MetricQuery> metricQueries = new ArrayList<>();
            metricQueries.addAll( coreJvmMetrics );
            for( String metricSetRef : jvmInstanceConfiguration.getMetricSetRefs() ) {
                Resource res;
                if( metricSetRef.startsWith( "builtin:" ) ) {
                    res = new ClassPathResource( metricSetRef.replace( "builtin:", "" ) );
                } else {
                    res = primaryConfig.createRelative( metricSetRef );
                }
                metricQueries.addAll( loadMetricSet( res ) );
            }
            jvmInstanceConfiguration.setMetricSet( ImmutableList.copyOf( metricQueries ) );
        }
    }

    private List<MetricQuery> loadMetricSet( Resource res ) throws IOException {
        if( !res.exists() ) {
            throw new RuntimeException( res + " does not exist" );
        }
        logger.info( "Loading metric set from {}", res );
        // TODO - manage input stream
        List<MetricQuery> queries = objectMapper.readValue( res.getInputStream(), new TypeReference<List<MetricQuery>>() {
        } );

        for( MetricQuery query : queries ) {
            query.index();
        }
        return ImmutableList.copyOf( queries );
    }
}
