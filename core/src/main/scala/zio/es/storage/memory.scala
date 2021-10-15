package zio.es.storage.memory

import zio.{Has, Ref, Tag, URLayer, ZIO, ZLayer}
import zio.es.{EventJournal, PersistenceId}
import zio.stream.ZStream
import java.util.NoSuchElementException
import scala.collection.immutable.Queue
import zio.es

case class InMemoryStorage[E](private val ref: Ref[Map[PersistenceId, Queue[E]]]) extends EventJournal[E]:
  override def persist(id: PersistenceId)(event: E) =
    ref.update(_.updatedWith(id)(_.map(_.enqueue(event)).orElse(Some(Queue(event)))))

  override def retrieve(id: PersistenceId) =
    ZStream.fromIterableM {
      for
        ids    <- ref.get
        events <- ZIO.fromOption(ids.get(id)).orElseFail(NoSuchElementException())
      yield events
    }

object InMemoryStorage:
  def layer[E](using
      Tag[Ref[Map[PersistenceId, Queue[E]]]],
      Tag[EventJournal[E]]
  ): URLayer[Has[Ref[Map[PersistenceId, Queue[E]]]], Has[EventJournal[E]]] =
    ZLayer.fromService(InMemoryStorage(_))
