# Welcome to ZIO Event Sourcing

ZIO Event Sourcing is a type-safe library adding event sourcing concepts to the zio ecosystem.

## Quickstart

### Creating Event Sourcing behaviour

```scala
import zio.{Has, URIO}
import zio.es.{EventJournal, EventSourcing, StateStorage}

val es: URIO[Has[StateStorage[State]] & Has[EventJournal[Event]], EventSourcing[State, Event]] =
  EventSourcing.make[State, Event](capacity = 5)(handler = (state, event) => state)(init = State.empty)
```

### Adding Event Sourcing behaviour to your services

```scala
import zio.{Task, UIO}
import zio.es.{EventSourcing, PersistenceId}

type State = Int

enum Event:
  case Increment, Decrement

trait Service:
  def increment(id: PersistenceId): UIO[Unit]
  def decrement(id: PersistenceId): UIO[Unit]
  def get(id: PersistenceId): Task[State]

case class ServiceLive(es: EventSourcing[State, Event]):
  override def increment(id: PersistenceId) =
    es.log(id)(Event.Increment)
   
   override def decrement(id: PersistenceId) =
     es.log(id)(Event.Decrement)
   
   def get(id: PersistenceId) =
     es.get(id)
``` 
