import kotlinx.coroutines.runBlocking
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

class KafkaProviderService(
    private val kafkaHost: String,
    private val kafkaPort: Int
) : RequestsProviderService {

    private lateinit var target: MidpointRepository

    override fun start(target: MidpointRepository) {
        this.target = target

        val props = getStreamsConfig()
        val builder = StreamsBuilder()

        val source = builder.stream<String, String>(INPUT_TOPIC)
        source.foreach { _, value -> handleMessage(value) }

        producer = KafkaProducer(props)

        val streams = KafkaStreams(builder.build(), props)
        val latch = CountDownLatch(1)

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(Thread {
            streams.close()
            latch.countDown()
        })
        try {
            streams.start()
            latch.await()
        } catch (e: Throwable) {
            exitProcess(1)
        }
        exitProcess(0)
    }

    private lateinit var producer: KafkaProducer<String, String>

    private fun getStreamsConfig(): Properties {
        val props = Properties()

        listOf(
            StreamsConfig.APPLICATION_ID_CONFIG to "streams-app",
            StreamsConfig.BOOTSTRAP_SERVERS_CONFIG to "$kafkaHost:$kafkaPort",
            StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG to 0,
            StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG to Serdes.String().javaClass.name,
            StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG to Serdes.String().javaClass.name,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer().javaClass.name,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer().javaClass.name
        ).forEach { (k, v) -> props.putIfAbsent(k, v) }

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
                with(target) {
                    val userId = getUserByFullName(model.name)
                    getUserShadows(userId!!)
                        .filter { shadowBelongsToLDAP(it) }
                        .forEach { setMemberOf(it, model.newGroup) }
                }
            }
        } catch (exception: Exception) {
            val record = ProducerRecord(OUTPUT_TOPIC, "error", "Error happened: $exception")
            producer.send(record)
        }
    }

    companion object {
        private const val INPUT_TOPIC = "requests-input"
        private const val OUTPUT_TOPIC = "requests-output"
    }
}
