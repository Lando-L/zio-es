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
    testM("retrieve correcly fetches states from the storage") {
      for
        states <- Ref.make(Map(id -> state))
        storage = InMemoryStorage(states)
        result <- storage.retrieve(id)
      yield assert(result)(equalTo(state))
    },
    testM("retrieve fails to fetch states if their id is not found") {
      for
        states <- Ref.make[Map[PersistenceId, Int]](Map.empty)
        storage = InMemoryStorage(states)
        result <- storage.retrieve(id).run
      yield assert(result)(fails(isSubtype[NoSuchElementException](anything)))
    },
    testM("update correcly modifies states within the storage") {
      for
        states <- Ref.make(Map(id -> state))
        storage = InMemoryStorage(states)
        _      <- storage.update(id)(_ + 1)
        result <- storage.retrieve(id)
      yield assert(result)(equalTo(state + 1))
    },
    testM("update fails to modify states if their id is not found") {
      for
        states <- Ref.make[Map[PersistenceId, Int]](Map.empty)
        storage = InMemoryStorage(states)
        _      <- storage.update(id)(_ + 1)
        result <- storage.retrieve(id).run
      yield assert(result)(fails(isSubtype[NoSuchElementException](anything)))
    }
  )
