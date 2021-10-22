package zio.es.storage.memory

import zio.{Has, Ref, Tag, URLayer, ZIO, ZLayer}
import zio.es.{EventJournal, PersistenceId}
import zio.stream.ZStream
import java.util.NoSuchElementException
import scala.collection.immutable.Queue

case class InMemoryStorage[S, E](
    private val states: Ref[Map[PersistenceId, S]],
    private val events: Ref[Map[PersistenceId, Queue[E]]]
) extends EventJournal[S, E]:
  override def persist(id: PersistenceId)(event: E) =
    events.update(_.updatedWith(id)(_.map(_.enqueue(event)).orElse(Some(Queue(event)))))

  override def replay(id: PersistenceId) =
    ZStream.fromIterableM {
      events.get.flatMap { ref =>
        ZIO.fromOption(ref.get(id)).orElseFail(NoSuchElementException())
      }
    }

  override def retrieve(id: PersistenceId) =
    states.get.flatMap { ref =>
      ZIO.fromOption(ref.get(id)).orElseFail(NoSuchElementException())
    }

object InMemoryStorage:
  def layer[S, E](using
      Tag[Ref[Map[PersistenceId, S]]],
      Tag[Ref[Map[PersistenceId, Queue[E]]]],
      Tag[EventJournal[S, E]]
  ): URLayer[Has[Ref[Map[PersistenceId, S]]] & Has[Ref[Map[PersistenceId, Queue[E]]]], Has[EventJournal[S, E]]] =
    ZLayer.fromServices((states, events) => InMemoryStorage[S, E](states, events))
