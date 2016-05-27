package org.jmx;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource()
public class MetricsMXBean {

    private AtomicLong metricsCollected = new AtomicLong(  );
    private AtomicLong serverPolls = new AtomicLong(  );
    public void incrementMetricsCollected() {
        metricsCollected.incrementAndGet();
    }
    public void incrementServerPolls() {
        serverPolls.incrementAndGet();

    }

    @ManagedAttribute
    public long getMetricsCollected() {
        return metricsCollected.get();
    }

    @ManagedAttribute
    public long getServerPolls() {
        return serverPolls.get();
    }
}
