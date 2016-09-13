Eventuate replication endpoint health information for dropwizard's healthchecks lib
===================================================================================

eventuate-tool's dropwizard-healthchecks provides health monitoring facilities for Eventuate components based on 
[dropwizard's healthchecks library](http://metrics.dropwizard.io/3.1.0/getting-started/#health-checks).
The following can be monitored:

- health of the replication from remote source logs based on 
  [Available/Unavailable](http://rbmhtechnology.github.io/eventuate/reference/event-log.html#failure-detection) 
  messages.
- health of the connection to the storage backend for persisting events based on information from an
  event-log's 
  [circuit breaker](http://rbmhtechnology.github.io/eventuate/reference/event-sourcing.html?highlight=circuitbreaker#circuit-breaker).
- health of Eventuate's actors based on akka's 
  [death watch](http://doc.akka.io/docs/akka/2.4/general/supervision.html#What_Lifecycle_Monitoring_Means)
  
Register with HealthCheckRegistry
---------------------------------

Given a `ReplicationEndpoint` (`endpoint`) to be monitored and a `HealthCheckRegistry` (`healthRegistry`) 
health checks can be registered under a given optional prefix (`namePrefix`) for each of the components listed above as follows: 

- replication health:
  ```scala
  val monitor = new ReplicationHealthMonitor(endpoint, healthRegistry, namePrefix)
  ```
- persistence health:
  ```scala
  val monitor = new PersistenceHealthMonitor(endpoint, healthRegistry, namePrefix)
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

Healthcheck names
-----------------

For a given prefix the individual monitors register the following health checks:

- `ReplicationHealthMonitor` registers for each local log that is replicated to a remote endpoint:
  ```
  <prefix>.replication-from.<remote-endpoint-id>.<log-name>
  ```
  This turns _unhealthy_ as soon as an `Unavailable` message for this particular log arrives and back 
  to _healthy_ when a corresponding `Available` message arrives.
- `PersistenceHealthMonitor` registers for each local log that uses a circuit breaker:
  ```
  <prefix>.persistence-of.<log-id>
  ```
  This turns _unhealthy_ as soon as the circuit breaker opens and _healthy_ when it closes.
- `ActorHealthMonitor` registers for each local log:
  ```
  <prefix>.actor.eventlog.<log-id>
  ```
  and for the acceptor:
  ```
  <prefix>.actor.acceptor
  ```
  These turn _unhealthy_ as soon as the corresponding actors terminate.
