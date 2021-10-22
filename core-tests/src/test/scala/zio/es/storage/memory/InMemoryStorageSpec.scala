package zio.es.storage.memory

import zio.{Chunk, Ref}
import zio.es.PersistenceId
import zio.stream.Sink
import zio.test.*
import zio.test.Assertion.*
import scala.collection.immutable.Queue
import java.util.NoSuchElementException

object InMemoryStorageSpec extends DefaultRunnableSpec:
  enum Event:
    case Increment, Decrement

  val id    = PersistenceId("1")
  val state = 1

  def spec = suite("InMemoryStorageSpec")(
    testM("persist correctly appends an event to the storage") {
      for
        states <- Ref.make[Map[PersistenceId, Int]](Map.empty)
        events <- Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty)
        storage = InMemoryStorage(states, events)
        _      <- storage.persist(id)(Event.Increment)
        _      <- storage.persist(id)(Event.Increment)
        _      <- storage.persist(id)(Event.Decrement)
        result <- events.get
      yield assert(result.get(id))(isSome(equalTo(Queue(Event.Increment, Event.Increment, Event.Decrement))))
    },
    testM("retrieve correcly fetches states from the storage") {
      for
        states <- Ref.make(Map(id -> state))
        events <- Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty)
        storage = InMemoryStorage(states, events)
        result <- storage.retrieve(id)
      yield assert(result)(equalTo(state))
    },
    testM("retrieve fails to fetch states if their id is not found") {
      for
        states <- Ref.make[Map[PersistenceId, Int]](Map.empty)
        events <- Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty)
        storage = InMemoryStorage(states, events)
        result <- storage.retrieve(id).run
      yield assert(result)(fails(isSubtype[NoSuchElementException](anything)))
    },
    testM("replay correcly fetches events from the storage") {
      for
        states <- Ref.make[Map[PersistenceId, Int]](Map.empty)
        events <- Ref.make(Map(id -> Queue(Event.Increment, Event.Increment, Event.Decrement)))
        storage = InMemoryStorage(states, events)
        result <- storage.replay(id).run(Sink.collectAll)
      yield assert(result)(equalTo(Chunk(Event.Increment, Event.Increment, Event.Decrement)))
    },
    testM("replay fails to fetch events if their id is not found") {
      for
        states <- Ref.make[Map[PersistenceId, Int]](Map.empty)
        events <- Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty)
        storage = InMemoryStorage(states, events)
        result <- storage.replay(id).run(Sink.collectAll).run
      yield assert(result)(fails(isSubtype[NoSuchElementException](anything)))
    }
  )
