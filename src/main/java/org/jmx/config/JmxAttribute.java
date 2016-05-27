package org.jmx.config;

import java.util.List;

import com.google.common.base.Splitter;

public final class JmxAttribute {
    private String name;

    public String getNestedName() {
        return nestedName;
    }

    private String nestedName;
    private String alias;

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        if( name.contains( "." )) {
            List<String> pieces = Splitter.on( '.' ).omitEmptyStrings().splitToList( name );
            this.name = pieces.get(0);
            this.nestedName = pieces.get( 1 );
        } else {
            this.name = name;
        }
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias( String alias ) {
        this.alias = alias;
    }

    public MetricType getType() {
        return type;
    }

    public void setType( MetricType type ) {
        this.type = type;
    }

    private MetricType type = MetricType.GAUGE;
}
