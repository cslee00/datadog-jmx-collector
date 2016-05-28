# JMX Collector for DataDog

Collect JMX metrics and feed to DataDog.  Dynamically discovers JVMs matching criteria & attaches to them, pulling all configured JMX metrics.  By default core JXM metrics (those defined by Platform MBeans) are pulled, including all garbage collector & memory pool metrics.

Configuration is via a JSON config file, specifying an expression for selecting JVMs, an expression for extracting a meaningful name for the JVM, default tags for JVMs, and optional references to additional JMX metric definitions to extract.
```
[
  {
    "jvmSelector": "#appArgs.contains('Bootstrap')",
    "jvmNameExtractor": "#systemProps['node.name']",
    "tags": ["jvm-type:some-system"],
    "metricSetRefs": [
    ]
  }
]
```

By default metrics are delivered to DogStatsd @ 127.0.0.1:8125

