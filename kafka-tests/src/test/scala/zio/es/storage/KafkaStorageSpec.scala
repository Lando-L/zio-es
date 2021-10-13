package zio.es.storage.kafka

import zio.ZIO
import zio.kafka.producer.ProducerSettings
import zio.es.PersitenceId
import zio.test.*
import zio.test.Assertion.*

object KafkaStorageSpec extends DefaultRunnableSpec:
  enum Event:
    case Increment
    case Decrement
    case Reset

  val id = PersitenceId("1")

  def spec = ???
