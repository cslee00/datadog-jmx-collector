package org.jmx;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;

import javax.management.MBeanServer;

import org.jmx.connection.JmxConnectionStateResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.core.task.TaskExecutor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.jmx.support.MBeanServerFactoryBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableMBeanExport(defaultDomain = "thps.jmx-collector")
public class JmxCollectorMain {

    @Value( "${jmxcollector.pollRateMs}" )
    private int pollRateMs;

    @Value( "${jmxcollector.statsdHost}" )
    private String statsdHost;

    @Value( "${jmxcollector.statsdPort}" )
    private int statsdPort;

    @Value( "${jmxcollector.configFile}")
    private String configFile;

    public static void main( String[] args ) {
        System.setProperty( "networkaddress.cache.ttl", "30" );
        System.setProperty( "sun.net.inetaddr.ttl", "30" );
        SpringApplication app = new SpringApplication( JmxCollectorMain.class );
        if( System.getProperty( "os.name" ).toLowerCase().contains( "win" ) ) {
            app.setAdditionalProfiles( "windows" );
            System.setProperty( "java.library.path", System.getProperty( "java.home" ) + "\\jre\\bin\\" );
        }
        app.run( args );
    }

    @Bean
    public ApplicationRunner applicationRunner() {
        return new JmxCollector( collectorMetrics(), configFile, taskScheduler(), taskExecutor(), pollRateMs, jmxConnectionStateResolver(), statsdClient(), objectMapper() );
    }

    @Bean
    public TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize( 2 );
        executor.setMaxPoolSize( 10 );
        executor.setKeepAliveSeconds( (pollRateMs / 1000) * 3 );
        executor.setThreadNamePrefix( "jmx-poller-" );
        return executor;
    }

    @Bean
    public JmxConnectionStateResolver jmxConnectionStateResolver() {
        return new JmxConnectionStateResolver( pollRateMs );
    }

    @Bean
    public StatsDClient statsdClient() {
        return new NonBlockingStatsDClient( "", statsdHost, statsdPort, 1000 );
    }

    @Bean
    public ExpressionParser expressionParser() {
        return new SpelExpressionParser();
    }


    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.setDeserializerModifier( new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<Enum> modifyEnumDeserializer( DeserializationConfig config, final JavaType type, BeanDescription beanDesc,
              final JsonDeserializer<?> deserializer ) {
                return new JsonDeserializer<Enum>() {
                    @Override
                    public Enum deserialize( JsonParser jp, DeserializationContext ctxt ) throws IOException {
                        Class<? extends Enum> rawClass = (Class<Enum<?>>) type.getRawClass();
                        return Enum.valueOf( rawClass, jp.getValueAsString().toUpperCase() );
                    }
                };
            }
        } );

        module.addDeserializer( Expression.class, new ExpressionDeserializer( expressionParser() ) );
        mapper.registerModule( module );

        return mapper;
    }

    @Bean
    public MetricsMXBean collectorMetrics() {
        return new MetricsMXBean();
    }

    public static class ExpressionDeserializer extends JsonDeserializer<Expression> {
        private final ExpressionParser expressionParser;

        public ExpressionDeserializer( ExpressionParser expressionParser ) {
            this.expressionParser = checkNotNull( expressionParser, "expressionParser is required" );
        }

        @Override
        public Expression deserialize( JsonParser p, DeserializationContext ctxt ) throws IOException, JsonProcessingException {
            return expressionParser.parseExpression( p.getValueAsString() );
        }
    }
}

