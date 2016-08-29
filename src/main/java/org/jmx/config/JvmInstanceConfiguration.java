package org.jmx.config;

import java.util.List;

import org.springframework.expression.Expression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

public final class JvmInstanceConfiguration {
    private Expression jvmSelector;
    private Expression jvmNameExtractor;
    private List<String> tags = ImmutableList.of();
    private List<String> metricSetRefs = ImmutableList.of();

    public Range<Integer> getJmxPortRange() {
        return jmxPortRange;
    }

    public void setJmxPortRange( Range<Integer> jmxPortRange ) {
        this.jmxPortRange = jmxPortRange;
    }

    private Range<Integer> jmxPortRange;

    public List<MetricQuery> getMetricSet() {
        return metricSet;
    }

    private List<MetricQuery> metricSet;

    public Expression getJvmSelector() {
        return jvmSelector;
    }

    public void setJvmSelector( Expression jvmSelector ) {
        this.jvmSelector = jvmSelector;
    }

    public Expression getJvmNameExtractor() {
        return jvmNameExtractor;
    }

    public void setJvmNameExtractor( Expression jvmNameExtractor ) {
        this.jvmNameExtractor = jvmNameExtractor;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags( List<String> tags ) {
        this.tags = tags;
    }

    public List<String> getMetricSetRefs() {
        return metricSetRefs;
    }

    public void setMetricSetRefs( List<String> metricSetRefs ) {
        this.metricSetRefs = metricSetRefs;
    }

    public void setMetricSet( List<MetricQuery> metricSet ) {
        this.metricSet = metricSet;
    }
}
