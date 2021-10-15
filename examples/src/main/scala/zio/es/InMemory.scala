import zio.{App, Dequeue, Has, Ref, RIO, Task, UManaged, URLayer, URManaged, ZIO, ZLayer, ZManaged, console}
import zio.es.{EventJournal, EventSourcing, PersistenceId}
import zio.es.storage.memory.InMemoryStorage
import zio.stream.{Sink, ZStream}
import java.util.NoSuchElementException
import scala.collection.immutable.Queue

case class State(items: Map[String, Int])

object State:
  def empty: State = State(Map.empty)

extension (state: State)
  def add(item: String)(quantity: Int): State = state.copy(state.items + (item -> quantity))
  def remove(item: String): State             = state.copy(state.items - item)
  def isEmpty: Boolean                        = state.items.isEmpty

enum Event:
  case Added(item: String, quantity: Int)
  case Removed(item: String)

trait ShoppingCart:
  def get(cart: PersistenceId): Task[State]
  def add(cart: PersistenceId)(item: String)(quantity: Int): Task[Unit]
  def remove(cart: PersistenceId)(item: String): Task[Unit]
  def subscribe: UManaged[Dequeue[Event]]

object ShoppingCart:
  def get(cart: PersistenceId): RIO[Has[ShoppingCart], State] =
    ZIO.serviceWith[ShoppingCart](_.get(cart))

  def add(cart: PersistenceId)(item: String)(quantity: Int): RIO[Has[ShoppingCart], Unit] =
    ZIO.serviceWith[ShoppingCart](_.add(cart)(item)(quantity))

  def remove(cart: PersistenceId)(item: String): RIO[Has[ShoppingCart], Unit] =
    ZIO.serviceWith[ShoppingCart](_.remove(cart)(item))

  def subscribe: URManaged[Has[ShoppingCart], Dequeue[Event]] =
    ZManaged.serviceWithManaged[ShoppingCart](_.subscribe)

case class ShoppingCartLive(source: EventSourcing[State, Event]) extends ShoppingCart:
  override def get(cart: PersistenceId) =
    source.replay(cart)(State.empty)

  override def add(cart: PersistenceId)(item: String)(quantity: Int) =
    source.log(cart)(Event.Added(item, quantity))

  override def remove(cart: PersistenceId)(item: String) =
    source.log(cart)(Event.Removed(item))

  override def subscribe =
    source.subscribe

object ShoppingCartLive:
  val layer: URLayer[Has[EventSourcing[State, Event]], Has[ShoppingCart]] =
    ZLayer.fromService(ShoppingCartLive(_))

object Example extends App:
  override def run(args: List[String]) =
    val alice = PersistenceId("Alice")

    val program =
      ShoppingCart.subscribe.use { subscription =>
        val stream = ZStream.fromQueue(subscription)

        for
          fiber  <- stream.take(3).map(_.toString).runCollect.fork
          _      <- ShoppingCart.add(alice)("Apples")(5)
          _      <- ShoppingCart.add(alice)("Algae")(2)
          _      <- ShoppingCart.remove(alice)("Algae")
          events <- fiber.join
          state  <- ShoppingCart.get(alice)
          _      <- console.putStrLn(state.toString)
          _      <- ZIO.foreach(events)(console.putStrLn(_))
        yield state
      }

    val journal =
      ZLayer.fromEffect(
        Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty)
      )

    val source =
      ZLayer.fromEffect(
        EventJournal.make[State, Event](1) {
          case (state, Event.Added(item, quantity)) => state.add(item)(quantity)
          case (state, Event.Removed(item))         => state.remove(item)
        }
      )

    val env =
      journal >>> InMemoryStorage.layer >>> source >>> ShoppingCartLive.layer

    program.provideCustomLayer(env).exitCode
