# Pekko Observability 

[![Build Status](https://dev.azure.com/pragmasoftnl/akka-sensors/_apis/build/status%2Fpekko-sensors-build?branchName=main)](https://dev.azure.com/pragmasoftnl/akka-sensors/_build/latest?definitionId=61&branchName=main)
[![codecov.io](http://codecov.io/github/jacum/pekko-sensors/coverage.svg?branch=master)](https://codecov.io/gh/jacum/pekko-sensors?branch=master)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
![Maven Central](https://img.shields.io/maven-central/v/nl.pragmasoft.pekko/sensors_2.13?color=%2300AA00)

**Non-intrusive native Prometheus collectors for Pekko internals, negligible performance overhead, suitable for production use.**

- Are you running (or about to run) Pekko in production, full-throttle, and want to see what happens inside?  Did your load tests produce some ask timeouts? thread starvation? threads behaving non-reactively? old code doing nasty blocking I/O?

- Can't use any of Akka native metric libraries (Kamon, Cinnamon)? 

- Already familiar with Prometheus/Grafana observability stack?

If you answer 'yes' to most of the questions above, Pekko Sensors may be the right choice for you:

- Comprehensive feature set to make internals of your Pekko visible, in any environment, including high-load production. 

- It is OSS/free, as in MIT license, and uses explicit, very lightweight instrumentation - yet is a treasure trove for a busy observability engineer.

- Won't affect CPU costs, when running in public cloud.

- Easy Demo/Evaluation setup included: Pekko example app, Prometheus server and Grafana dashboards.

Actor dashboard:
![Actors](./docs/pekko-actors.png)

Dispatcher dashboard:
![Dispatchers](./docs/pekko-dispatchers.png)

## Features

###  Dispatchers
 - time of runnable waiting in queue (histogram) 
 - time of runnable run (histogram)
 - implementation-specific ForkJoinPool and ThreadPool stats (gauges)
 - thread states, as seen from JMX ThreadInfo (histogram, updated once in X seconds) 
 - active worker threads (histogram, updated on each runnable)

### Thread watcher
- thread watcher, keeping eye on threads running suspiciously long, and reporting their stacktraces - to help you find blocking code quickly

### Basic actor stats
 - number of actors (gauge)
 - time of actor 'receive' run (histogram)
 - actor activity time (histogram)
 - unhandled messages (count)
 - exceptions (count)
 
### Persistent actor stats
 - recovery time (histogram)
 - number of recovery events (histogram)
 - persist time (histogram)
 - recovery failures (counter)
 - persist failures (counter)

### Cluster
 - cluster events, per type/member (counter)

### Java Virtual Machine (from Prometheus default collectors)
- number of instances
- start since / uptime
- JVM version
- memory pools
- garbage collector

## Demo setup

We assuming you have `docker` and `docker-compose` up and running.

Prepare sample app:
```
sbt "compile; project app; Docker/publishLocal"
```

Start observability stack:
```
docker-compose -f examples/observability/docker-compose.yml up
```

Send some events:
```
for z in {1..100}; do curl -X POST http://localhost:8080/api/ping/$z/100; done
for z in {101..200}; do curl -X POST http://localhost:8080/api/ping-tp/$z/100; done
for z in {3001..3300}; do curl -X POST http://localhost:8080/api/ping-persistence/$z/300 ; done
```

Open Grafana at http://localhost:3000.

Go to http://localhost:3000/plugins/sensors-prometheus-app, click *Enable*.
Sensors' bundled dashboards will be imported.
 
 
## Usage

### SBT dependency

```
libraryDependencies ++= 
  Seq(
     "nl.pragmasoft.pekko" %% "sensors" % "1.0.3",
  )
```

### Prometheus exporter

If you already have Prometheus exporter in your application, `CollectorRegistry.defaultRegistry` will be used by default.
To control this finely, `PekkoSensors.prometheusRegistry` needs to be overridden.

For an example of HTTP exporter service, check `MetricService` implementation in example application (`app`) module. 

### Application configuration

Override `type` and `executor` with Sensors' instrumented executors.
Add `pekko.sensors.PekkoSensorsExtension` to extensions.

```
pekko {

  actor {

    # main/global/default dispatcher

    default-dispatcher {
      type = "nl.pragmasoft.pekko.sensors.dispatch.InstrumentedDispatcherConfigurator"
      executor = "nl.pragmasoft.pekko.sensors.dispatch.InstrumentedExecutor"

      instrumented-executor {
        delegate = "fork-join-executor" 
        measure-runs = true
        watch-long-runs = true
        watch-check-interval = 1s
        watch-too-long-run = 3s
      }
    }

    # some other dispatcher used in your app

    default-blocking-io-dispatcher {
      type = "nl.pragmasoft.pekko.sensors.dispatch.InstrumentedDispatcherConfigurator"
      executor = "nl.pragmasoft.pekko.sensors.dispatch.InstrumentedExecutor"

      instrumented-executor {
        delegate = "thread-pool-executor"
        measure-runs = true
        watch-long-runs = false
      }
    }
  }

  extensions = [
    nl.pragmasoft.pekko.sensors.PekkoSensorsExtension
  ]
}

```

### Using explicit/inline executor definition

```
  default-dispatcher {
    type = "nl.pragmasoft.pekko.sensors.dispatch.InstrumentedDispatcherConfigurator"
    executor = "nl.pragmasoft.pekko.sensors.dispatch.InstrumentedExecutor"

    instrumented-executor {
      delegate = "fork-join-executor"
      measure-runs = true
      watch-long-runs = false
    }

    fork-join-executor {
      parallelism-min = 6
      parallelism-factor = 1
      parallelism-max = 6
    }
  }  
```

### Actors (classic)

```
 # Non-persistent actors
 class MyImportantActor extends Actor with ActorMetrics {

    # This becomes label 'actor', default is simple class name
    # but you may segment it further
    # Just make sure the cardinality is sane (<100)
    override protected def actorTag: String = ... 

      ... # your implementation
  }

 # Persistent actors
 class MyImportantPersistentActor extends Actor with PersistentActorMetrics {
  ...


```

### Actors (typed)

```
val behavior = BehaviorMetrics[Command]("ActorLabel") # basic actor metrics
    .withReceiveTimeoutMetrics(TimeoutCmd) # provides metric for amount of received timeout commands
    .withPersistenceMetrics # if inner behavior is event sourced, persistence metrics would be collected
    .setup { ctx: ActorContext[Command] =>
      ... # your implementation
    }
```

### Internal parameters

Some parameters of the Sensors library itself, that you may want to tune:
```
pekko.sensors {
  thread-state-snapshot-period = 5s
  cluster-watch-enabled = false
}
```

### Additional metrics

For anything additional to measure in actors, extend `*ActorMetrics` in your own trait.

```
trait CustomActorMetrics extends ActorMetrics  with MetricsBuilders {

  val importantEvents: Counter = counter
    .name("important_events_total")
    .help(s"Important events")
    .labelNames("actor")
    .register(metrics.registry)

}

```
