package zio.es.storage.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Has, Ref, ULayer, ZIO, ZLayer}
import zio.es.{EventJournal, PersistenceId}
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde
import zio.stream.ZStream

case class KafkaStorage[E](
  private val topic: String,
  private val consumer: Consumer,
  private val producer: Producer,
  private val eventSerde: Serde[Any, E]
) extends EventJournal[E]:
  override def persist(id: PersistenceId)(event: E) =
    producer
      .produce(topic, id, event, KafkaStorage.persistenceIdSerde, eventSerde)
      .unit
  
  override def retrieve(id: PersistenceId) =
    consumer
      .plainStream(KafkaStorage.persistenceIdSerde, eventSerde)
      .filter(record => record.record.key() == id)
      .map(record => record.record.value())

object KafkaStorage:
  val persistenceIdSerde: Serde[Any, PersistenceId] =
    Serde.string.inmap(PersistenceId.apply)(PersistenceId.toString)

  def layer[E](topic: String)(consumer: Consumer)(producer: Producer)(eventSerde: Serde[Any, E]): ULayer[Has[EventJournal[E]]] =
    ZLayer.succeed(KafkaStorage(topic, consumer, producer, eventSerde))
