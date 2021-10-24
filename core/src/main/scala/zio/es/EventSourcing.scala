package zio.es

import zio.stream.{Stream, ZStream}
import zio.{Dequeue, Has, Hub, Ref, RIO, Tag, Task, UIO, UManaged, URIO, URLayer, ZIO, ZLayer}
import java.util.NoSuchElementException

trait StateStorage[S]:
  def retrieve(id: PersistenceId): Task[S]
  def update(id: PersistenceId)(f: S => S): Task[Unit]

trait EventJournal[E]:
  def persist(id: PersistenceId)(event: E): Task[Unit]
  def replay(id: PersistenceId): Stream[Throwable, E]

sealed trait EventSourcing[S, E]:
  def log(id: PersistenceId)(event: E): Task[Unit]
  def get(id: PersistenceId): Task[S]
  def subscribe: UManaged[Dequeue[(PersistenceId, E)]]

object EventSourcing:
  def make[S, E](capacity: Int)(handler: (S, E) => S)(init: => S)(using
      Tag[StateStorage[S]],
      Tag[EventJournal[E]]
  ): URIO[Has[StateStorage[S]] & Has[EventJournal[E]], EventSourcing[S, E]] =
    ZIO.services[StateStorage[S], EventJournal[E]].flatMap { (storage, journal) =>
      Hub.bounded[(PersistenceId, E)](capacity).map { hub =>
        new EventSourcing[S, E]:
          override def log(id: PersistenceId)(event: E) =
            for
              _ <- journal.persist(id)(event)
              _ <- storage.update(id)(handler(_, event))
              _ <- hub.publish((id, event))
            yield ()

          override def get(id: PersistenceId) =
            storage.retrieve(id) <> journal
              .replay(id)
              .fold(init)(handler)
              .flatMap(state => storage.update(id)(_ => state).as(state))

          override def subscribe =
            hub.subscribe
      }
    }
