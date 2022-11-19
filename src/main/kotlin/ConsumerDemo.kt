import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

@Serializable
data class KafkaMessageModel(val name: String, val newGroup: String)

object ConsumerDemo {
    private const val INPUT_TOPIC = "requests-input"
    private const val OUTPUT_TOPIC = "requests-output"

    private lateinit var producer: KafkaProducer<String, String>

    private fun getStreamsConfig(): Properties {
        val props = Properties()

        props.putIfAbsent(StreamsConfig.APPLICATION_ID_CONFIG, "streams-app")
        props.putIfAbsent(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "80.89.203.177:9092")
        props.putIfAbsent(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0)
        props.putIfAbsent(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().javaClass.name)
        props.putIfAbsent(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().javaClass.name)
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer().javaClass.name)
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer().javaClass.name)

        // setting offset reset to earliest so that we can re-run the demo code with the same pre-loaded data
        // Note: To re-run the demo, you need to use the offset reset tool:
        // https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        return props
    }

    private fun handleMessage(message: String) {
        println("New message: '$message'")

        try {
            val model = Json.decodeFromString<KafkaMessageModel>(message)
            val record = ProducerRecord(OUTPUT_TOPIC, "info", "Received request: $model")
            producer.send(record)

            runBlocking {
                val userId = getMidpointUserByFullName(model.name)
                getUserShadows(UserShadowsRequestInfo(userId!!))
                    .filter { isLdapShadow(it) }
                    .forEach { changeMemberOf(ShadowAddMemberOfRequestInfo(it, model.newGroup)) }
            }
        } catch (exception: Exception) {
            val record = ProducerRecord(OUTPUT_TOPIC, "error", "Error happened: $exception")
            producer.send(record)
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 4) {
            throw IllegalArgumentException("wrong amount of args")
        }
        val (host, portString, userName, password) = args
        midpoint = Midpoint(host, portString.toInt(), userName, password)

        val props = getStreamsConfig()
        val builder = StreamsBuilder()

        val source = builder.stream<String, String>(INPUT_TOPIC)
        source.foreach { _, value -> handleMessage(value) }

        producer = KafkaProducer(props)

        val streams = KafkaStreams(builder.build(), props)
        val latch = CountDownLatch(1)

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(object : Thread("streams-shutdown-hook") {
            override fun run() {
                streams.close()
                latch.countDown()
            }
        })
        try {
            streams.start()
            latch.await()
        } catch (e: Throwable) {
            exitProcess(1)
        }
        exitProcess(0)
    }
}