import zio.{App, Dequeue, Has, Ref, RIO, Task, UManaged, URLayer, URManaged, ZIO, ZLayer, ZManaged, console}
import zio.es.{EventJournal, EventSourcing, PersitenceId}
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
  case Added(cart: String, item: String, quantity: Int)
  case Removed(cart: String, item: String)

trait ShoppingCart:
  def state(cart: String): Task[State]
  def add(cart: String)(item: String)(quantity: Int): Task[Unit]
  def remove(cart: String)(item: String): Task[Unit]
  def subscribe: UManaged[Dequeue[Event]]

object ShoppingCart:
  def state(cart: String): RIO[Has[ShoppingCart], State] =
    ZIO.serviceWith[ShoppingCart](_.state(cart))

  def add(cart: String)(item: String)(quantity: Int): RIO[Has[ShoppingCart], Unit] =
    ZIO.serviceWith[ShoppingCart](_.add(cart)(item)(quantity))

  def remove(cart: String)(item: String): RIO[Has[ShoppingCart], Unit] =
    ZIO.serviceWith[ShoppingCart](_.remove(cart)(item))

  def subscribe: URManaged[Has[ShoppingCart], Dequeue[Event]] =
    ZManaged.serviceWithManaged[ShoppingCart](_.subscribe)

case class InMemoryShoppingCart(storage: EventSourcing[State, Event]) extends ShoppingCart:
  override def state(cart: String): Task[State] =
    storage.replay(PersitenceId(cart))(State.empty)

  override def add(cart: String)(item: String)(quantity: Int) =
    storage.log(PersitenceId(cart))(Event.Added(cart, item, quantity))

  override def remove(cart: String)(item: String) =
    storage.log(PersitenceId(cart))(Event.Removed(cart, item))

  override def subscribe =
    storage.subscribe

object InMemoryShoppingCart:
  val layer: URLayer[Has[EventSourcing[State, Event]], Has[ShoppingCart]] =
    ZLayer.fromService(InMemoryShoppingCart(_))

object Example extends App:
  override def run(args: List[String]) =
    val program =
      ShoppingCart.subscribe.use { subscription =>
        val stream = ZStream.fromQueue(subscription)

        val alice =
          for
            _     <- ShoppingCart.add("Alice")("Apples")(5)
            _     <- ShoppingCart.add("Alice")("Algae")(2)
            _     <- ShoppingCart.remove("Alice")("Algae")
            state <- ShoppingCart.state("Alice")
          yield state

        val bob =
          for
            _     <- ShoppingCart.add("Bob")("Bananas")(10)
            _     <- ShoppingCart.add("Bob")("Bees")(20)
            _     <- ShoppingCart.add("Bob")("Biscuits")(5)
            state <- ShoppingCart.state("Bob")
          yield state

        for
          fiber     <- stream.take(6).map(_.toString).runCollect.fork
          cartBob   <- bob
          cartAlice <- alice
          events    <- fiber.join
          _         <- console.putStrLn(cartAlice.toString)
          _         <- console.putStrLn(cartBob.toString)
          _         <- ZIO.foreach(events)(console.putStrLn(_))
        yield ()
      }

    val ref =
      ZLayer.fromEffect(
        Ref.make[Map[PersitenceId, Queue[Event]]](Map.empty)
      )

    val source =
      ZLayer.fromEffect(
        EventSourcing.make[State, Event](1) {
          case (state, Event.Added(_, item, quantity)) => state.add(item)(quantity)
          case (state, Event.Removed(_, item))         => state.remove(item)
        }
      )

    val env =
      ref >>> InMemoryStorage.layer >>> source >>> InMemoryShoppingCart.layer

    program.provideCustomLayer(env).exitCode
