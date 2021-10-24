package zio.es.storage.memory

import zio.{Chunk, Ref}
import zio.es.PersistenceId
import zio.stream.Sink
import zio.test.*
import zio.test.Assertion.*
import scala.collection.immutable.Queue
import java.util.NoSuchElementException

object InMemoryJournalSpec extends DefaultRunnableSpec:
  enum Event:
    case Increment, Decrement

  val id    = PersistenceId("1")
  val state = 1

  def spec = suite("InMemoryJournalSpec")(
    testM("persist correctly appends an event to the journal") {
      for
        events <- Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty)
        journal = InMemoryJournal(events)
        _      <- journal.persist(id)(Event.Increment)
        _      <- journal.persist(id)(Event.Increment)
        _      <- journal.persist(id)(Event.Decrement)
        result <- events.get
      yield assert(result.get(id))(isSome(equalTo(Queue(Event.Increment, Event.Increment, Event.Decrement))))
    },
    testM("replay correcly fetches events from the journal") {
      for
        events <- Ref.make(Map(id -> Queue(Event.Increment, Event.Increment, Event.Decrement)))
        journal = InMemoryJournal(events)
        result <- journal.replay(id).run(Sink.collectAll)
      yield assert(result)(equalTo(Chunk(Event.Increment, Event.Increment, Event.Decrement)))
    },
    testM("replay fails to fetch events if their id is not found") {
      for
        events <- Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty)
        journal = InMemoryJournal(events)
        result <- journal.replay(id).run(Sink.collectAll).run
      yield assert(result)(fails(isSubtype[NoSuchElementException](anything)))
    }
  )
