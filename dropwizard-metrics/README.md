Eventuate replication metrics for dropwizard's metrics lib
=========================================================

eventuate-tool's dropwizard-metrics contains a [`Gauge`](http://metrics.dropwizard.io/3.1.0/manual/core/#gauges)-implementation 
that returns
metrics of an [`ReplicationEndpoint`](http://rbmhtechnology.github.io/eventuate/reference/event-log.html#replication-endpoints). 

Register with MetricRegistry
----------------------------

The gauge can be registered with a [`MetricRegistry`](http://metrics.dropwizard.io/3.1.0/manual/core/#metric-registries) in the usual way:

```scala
val metrics = new MetricRegistry
metrics.register(endpoint.id, new ReplicationEndpointGauge(endpoint))
```

Export through MetricServlet
-----------------------------

This state is modelled as Bean
that can be exported through [dropwizard's `MetricServlet`](http://metrics.dropwizard.io/3.1.0/manual/servlets/#metricsservlet). 
The JSON representation looks as follows:

```json
  "gauges" : {
    "endpointB" : {
      "value" : {
        "replicatedLogsMetrics" : {
          "log1" : {
            "localSequenceNo" : 4,
            "localVersionVector" : {
              "endpointA_log1" : 2,
              "endpointB_log1" : 4
            },
            "replicationProgress" : {
              "endpointA_log1" : 5
            }
          }
        }
      }
    }
  }
```

In this exmaple we have two replication endpoints (`endpointA` and `endpointB`) on two nodes with a single replicated log (`log1`) 
and the metrics of `endpointB` are shown. So for each 
[replicated log](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.ReplicationEndpoint@logNames:Set[String])
of an `ReplicationEndpoint` the following is displayed:

- The sequence number of the local log (`localSequenceNo`).
- The entire version vector of the local log (`localVersionVector`). For details see also the 
  [eventuate documentation](http://rbmhtechnology.github.io/eventuate/architecture.html#vector-clocks).
- For each remote replica (here `endpointA_log1`) of the local log (`replicationProgress`):
  - The replication progress in form of the sequence number in the remote log upto which events have been replicated to the local log.

For checking the actual replication progress (i.e. how many events need to be replicated from one log to another for a full replication)
the local sequence of one log has to be compared with the replication progress of this replica on a remote node.
In this example the local sequence number of log `log1` of `endpointB` (here `4`) has to be compared 
with the replication progress of log `endpointB_log1` of `endpointA` (here not shown as this is part of `endpointA` metrics). 
If the replication progress is smaller that the local sequence number the replication from B to A lacks behind B's progress.
