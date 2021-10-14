package zio.es

import zio.stream.ZStream
import zio.test.*
import zio.test.Assertion.*
import zio.test.mock.*
import zio.test.mock.Expectation.*
import zio.test.mock.MockSystem
import zio.{Has, RIO, Task, ULayer, URLayer, ZLayer}

object EventSourcingSpec extends DefaultRunnableSpec:
  enum Event:
    case Increment, Decrement, Reset

  object EventJournalMock extends Mock[Has[EventJournal[Event]]]:
  
    object Persist extends Effect[(PersitenceId, Event), Throwable, Unit]
    object Retrieve extends Stream[PersitenceId, Throwable, Event]

    val compose: URLayer[Has[Proxy], Has[EventJournal[Event]]] =
      ZLayer.fromServiceM { proxy =>
        withRuntime[Any].map { runtime =>
          new EventJournal[Event]:
            override def persist(persitenceId: PersitenceId)(event: Event) =
              proxy(Persist, persitenceId, event)
            
            override def retrieve(persitenceId: PersitenceId) =
              runtime.unsafeRun(proxy(Retrieve, persitenceId))
        }
      }

  val id = PersitenceId("1")
  val event = Event.Increment
  
  def spec = suite("EventSourcingSpec")(
    testM("log correctly persists an event and publishes it subsequently") {
      
      val program = EventSourcing.make[Any, Event](1)((state, _) => state).flatMap { source =>
        source.subscribe.use { subscription =>
          for
            _ <- source.log(id)(event)
            published <- subscription.takeAll
          yield
            published
        }
      }

      val env = EventJournalMock.Persist(equalTo((id, event)), unit)
      val result = program.provideLayer(env)

      assertM(result)(equalTo(List(event)))
    },

    testM("reply correctly aggregates stored events") {
      def eventHandler(state: Int, event: Event): Int =
        event match
          case Event.Increment => state + 1
          case Event.Decrement => state - 1
          case Event.Reset => 0

      val program = EventSourcing.make[Int, Event](1)(eventHandler).flatMap(_.replay(id)(0))

      val env = EventJournalMock.Retrieve(equalTo(id), value(ZStream.fromIterable(List(Event.Increment, Event.Increment, Event.Decrement))))
      val result = program.provideLayer(env)

      assertM(result)(equalTo(1))
    }
  )
