package zio.es

import zio.stream.{Stream, ZStream}
import zio.{Has, Ref, RIO, Tag, Task, URLayer, ZIO, ZLayer}
import scala.collection.immutable.Queue
import java.util.NoSuchElementException

trait EventJournal[E]:
  def persist(persitenceId: PersitenceId)(event: E): Task[Unit]
  def retrieve(persitenceId: PersitenceId): Stream[Throwable, E]

object EventJournal:
  def persist[E](persitenceId: PersitenceId)(event: E): RIO[Has[EventJournal[E]], Unit] =
    ZIO.serviceWith[EventJournal[E]](_.persist(persitenceId)(event))

  def retrieve[E](persitenceId: PersitenceId): ZStream[Has[EventJournal[E]], Throwable, E] =
    ZStream.serviceWithStream[EventJournal[E]](_.retrieve(persitenceId))
  