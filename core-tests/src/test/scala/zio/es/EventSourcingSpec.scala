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

    object Persist  extends Effect[(PersistenceId, Event), Throwable, Unit]
    object Retrieve extends Stream[PersistenceId, Throwable, Event]

    val compose: URLayer[Has[Proxy], Has[EventJournal[Event]]] =
      ZLayer.fromServiceM { proxy =>
        withRuntime[Any].map { runtime =>
          new EventJournal[Event]:
            override def persist(id: PersistenceId)(event: Event) =
              proxy(Persist, id, event)

            override def retrieve(id: PersistenceId) =
              runtime.unsafeRun(proxy(Retrieve, id))
        }
      }

  val id    = PersistenceId("1")
  val event = Event.Increment

  def spec = suite("EventSourcingSpec")(
    testM("log correctly persists an event and publishes it subsequently") {

      val program =
        EventJournal.make[Any, Event](id)(1)((state, _) => state).flatMap { journal =>
          journal.subscribe.use { sub =>
            for
              _      <- journal.log(event)
              events <- sub.takeAll
            yield events
          }
        }

      val env    = EventJournalMock.Persist(equalTo((id, event)), unit)
      val result = program.provideLayer(env)

      assertM(result)(equalTo(List(event)))
    },
    testM("reply correctly aggregates stored events") {
      def eventHandler(state: Int, event: Event): Int =
        event match
          case Event.Increment => state + 1
          case Event.Decrement => state - 1
          case Event.Reset     => 0

      val program = EventJournal.make[Int, Event](id)(1)(eventHandler).flatMap(_.replay(0))

      val env = EventJournalMock.Retrieve(
        equalTo(id),
        value(ZStream.fromIterable(List(Event.Increment, Event.Increment, Event.Decrement)))
      )
      val result = program.provideLayer(env)

      assertM(result)(equalTo(1))
    }
  )
