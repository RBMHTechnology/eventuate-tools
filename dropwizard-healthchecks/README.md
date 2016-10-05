Eventuate replication endpoint health information for dropwizard's healthchecks lib
===================================================================================

eventuate-tool's dropwizard-healthchecks provides health monitoring facilities for Eventuate components based on 
[dropwizard's healthchecks library](http://metrics.dropwizard.io/3.1.0/getting-started/#health-checks).
The following can be monitored:

- health of the replication from remote source logs based on 
  [Available/Unavailable](http://rbmhtechnology.github.io/eventuate/reference/event-log.html#failure-detection) 
  messages.
- health of the connection to the storage backend for persisting new events based on information from an
  event-log's 
  [circuit breaker](http://rbmhtechnology.github.io/eventuate/reference/event-sourcing.html?highlight=circuitbreaker#circuit-breaker).
- health of Eventuate's actors based on akka's 
  [death watch](http://doc.akka.io/docs/akka/2.4/general/supervision.html#What_Lifecycle_Monitoring_Means)
  

Project dependency
------------------

The following artifact is published to [jfrog's](https://oss.jfrog.org/) snapshot and release repository:

- Artifact Id: `dropwizard-healthchecks_<scala-version>`
- Group Name: `com.rbmhtechnology.eventuate-tools`

Settings for an sbt-build:

```scala
libraryDependencies += "com.rbmhtechnology.eventuate-tools" %% "dropwizard-healthchecks" % "<version>"

// for snapshots
resolvers += "OJO Snapshots" at "https://oss.jfrog.org/oss-snapshot-local"

// for releases
resolvers += "OJO Releases" at "https://oss.jfrog.org/oss-release-local"

```


Register with HealthCheckRegistry
---------------------------------

Given a `ReplicationEndpoint` (`endpoint`) to be monitored and a `HealthCheckRegistry` (`healthRegistry`) 
health checks can be registered under a given optional prefix (`namePrefix`) for each of the components listed above as follows: 

- replication health:
  ```scala
  val monitor = new ReplicationHealthMonitor(endpoint, healthRegistry, namePrefix)
  ```
- circuit breaker health:
  ```scala
  val monitor = new CircuitBreakerHealthMonitor(endpoint, healthRegistry, namePrefix)
  ```
- actor health:
  ```scala
  val monitor = new ActorHealthMonitor(endpoint, healthRegistry, namePrefix)
  ```

There is also a convenience class to register all at once in a single `HealthCheckRegistry` 
under a single `namePrefix`:

```scala
val monitor = new ReplicationEndpointHealthMonitor(endpoint, healthRegistry, namePrefix)
```

Health monitoring can be stopped to remove the registered health checks in each case as follows:
```scala
monitor.stopMonitoring()
```

When the actor system stops without the monitoring being stopped first all registered health checks 
turn unhealthy and indicate the monitored component in an unknown state. This ensures that in case of an 
unexpected actor system stop (as for example triggered by Eventuate's cassandra extension, when the
database cannot be accessed at startup) all components are reported as unhealthy.

Healthcheck names
-----------------

For a given prefix the individual monitors register the following health checks:

- `ReplicationHealthMonitor` registers for each local log that is replicated to a remote endpoint:
  ```
  <prefix>.replication-from.<remote-endpoint-id>.<log-name>
  ```
  This turns _unhealthy_ as soon as an `Unavailable` message for this particular log arrives and back 
  to _healthy_ when a corresponding `Available` message arrives. See also the 
  [corresponding section](http://rbmhtechnology.github.io/eventuate/reference/event-log.html#failure-detection)
  in the Eventuate documentation.
- `CircuitBreakerHealthMonitor` registers for each local log: 
  ```
  <prefix>.circuit-breaker-of.<log-id>
  ```
  This turns _unhealthy_ as soon as the circuit breaker opens and _healthy_ when it closes. Currently 
  only the 
  [`CassandraEventLog`](http://rbmhtechnology.github.io/eventuate/reference/event-log.html#cassandra-storage-backend)
  uses the circuit breaker and it only applies when persisting of locally emitted events fails. 
- `ActorHealthMonitor` registers for each local log:
  ```
  <prefix>.actor.eventlog.<log-id>
  ```
  and for the acceptor:
  ```
  <prefix>.actor.acceptor
  ```
  These turn _unhealthy_ as soon as the corresponding actors terminate.
