package zio.es

import zio.stream.{Stream, ZStream}
import zio.{Dequeue, Has, Hub, Ref, RIO, Tag, Task, UIO, UManaged, URIO, URLayer, ZIO, ZLayer}
import java.util.NoSuchElementException

trait EventJournal[S, E]:
  protected def persist(id: PersistenceId)(event: E): Task[Unit]
  protected def replay(id: PersistenceId): Stream[Throwable, E]
  protected def retrieve(id: PersistenceId): Task[S]

  def make(capacity: Int)(handler: (S, E) => S)(init: => S): UIO[EventSourcing[S, E]] =
    Hub.bounded[(PersistenceId, E)](capacity).map { hub =>
      new EventSourcing[S, E]:
        override def log(id: PersistenceId)(event: E) =
          persist(id)(event).flatMap(_ => hub.publish((id, event))).unit

        override def get(id: PersistenceId) =
          retrieve(id) <> replay(id).fold(init)(handler)

        override def subscribe =
          hub.subscribe
    }

  def makeM(capacity: Int)(handler: (S, E) => Task[S])(init: => S): UIO[EventSourcing[S, E]] =
    Hub.bounded[(PersistenceId, E)](capacity).map { hub =>
      new EventSourcing[S, E]:
        override def log(id: PersistenceId)(event: E) =
          persist(id)(event).flatMap(_ => hub.publish((id, event))).unit

        override def get(id: PersistenceId) =
          retrieve(id) <> replay(id).foldM(init)(handler)

        override def subscribe =
          hub.subscribe
    }

object EventJournal:
  def make[S, E](capacity: Int)(handler: (S, E) => S)(init: => S)(using
      Tag[EventJournal[S, E]]
  ): URIO[Has[EventJournal[S, E]], EventSourcing[S, E]] =
    ZIO.serviceWith[EventJournal[S, E]](_.make(capacity)(handler)(init))

  def makeM[S, E](capacity: Int)(handler: (S, E) => Task[S])(init: => S)(using
      Tag[EventJournal[S, E]]
  ): URIO[Has[EventJournal[S, E]], EventSourcing[S, E]] =
    ZIO.serviceWith[EventJournal[S, E]](_.makeM(capacity)(handler)(init))

sealed trait EventSourcing[S, E]:
  def log(id: PersistenceId)(event: E): Task[Unit]
  def get(id: PersistenceId): Task[S]
  def subscribe: UManaged[Dequeue[(PersistenceId, E)]]
