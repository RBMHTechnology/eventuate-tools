Eventuate replication endpoint metrics for dropwizard's metrics lib
===================================================================

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
            "localSequenceNo" : 5,
            "localVersionVector" : {
              "endpointA_log1" : 2,
              "endpointB_log1" : 4
            },
            "replicationProgress" : {
              "endpointA_log1" : 4
            }
          }
        }
      }
    }
  }
```

In this exmaple we have two replication endpoints (`endpointA` and `endpointB`) on two nodes with a single replicated log (`log1`) 
and the metrics of `endpointB` are shown. The metrics of `endpointA` could look as follows:

```json
  "gauges" : {
    "endpointA" : {
      "value" : {
        "replicatedLogsMetrics" : {
          "log1" : {
            "localSequenceNo" : 4,
            "localVersionVector" : {
              "endpointA_log1" : 2,
              "endpointB_log1" : 2
            },
            "replicationProgress" : {
              "endpointB_log1" : 3
            }
          }
        }
      }
    }
  }
```
 
 
So for each 
[replicated log](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.ReplicationEndpoint@logNames:Set[String])
of an `ReplicationEndpoint` the following is displayed:

- The sequence number of the local log (`localSequenceNo`).
- The entire version vector of the local log (`localVersionVector`). For details see also the 
  [Eventuate documentation](http://rbmhtechnology.github.io/eventuate/architecture.html#vector-clocks).
- For each remote replica (here `endpointA_log1`) of the local log (`replicationProgress`):
  - The replication progress in form of the sequence number in the remote log up to which events have been replicated to the local log.

For checking the actual replication progress (i.e. how many events need to be replicated from one log to another for a full replication)
the local sequence number of one log has to be compared with the replication progress of this replica on a remote node.
In this example one can see that the replication from A to B has caught up with A local progress as 
`endpointA` -> `log1.sequenceNo` (4) equals `endpointB` -> `log1.replicationProgress.endpointA_log1` (4). 
As opposed to this the replication from B to A lacks behind as `endpointB` -> `log1.sequenceNo` (5) is
greater than `endpointA` -> `log1.replicationProgress.endpointB_log1` (3).

