import zio.{App, Dequeue, Has, Ref, RIO, Task, UManaged, URLayer, URManaged, ZIO, ZLayer, ZManaged}
import zio.es.{EventJournal, EventJournalInMemory, EventSourcing, PersitenceId}
import zio.stream.{Sink, ZStream}
import java.util.NoSuchElementException
import scala.collection.immutable.Queue

// case class State(items: Map[String, Int])

// object State:
//   def empty: State = State(Map.empty)

// extension (state: State)
//   def add(item: String)(quantity: Int): State = state.copy(state.items + (item -> quantity))
//   def remove(item: String): State = state.copy(state.items - item)
//   def isEmpty: Boolean = state.items.isEmpty

// enum Event:
//   case Added(cart: String, item: String, quantity: Int)
//   case Removed(cart: String, item: String)

// trait ShoppingCart:
//   def state(cart: String): Task[State]
//   def add(cart: String)(item: String)(quantity: Int): Task[Unit]
//   def remove(cart: String)(item: String): Task[Unit]
//   def subscribe: UManaged[Dequeue[Event]]

// object ShoppingCart:
//   def state(cart: String): RIO[Has[ShoppingCart], State] =
//     ZIO.serviceWith[ShoppingCart](_.state(cart))

//   def add(cart: String)(item: String)(quantity: Int): RIO[Has[ShoppingCart], Unit] =
//     ZIO.serviceWith[ShoppingCart](_.add(cart)(item)(quantity))

//   def remove(cart: String)(item: String): RIO[Has[ShoppingCart], Unit] =
//     ZIO.serviceWith[ShoppingCart](_.remove(cart)(item))

//   def subscribe: URManaged[Has[ShoppingCart], Dequeue[Event]] =
//     ZManaged.serviceWithManaged[ShoppingCart](_.subscribe)

// case class InMemoryShoppingCart(carts: Ref[Map[String, State]], storage: EventSourcing[State, Event]) extends ShoppingCart:
//   override def state(cart: String): Task[State] =
//     for
//       states <- carts.get
//       state <- ZIO.fromOption(states.get(cart)).orElseFail(NoSuchElementException())
//     yield
//       state

//   override def add(cart: String)(item: String)(quantity: Int) =
//     for
//       states <- carts.get
//       _ <- carts.set(states + (cart -> states.getOrElse(cart, State.empty).add(item)(quantity)))
//       _ <- storage.log(PersitenceId(cart))(Event.Added(cart, item, quantity))
//     yield
//       ()

//   override def remove(cart: String)(item: String) =
//     for
//       states <- carts.get
//       _ <- carts.set(states + (cart -> states.getOrElse(cart, State.empty).remove(item)))
//       _ <- storage.log(PersitenceId(cart))(Event.Removed(cart, item))
//     yield
//       ()
  
//   override def subscribe =
//     storage.subscribe

// object InMemoryShoppingCart:
//   val layer: URLayer[Has[Ref[Map[String, State]]] & Has[EventSourcing[State, Event]], Has[ShoppingCart]] =
//     ZLayer.fromServices[Ref[Map[String, State]], EventSourcing[State, Event], ShoppingCart] { (carts, storage) =>
//       InMemoryShoppingCart(carts, storage)
//     }

// object Example extends App {
//   override def run(args: List[String]) =
//     val program = ShoppingCart.subscribe.use { subscription =>
//       val stream = ZStream.fromQueue(subscription)
      
//       for
//         _ <- ShoppingCart.add("Alice")("Apples")(5)
//         _ <- ShoppingCart.add("Bob")("Bananas")(10)
//      yield
//        () 
//     }

//     val carts: ZLayer[Any, Nothing, Has[Ref[Map[String, State]]]] = ZLayer.fromEffect(Ref.make[Map[String, State]](Map.empty))
    
//     val storage: ZLayer[Any, Nothing, Has[Ref[Map[PersitenceId, Queue[Event]]]]] = ZLayer.fromEffect(Ref.make[Map[PersitenceId, Queue[Event]]](Map.empty))
//     val journal: ZLayer[Any, Nothing, Has[EventJournal[Event]]] = storage >>> EventJournalInMemory.layer
//     val source: ZLayer[Any, Nothing, Has[EventSourcing[State, Event]]] = journal >>> ZLayer.fromEffect(EventSourcing.make[State, Event](1) {
//       case (state, Event.Added(_, item, quantity)) => state.add(item)(quantity)
//       case (state, Event.Removed(_, item)) => state.remove(item)
//     })

//     val shop: ZLayer[Any, Nothing, Has[Ref[Map[String, State]]] & Has[EventSourcing[State, Event]]] = carts ++ source

//     val env: ZLayer[Any, Nothing, Has[ShoppingCart]] = shop >>> InMemoryShoppingCart.layer

//     program.provideLayer(env).exitCode
// }
