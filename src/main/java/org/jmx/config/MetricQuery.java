package org.jmx.config;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.management.ObjectName;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

public class MetricQuery {
    private Multimap<String, JmxAttribute> nameIdx;

    public ObjectName getPattern() {
        return pattern;
    }

    public void setPattern( ObjectName pattern ) {
        this.pattern = pattern;
    }

    public List<JmxAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes( List<JmxAttribute> attributes ) {
        this.attributes = attributes;
    }

    private ObjectName pattern;
    private List<JmxAttribute> attributes = ImmutableList.of();

    public String[] getUniqueAttributeNames() {
        return uniqueAttributeNames;
    }

    private String[] uniqueAttributeNames;

    public void index() {
        Multimap<String, JmxAttribute> nameIdx = ArrayListMultimap.create();
        for( JmxAttribute attribute : attributes ) {
            nameIdx.put( attribute.getName(), attribute );
        }
        this.nameIdx = nameIdx;
        Set<String> attributeNames = nameIdx.keySet();
        uniqueAttributeNames = attributeNames.toArray( new String[ attributeNames.size() ] );
    }

    public Collection<JmxAttribute> findAttributesFor( String name ) {
        return nameIdx.get( name );
    }
}
