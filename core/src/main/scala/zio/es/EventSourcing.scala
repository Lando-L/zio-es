package zio.es

import zio.stream.{Stream, ZStream}
import zio.{Dequeue, Has, Hub, Tag, Task, ULayer, URIO, UManaged, ZIO, ZLayer}

sealed trait EventSourcing[S, E](private val hub: Hub[E]):
  def log(id: PersitenceId)(event: E): Task[Unit]
  def replay(id: PersitenceId)(init: S): Task[S]
  def subscribe: UManaged[Dequeue[E]]

object EventSourcing:
  def make[S, E](
      capacity: Int
  )(handler: (S, E) => S)(using Tag[EventJournal[E]]): URIO[Has[EventJournal[E]], EventSourcing[S, E]] =
    ZIO.serviceWith[EventJournal[E]] { journal =>
      Hub.bounded[E](capacity).map { hub =>
        new EventSourcing[S, E](hub):
          override def log(id: PersitenceId)(event: E) =
            journal
              .persist(id)(event)
              .flatMap(_ => hub.publish(event))
              .unit

          override def replay(id: PersitenceId)(init: S) =
            journal.retrieve(id).fold(init)(handler)

          override def subscribe =
            hub.subscribe
      }
    }

  def makeM[S, E](
      capacity: Int
  )(handler: (S, E) => Task[S])(using Tag[EventJournal[E]]): URIO[Has[EventJournal[E]], EventSourcing[S, E]] =
    ZIO.serviceWith[EventJournal[E]] { journal =>
      Hub.bounded[E](capacity).map { hub =>
        new EventSourcing[S, E](hub):
          override def log(id: PersitenceId)(event: E) =
            journal
              .persist(id)(event)
              .flatMap(_ => hub.publish(event))
              .unit

          override def replay(id: PersitenceId)(init: S) =
            journal.retrieve(id).foldM(init)(handler)

          override def subscribe =
            hub.subscribe
      }
    }
