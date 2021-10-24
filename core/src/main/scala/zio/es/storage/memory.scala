package zio.es.storage.memory

import zio.{Has, Ref, Tag, URLayer, ZIO, ZLayer}
import zio.es.{EventJournal, PersistenceId, StateStorage}
import zio.stream.ZStream
import java.util.NoSuchElementException
import scala.collection.immutable.Queue

case class InMemoryStorage[S](private val states: Ref[Map[PersistenceId, S]]) extends StateStorage[S]:
  override def retrieve(id: PersistenceId) =
    states.get.flatMap { ref =>
      ZIO.fromOption(ref.get(id)).orElseFail(NoSuchElementException())
    }

  override def update(id: PersistenceId)(f: S => S) =
    states.update(_.updatedWith(id)(_.map(f)))

object InMemoryStorage:
  def layer[S](using
      Tag[Ref[Map[PersistenceId, S]]],
      Tag[StateStorage[S]]
  ): URLayer[Has[Ref[Map[PersistenceId, S]]], Has[StateStorage[S]]] =
    ZLayer.fromService(InMemoryStorage[S](_))

case class InMemoryJournal[E](private val events: Ref[Map[PersistenceId, Queue[E]]]) extends EventJournal[E]:
  override def persist(id: PersistenceId)(event: E) =
    events.update(_.updatedWith(id)(_.map(_.enqueue(event)).orElse(Some(Queue(event)))))

  override def replay(id: PersistenceId) =
    ZStream.fromIterableM {
      events.get.flatMap { ref =>
        ZIO.fromOption(ref.get(id)).orElseFail(NoSuchElementException())
      }
    }

object InMemoryJournal:
  def layer[S, E](using
      Tag[Ref[Map[PersistenceId, Queue[E]]]],
      Tag[EventJournal[E]]
  ): URLayer[Has[Ref[Map[PersistenceId, Queue[E]]]], Has[EventJournal[E]]] =
    ZLayer.fromService(InMemoryJournal[E](_))
