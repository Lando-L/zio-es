import zio.{App, Dequeue, Has, Ref, RIO, Task, UManaged, URLayer, ZIO, ZLayer, ZManaged, console}
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
  def create(cart: PersistenceId): Task[Unit]
  def state(cart: PersistenceId): Task[State]
  def add(cart: PersistenceId)(item: String)(quantity: Int): Task[Unit]
  def remove(cart: PersistenceId)(item: String): Task[Unit]
  def subscribe(cart: PersistenceId): Task[UManaged[Dequeue[Event]]]

object ShoppingCart:
  def create(cart: PersistenceId): RIO[Has[ShoppingCart], Unit] =
    ZIO.serviceWith[ShoppingCart](_.create(cart))

  def state(cart: PersistenceId): RIO[Has[ShoppingCart], State] =
    ZIO.serviceWith[ShoppingCart](_.state(cart))

  def add(cart: PersistenceId)(item: String)(quantity: Int): RIO[Has[ShoppingCart], Unit] =
    ZIO.serviceWith[ShoppingCart](_.add(cart)(item)(quantity))

  def remove(cart: PersistenceId)(item: String): RIO[Has[ShoppingCart], Unit] =
    ZIO.serviceWith[ShoppingCart](_.remove(cart)(item))

  def subscribe(cart: PersistenceId): RIO[Has[ShoppingCart], UManaged[Dequeue[Event]]] =
    ZIO.serviceWith[ShoppingCart](_.subscribe(cart))

case class ShoppingCartLive(ref: Ref[Map[PersistenceId, EventSourcing[State, Event]]], journal: EventJournal[Event])
    extends ShoppingCart:
  override def create(cart: PersistenceId) =
    for
      storage <- ref.get
      if !storage.contains(cart)
      source <- journal.make[State](cart)(1) {
        case (state, Event.Added(item, quantity)) => state.add(item)(quantity)
        case (state, Event.Removed(item))         => state.remove(item)
      }
      _ <- ref.update(_ + (cart -> source))
    yield ()

  override def state(cart: PersistenceId) =
    for
      storage <- ref.get
      events  <- ZIO.fromOption(storage.get(cart)).orElseFail(NoSuchElementException())
      state   <- events.replay(State.empty)
    yield state

  override def add(cart: PersistenceId)(item: String)(quantity: Int) =
    for
      storage <- ref.get
      events  <- ZIO.fromOption(storage.get(cart)).orElseFail(NoSuchElementException())
      _       <- events.log(Event.Added(item, quantity))
    yield ()

  override def remove(cart: PersistenceId)(item: String) =
    for
      storage <- ref.get
      events  <- ZIO.fromOption(storage.get(cart)).orElseFail(NoSuchElementException())
      _       <- events.log(Event.Removed(item))
    yield ()

  override def subscribe(cart: PersistenceId) =
    for
      storage <- ref.get
      events  <- ZIO.fromOption(storage.get(cart)).orElseFail(NoSuchElementException())
    yield events.subscribe

object ShoppingCartLive:
  val layer: URLayer[Has[Ref[Map[PersistenceId, EventSourcing[State, Event]]]] & Has[EventJournal[Event]], Has[
    ShoppingCart
  ]] =
    ZLayer.fromServices[Ref[Map[PersistenceId, EventSourcing[State, Event]]], EventJournal[Event], ShoppingCart] {
      (ref, journal) => ShoppingCartLive(ref, journal)
    }

object Example extends App:
  override def run(args: List[String]) =
    val alice = PersistenceId("Alice")

    val program =
      ShoppingCart.create(alice).flatMap { _ =>
        ShoppingCart.subscribe(alice).flatMap { source =>
          source.use { subscription =>
            val stream = ZStream.fromQueue(subscription)

            for
              fiber  <- stream.take(3).map(_.toString).runCollect.fork
              _      <- ShoppingCart.add(alice)("Apples")(5)
              _      <- ShoppingCart.add(alice)("Algae")(2)
              _      <- ShoppingCart.remove(alice)("Algae")
              events <- fiber.join
              state  <- ShoppingCart.state(alice)
              _      <- console.putStrLn(state.toString)
              _      <- ZIO.foreach(events)(console.putStrLn(_))
            yield state
          }
        }
      }

    val journalRef  = ZLayer.fromEffect(Ref.make[Map[PersistenceId, Queue[Event]]](Map.empty))
    val shoppingRef = ZLayer.fromEffect(Ref.make[Map[PersistenceId, EventSourcing[State, Event]]](Map.empty))
    val env         = (shoppingRef ++ (journalRef >>> InMemoryStorage.layer)) >>> ShoppingCartLive.layer

    program.provideCustomLayer(env).exitCode
