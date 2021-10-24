package zio.es

import zio.stream.ZStream
import zio.test.*
import zio.test.Assertion.*
import zio.test.mock.*
import zio.test.mock.Expectation.*
import zio.test.mock.MockSystem
import zio.{Has, RIO, Task, ULayer, URLayer, ZLayer}
import java.util.NoSuchElementException

object EventSourcingSpec extends DefaultRunnableSpec:
  enum Event:
    case Increment, Decrement

  object StateStorageMock extends Mock[Has[StateStorage[Int]]]:
    object Retrieve extends Effect[PersistenceId, Throwable, Int]
    object Update   extends Effect[(PersistenceId, Int => Int), Throwable, Unit]

    val compose: URLayer[Has[Proxy], Has[StateStorage[Int]]] =
      ZLayer.fromService { proxy =>
        new StateStorage[Int]:
          override def retrieve(id: PersistenceId) =
            proxy(Retrieve, id)

          override def update(id: PersistenceId)(f: Int => Int) =
            proxy(Update, id, f)
      }

  object EventJournalMock extends Mock[Has[EventJournal[Event]]]:

    object Persist extends Effect[(PersistenceId, Event), Throwable, Unit]
    object Replay  extends Stream[PersistenceId, Throwable, Event]

    val compose: URLayer[Has[Proxy], Has[EventJournal[Event]]] =
      ZLayer.fromServiceM { proxy =>
        withRuntime[Any].map { runtime =>
          new EventJournal[Event]:
            override def persist(id: PersistenceId)(event: Event) =
              proxy(Persist, id, event)

            override def replay(id: PersistenceId) =
              runtime.unsafeRun(proxy(Replay, id))
        }
      }

  val id    = PersistenceId("1")
  val event = Event.Increment
  val state = 1

  def eventHandler(state: Int, event: Event): Int =
    event match
      case Event.Increment => state + 1
      case Event.Decrement => state - 1

  def spec = suite("EventSourcingSpec")(
    testM("log correctly persists an event and publishes it subsequently") {
      val program = EventSourcing.make[Int, Event](1)((state, _) => state)(0).flatMap { source =>
        source.subscribe.use { sub =>
          for
            _      <- source.log(id)(event)
            events <- sub.takeAll
          yield events
        }
      }

      val env = EventJournalMock.Persist(equalTo((id, event)), unit)
        ++ StateStorageMock.Update(isSubtype[(PersistenceId, Int => Int)](anything))

      val result = program.provideLayer(env)

      assertM(result)(equalTo(List((id, event))))
    },
    testM("get correctly fetches states from storage") {
      val program = EventSourcing.make[Int, Event](1)((state, _) => state)(0).flatMap(_.get(id))
      val env     = StateStorageMock.Retrieve(equalTo(id), value(state)) ++ EventJournalMock.empty
      val result  = program.provideLayer(env)

      assertM(result)(equalTo(state))
    },
    testM("get correctly replays events from storage if the state is not found") {
      val program = EventSourcing.make[Int, Event](1)(eventHandler)(0).flatMap(_.get(id))
      val env = StateStorageMock.Retrieve(equalTo(id), failure(NoSuchElementException()))
        ++ EventJournalMock.Replay(
          equalTo(id),
          value(ZStream.fromIterable(List(Event.Increment, Event.Increment, Event.Decrement)))
        )
        ++ StateStorageMock.Update(isSubtype[(PersistenceId, Int => Int)](anything))

      val result = program.provideLayer(env)

      assertM(result)(equalTo(state))
    },
    testM("get fails to fetch states if neither the state nor the events are found") {
      val program = EventSourcing.make[Int, Event](1)(eventHandler)(0).flatMap(_.get(id))
      val env = StateStorageMock.Retrieve(equalTo(id), failure(NoSuchElementException()))
        ++ EventJournalMock.Replay(equalTo(id), value(ZStream.fail(NoSuchElementException())))

      val result = program.provideLayer(env).run

      assertM(result)(fails(isSubtype[NoSuchElementException](anything)))
    }
  )
