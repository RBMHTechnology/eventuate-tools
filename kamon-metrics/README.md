Eventuate replication endpoint metrics for kamon metrics lib
============================================================

eventuate-tool's kamon-metrics provides
metrics of any replicated log as [entity](http://kamon.io/core/metrics/core-concepts/) under the category `eventuate-replicated-log` 
using kamon's histograms as basis [recording instruments](http://kamon.io/core/metrics/instruments/).

Start/Stop recording metrics
----------------------------

Initially a `KamonReplicationEndpointMetrics` has to initialized with the `ReplicationEndpoint` to 
be monitored and optionally a entity-name-prefix:

```scala
val endpoint = new ReplicationEndpoint(...)
val metrics = new KamonReplicationEndpointMetrics(endpoint, Some("prefix."))
```

Recording of metrics starts immediately and can be stopped with:

```scala
metrics.stopRecording()
```

Recorded values
---------------

Once recording is started it will record for each event-log managed by the `ReplicationEndpoint` under

- the name `"<prefix><logId>"` and
- the category `eventuate-replicated-log`

the following set of histograms:

- `sequenceNo`: The sequence number of the local log.
- `localVersionVector.<process-id>`: The entire version vector of the local log. For details see also the 
  [Eventuate documentation](http://rbmhtechnology.github.io/eventuate/architecture.html#vector-clocks).
- For each remote replica (`<remote-log.id>`) of the local log: 
  - `replicationProgress.<remote-log-id>`: The replication progress in form of the sequence number 
    in the remote log up to which events have been replicated to the local log.

As each one of the recorded values is only increasing the max-value of the histogram also represents 
the latest recorded value. 

For checking the actual replication progress (i.e. how many events need to be replicated from one log to another for a full replication)
the local sequence number of one log has to be compared with the replication progress of this replica on a remote node.
For example in case of two replication endpoints (`endpointA` and `endpointB`) on two nodes with a single replicated log (`log1`)
and the following recorded values:

- for the entity named: `endpointA_log1`
  - `sequenceNo` = 4
  - `replicationProgress.endpointB_log1` = 3
- for the entity named : `endpointB_log1`
  - `sequenceNo` = 5
  - `replicationProgress.endpointA_log1` = 4
  
one can see that the replication from A to B has caught up with A's local progress as 
`endpointA_log1` -> `sequenceNo` (4) equals `endpointB_log1` -> `replicationProgress.endpointA_log1` (4). 
As opposed to this the replication from B to A lacks behind as `endpointB_log1` -> `sequenceNo` (5) is
greater than `endpointA_log1` -> `replicationProgress.endpointB_log1` (3)
