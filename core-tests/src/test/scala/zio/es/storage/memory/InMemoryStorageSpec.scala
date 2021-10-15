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

  val id = PersistenceId("1")

  def spec = suite("InMemoryStorageSpec")(
    testM("persist correctly appends an event to the storage") {
      for
        ref <- Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty)
        journal = InMemoryStorage(ref)
        _      <- journal.persist(id)(Event.Increment)
        _      <- journal.persist(id)(Event.Increment)
        _      <- journal.persist(id)(Event.Decrement)
        events <- ref.get
      yield assert(events.get(id))(isSome(equalTo(Queue(Event.Increment, Event.Increment, Event.Decrement))))
    },
    testM("retrieve correcly replays the events from the storage") {
      for
        ref <- Ref.make(Map(id -> Queue(Event.Increment, Event.Increment, Event.Decrement)))
        journal = InMemoryStorage(ref)
        events <- journal.retrieve(id).run(Sink.collectAll)
      yield assert(events)(equalTo(Chunk(Event.Increment, Event.Increment, Event.Decrement)))
    },
    testM("retrieve fails to replay events if id is not found") {
      for
        ref <- Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty)
        journal = InMemoryStorage(ref)
        events <- journal.retrieve(id).run(Sink.collectAll).run
      yield assert(events)(fails(isSubtype[NoSuchElementException](anything)))
    }
  )
