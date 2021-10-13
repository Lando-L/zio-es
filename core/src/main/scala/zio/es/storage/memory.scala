package zio.es.storage.memory

import zio.{Has, Ref, ULayer, ZIO, ZLayer}
import zio.es.{EventJournal, PersitenceId}
import zio.stream.ZStream
import java.util.NoSuchElementException
import scala.collection.immutable.Queue
import zio.es

case class InMemoryStorage[E](private val ref: Ref[Map[PersitenceId, Queue[E]]]) extends EventJournal[E]:
  override def persist(persitenceId: PersitenceId)(event: E) =
    ref.update(_.updatedWith(persitenceId)(_.map(_.enqueue(event)).orElse(Some(Queue(event)))))

  override def retrieve(persitenceId: PersitenceId) =
    ZStream.fromIterableM {
      for
        ids    <- ref.get
        events <- ZIO.fromOption(ids.get(persitenceId)).orElseFail(NoSuchElementException())
      yield events
    }

object InMemoryStorage:
  def layer[E](ref: Ref[Map[PersitenceId, Queue[E]]]): ULayer[Has[EventJournal[E]]] =
    ZLayer.succeed(InMemoryStorage(ref))
