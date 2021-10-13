package zio.es.storage.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Has, Ref, ULayer, ZIO, ZLayer}
import zio.es.{EventJournal, PersitenceId}
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
  override def persist(persitenceId: PersitenceId)(event: E) =
    producer.produce(topic, persitenceId, event, KafkaStorage.persitenceIdSerde, eventSerde).unit
  
  override def retrieve(persitenceId: PersitenceId) =
    consumer.plainStream(KafkaStorage.persitenceIdSerde, eventSerde).map(record => record.record.value())

object KafkaStorage:
  val persitenceIdSerde: Serde[Any, PersitenceId] =
    Serde.string.inmap(PersitenceId.apply)(PersitenceId.toString)

  def layer[E](topic: String)(consumer: Consumer)(producer: Producer)(eventSerde: Serde[Any, E]): ULayer[Has[EventJournal[E]]] =
    ZLayer.succeed(KafkaStorage(topic, consumer, producer, eventSerde))
