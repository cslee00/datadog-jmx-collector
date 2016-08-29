package org.jmx.connection;

import static com.google.common.base.Preconditions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jmx.config.JvmInstanceConfiguration;
import org.springframework.expression.EvaluationContext;

public class ConnectionMetaData {
    private final String appArgs;
    private final Properties systemProperties;

    public String[] getJvmInstanceTags() {
        return jvmInstanceTags;
    }

    private final String[] jvmInstanceTags;

    public JvmInstanceConfiguration getJvmInstanceConfiguration() {
        return jvmInstanceConfiguration;
    }

    private final JvmInstanceConfiguration jvmInstanceConfiguration;

    public EvaluationContext getCtx() {
        return ctx;
    }

    private final EvaluationContext ctx;

    public String getAppArgs() {
        return appArgs;
    }

    public Properties getSystemProperties() {
        return systemProperties;
    }

    public ConnectionMetaData( String appArgs, Properties systemProperties, EvaluationContext ctx, JvmInstanceConfiguration jvmInstanceConfiguration,
      String jvmInstanceName ) {
        this.appArgs = appArgs;
        this.systemProperties = systemProperties;
        this.ctx = ctx;
        this.jvmInstanceConfiguration = jvmInstanceConfiguration;

        if( jvmInstanceName != null && jvmInstanceConfiguration != null ) {
            List<String> tags = new ArrayList<>();
            tags.addAll( jvmInstanceConfiguration.getTags() );
            tags.add( "jvm-instance:" + jvmInstanceName );
            this.jvmInstanceTags = tags.toArray( new String[ tags.size() ] );
        } else {
            jvmInstanceTags = new String[ 0 ];
        }
    }
}
