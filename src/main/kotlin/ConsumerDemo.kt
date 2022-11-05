package com.tmpproduction.ldapservice

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

object ConsumerDemo {
    private const val INPUT_TOPIC = "streams-plaintext-input"

    private fun getStreamsConfig(args: Array<String>?): Properties {
        val props = Properties()
        if (args != null && args.isNotEmpty()) {
            FileInputStream(args[0]).use { fis -> props.load(fis) }
            if (args.size > 1) {
                println("Warning: Some command line arguments were ignored. This demo only accepts an optional configuration file.")
            }
        }
        props.putIfAbsent(StreamsConfig.APPLICATION_ID_CONFIG, "streams-app")
        props.putIfAbsent(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
        props.putIfAbsent(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0)
        props.putIfAbsent(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().javaClass.name)
        props.putIfAbsent(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().javaClass.name)

        // setting offset reset to earliest so that we can re-run the demo code with the same pre-loaded data
        // Note: To re-run the demo, you need to use the offset reset tool:
        // https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        return props
    }

    private fun handleMessage(message: String) {
        println(message)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val props = getStreamsConfig(args)
        val builder = StreamsBuilder()

        val source = builder.stream<String, String>(INPUT_TOPIC)
        source.foreach { _, value -> handleMessage(value) }

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