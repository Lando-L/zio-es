package zio.es

import zio.stream.{Stream, ZStream}
import zio.{Dequeue, Has, Hub, Ref, RIO, Tag, Task, UIO, UManaged, URIO, URLayer, ZIO, ZLayer}
import scala.collection.immutable.Queue
import java.util.NoSuchElementException

trait EventJournal[E]:
  protected def persist(id: PersistenceId)(event: E): Task[Unit]
  protected def retrieve(id: PersistenceId): Stream[Throwable, E]

  def make[S](capacity: Int)(handler: (S, E) => S): UIO[EventSourcing[S, E]] =
    Hub.bounded[E](capacity).map { hub =>
      new EventSourcing[S, E]:
        override def log(id: PersistenceId)(event: E) =
          persist(id)(event).flatMap(_ => hub.publish(event)).unit

        override def replay(id: PersistenceId)(init: S) =
          retrieve(id).fold(init)(handler)

        override def subscribe =
          hub.subscribe
    }

  def makeM[S](capacity: Int)(handler: (S, E) => Task[S]): UIO[EventSourcing[S, E]] =
    Hub.bounded[E](capacity).map { hub =>
      new EventSourcing[S, E]:
        override def log(id: PersistenceId)(event: E) =
          persist(id)(event).flatMap(_ => hub.publish(event)).unit

        override def replay(id: PersistenceId)(init: S) =
          retrieve(id).foldM(init)(handler)

        override def subscribe =
          hub.subscribe
    }

object EventJournal:
  def make[S, E](capacity: Int)(handler: (S, E) => S)(using
      Tag[EventJournal[E]]
  ): URIO[Has[EventJournal[E]], EventSourcing[S, E]] =
    ZIO.serviceWith[EventJournal[E]](_.make(capacity)(handler))

  def makeM[S, E](capacity: Int)(handler: (S, E) => Task[S])(using
      Tag[EventJournal[E]]
  ): URIO[Has[EventJournal[E]], EventSourcing[S, E]] =
    ZIO.serviceWith[EventJournal[E]](_.makeM(capacity)(handler))

sealed trait EventSourcing[S, E]:
  def log(id: PersistenceId)(event: E): Task[Unit]
  def replay(id: PersistenceId)(init: S): Task[S]
  def subscribe: UManaged[Dequeue[E]]
