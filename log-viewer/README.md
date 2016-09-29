Event log viewer for Eventuate
==============================

log-viewer is a minimalistic scala-application for the command-line that can be used to view the
content of an [Eventuate](https://github.com/RBMHTechnology/eventuate) event log. It connects remotely to a running
Eventuate-based application to retrieve 
[`DurableEvent`s](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.DurableEvent)
of a given span of sequence numbers and simply prints a string representation to stdout.

Usage
-----

There are several options on how to start and customize the log-viewer. See below for more 
advanced alternatives, if the one proposed here is not suitable for you.

0. [Download](https://oss.jfrog.org/oss-snapshot-local/com/rbmhtechnology/eventuate-tools/log-viewer/) the universal zip-artifact.

0. Unzip anywhere creating a directory `log-viewer-<version>`.

0. Drop jar-files of your application containing
 
   - the application specific classes of the event 
     [payloads](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.DurableEvent@payload:Any),
   - the corresponding
     [custom serializers](http://rbmhtechnology.github.io/eventuate/reference/event-sourcing.html#custom-event-serialization) and
   - the corresponding akka configuration for custom serializers in a `reference.conf`
   
   in `log-viewer-<version>/ext`
   
0. Start log-viewer through the script in `log-viewer-<version>/bin`:
   
   In Unix environments:
   ```bash
   log-viewer --help
   ```
   to display the help page about the command line options.
   ```bash
   log-viewer --batchSize 20 --fromSeqNo 150 --maxEvents 100 --remoteHost foo.example.com --remotePort 5555
   ```
   to display 100 events from sequence number 150 on of the `default` log of the application running 
   on host foo.example.com with a akka remote port of 5555
   ([configured](http://doc.akka.io/docs/akka/2.4.1/scala/remoting.html#Preparing_your_ActorSystem_for_Remoting)
   through the configuration variable `akka.remote.netty.tcp.port` of the Eventuate application)

If the application jars do not contain a `reference.conf` with the akka configuration for custom serializers
you can provide a corresponding file on command line as follows:

```bash
log-viewer -Dconfig.file=path/serializer.conf ...
```

### Command line arguments

log-viewer comes with a usage page when called with command line option `--help`.

### Event formatting and filtering

log-viewer allows to specify a format string for printing retrieved events in a desired 
format. There are actually two types of _template engines_ that can be selected by the command 
line option `--eventFormatter`:

- `CaseClass`: Allows to specify a simple `java.util.Formatter`-like template to 
  select specific fields of a 
  [`DurableEvent`](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.DurableEvent).
  It does not support filtering nor selecting details of the payload so fomatting is limitted to 
  the direct fields of an `DurableEvent`. Check `--help` for more details.
  
- `Velocity`: Allows to specify a 
  [Velocity](http://velocity.apache.org/engine/1.7/user-guide.html) template. As Velocity 
  supports conditional expressions this can also be used to filter events and display only those 
  that match a certain criterion. The velocity template has access to two variables:
  
  - `ev` referencing the `DurableEvent`
  - `nl` referencing a line separator
  
  The following exmaple template assumes the payload class:
  ```
  case class CustomEvent(a:Int, b:String)
  ```
  With this a filtering template could be defined as:
  ```
  #if($ev.payload().a() > 10)$ev.payload().b()$nl#end
  ``` 
  This would display only `b` of events with
  `a > 10`. Note the `$nl` within the if expression at the end of the template string. Without it
  all events would be printed in a single line. If it were outside of the if expression a filtered 
  event would still be displayed as an empty line.

Alternative ways to start log-viewer
-------------------------------------------

### Start through sbt

When you start log-viewer through sbt (i.e. from the source-tree with `sbt log-viewer/run`), you have two options to customize the classpath:

0. Modify `build.sbt` and include your dependencies.
0. Drop the jar-files containing your classes in the `lib` folder. As the `lib` folder is explicitly excluded from
   git-management, this will keep your working directory clean.
   
If the customized classpath does not contain a `reference.conf` file containing the
configuration for the custom serializers, you can provide a corresponding file through the system property 
[`config.file`](https://github.com/typesafehub/config#standard-behavior). You have once again two options for this:

0. Add `javaOptions += "-Dconfig.file=..."` to `build.sbt`.
0. Call sbt with an additional first argument: `sbt 'set javaOptions += "-Dconfig.file=..."' "log-viewer/run ..."`.

### Create a custom log-viewer package

log-viewer uses [sbt-native-packager](https://github.com/sbt/sbt-native-packager) for packaging
the application into a distributable artifact. You can for example use `sbt universal:packageBin` to 
create a zip-file containing the application. This *universal artifact* contains a `bin` folder with
scripts for starting the application (`log-viewer`) and a `lib` folder with all required jars and an empty `ext` folder.
To _install_ the application, you can unzip the archive anywhere.

To create a custom log-viewer package that already contains all class definitions and 
configuration required for deserializing application specific events, you can create a new sbt-project 
with dependencies to your application specific classes as well as the log-viewer project:

```scala
libraryDependencies ++= Seq(
  "com.rbmhtechnology.eventuate-tools" %% "log-viewer" % "<version>"
  // your dependencies ...
)

// for snapshots
resolvers += "OJO Snapshots" at "https://oss.jfrog.org/oss-snapshot-local"

// for releases
resolvers += "OJO Releases" at "https://oss.jfrog.org/oss-release-local"
```

and use sbt-native-packager in a similar manner as log-viewer does:

```scala
enablePlugins(JavaAppPackaging)

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Test, packageDoc) := false

makeDeploymentSettings(Universal, packageBin in Universal, "zip")

publish <<= publish dependsOn (publish in Universal)

publishLocal <<= publishLocal dependsOn (publishLocal in Universal)
```


Trouble Shooting
----------------

If the log-viewer cannot connect to the remote application (even if it is up and running) it 
runs into an ask timeout like follows:
    
```
akka.pattern.AskTimeoutException: Ask timed out on [ActorSelection[Anchor(akka.tcp://location@localhost:2552/), Path(/user/acceptor)]] after [15000 ms]. Sender[null] sent message of type "com.rbmhtechnology.eventuate.ReplicationProtocol$GetReplicationEndpointInfo$".
```

This is typically caused by wrong network configuration. The remote host (`--remoteHost`) must be
the identical name or IP that is used by the remote application's remoting config 
(`akka.remote.netty.tcp.hostname`). If for example the remote application uses `127.0.0.1` 
log-viewer cannot connect with `-rh localhost`. In that case you should see in the log-file of 
the remote application something like the following:

```
10:45:20.089 [location-akka.actor.default-dispatcher-27] ERROR akka.remote.EndpointWriter - dropping message [class akka.actor.ActorSelectionMessage] for non-local recipient [Actor[akka.tcp://location@localhost:2552/]] arriving at [akka.tcp://location@localhost:2552] inbound addresses are [akka.tcp://location@127.0.0.1:2552]
10:45:34.955 [location-akka.actor.default-dispatcher-26] ERROR akka.remote.EndpointWriter - AssociationError [akka.tcp://location@127.0.0.1:2552] <- [akka.tcp://location@192.168.0.36:50550]: Error [Shut down address: akka.tcp://location@192.168.0.36:50550] [
akka.remote.ShutDownAssociation: Shut down address: akka.tcp://location@192.168.0.36:50550
Caused by: akka.remote.transport.Transport$InvalidAssociationException: The remote system terminated the association because it is shutting down.
]
```

The check for the name is even case sensitive!

The local bind address (`--localBindAddress`) must be accessible for the remote host. If you 
specify for example the name of your local host the remote application must be able to resolve this 
name.  
  
  

Current Limitations
-------------------

- log-viewer needs to have all application specific class definitions of
  [payload](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.DurableEvent@payload:Any)
  instances including their custom serializers and corresponding configuration in its classpath.
  So you need to customize it before you can run it.
- The application whose event log is viewed must be up and running.
- Event formatting with command line option `--event-format` is limited to select fields of a `DurableEvent`.
  It is impossible to select individual fields of the application defined payload.
- The event formatting implementation relies on the fact that the `productIterator` of a `case class`
  instance returns the values in the same order as `getDeclaredFields` of the corresponding `Class`.
  This is the case for scala 2.11 and JDK 1.8, however it is not guaranteed.

Implementation notes
--------------------

log-viewer uses the 
[`ReplicationProtocol`](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.ReplicationProtocol$)
to communicate with the running Eventuate-based application, so it basically looks like just another replication-client.
That is why it needs to have access to application jars.

