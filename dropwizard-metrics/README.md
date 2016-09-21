Eventuate replication endpoint metrics for dropwizard's metrics lib
===================================================================

eventuate-tool's dropwizard-metrics provides [`Gauge`s](http://metrics.dropwizard.io/3.1.0/manual/core/#gauges) 
that returns
metrics of an [`ReplicationEndpoint`](http://rbmhtechnology.github.io/eventuate/reference/event-log.html#replication-endpoints). 

Add dependency to project
-------------------------

The following artifact is published to [jfrog's](https://oss.jfrog.org/) snapshot and release repository:

- Artifact Id: `dropwizard-metrics_<scala-version>`
- Group Name: `com.rbmhtechnology.eventuate-tools`

Settings for an sbt-build:

```scala
libraryDependencies += "com.rbmhtechnology.eventuate-tools" %% "dropwizard-metrics" % "<version>"
// for snapshots
resolvers += "OJO Snapshots" at "https://oss.jfrog.org/oss-snapshot-local"
// for releases
resolvers += "OJO Releases" at "https://oss.jfrog.org/oss-release-local"

```

Start/Stop recording metrics
----------------------------

To register gauges for replication metrics and start recording metrics a `DropwizardReplicationMetricsRecorder`
has to be initialized with the `ReplicationEndpoint` to be monitored and the 
[`MetricRegistry`](http://metrics.dropwizard.io/3.1.0/manual/core/#metric-registries) where gauges should be registered.
A name prefix for all gauges can be optionally provided:

```scala
val endpoint = new ReplicationEndpoint(...)
val metricRegistry = new MetricRegistry
val recorder = new DropwizardReplicationMetricsRecorder(endpoint, metricRegistry, Some("prefix"))
```

Recording of metrics starts immediately and can be stopped with:

```scala
recorder.stopRecording()
```

Registered Gauges
-----------------

Once recording is started the gauges for the following values are registered:

- The sequence number of the local log with id <log-id> under the name `<prefix>.<log-id>.sequenceNo`.
- The time entry with id <process-id> in the version vector of the local log with id <log-id> under the name 
  `<prefix>.<log-id>.localVersionVector.<process-id>`. For details see also the 
  [Eventuate documentation](http://rbmhtechnology.github.io/eventuate/architecture.html#vector-clocks). 
- For each remote replica with id <remote-id> of the local log with id <log-id> the replication progress in 
  form of the sequence number in the remote log up to which events have been replicated to the local log
  under the name `<prefix>.<log-id>.replicationProgress.<remote-id>`

For checking the actual replication progress (i.e. how many events need to be replicated from one log to another for a full replication)
the local sequence number of one log has to be compared with the replication progress of this replica on a remote node.
For example in case of two replication endpoints (`endpointA` and `endpointB`) on two nodes (A and B) with a single replicated log (`log1`)
and the following recorded values:

- on node A
  - `endpointA_log1.sequenceNo` = 4
  - `endpointA_log1.replicationProgress.endpointB_log1` = 3
- on node B
  - `endpointB_log1.sequenceNo` = 5
  - `endpointB_log1.replicationProgress.endpointA_log1` = 4
  
one can see that the replication from A to B has caught up with A's local progress as 
`endpointA_log1.sequenceNo` (4) equals `endpointB_log1.replicationProgress.endpointA_log1` (4). 
As opposed to this the replication from B to A lacks behind as `endpointB_log1.sequenceNo` (5) is
greater than `endpointA_log1.replicationProgress.endpointB_log1` (3)
