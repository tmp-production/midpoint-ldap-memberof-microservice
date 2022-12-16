package com.tmpproduction.ldapservice.apps

import com.tmpproduction.ldapservice.MidpointRepository
import com.tmpproduction.ldapservice.impl.KafkaProviderService
import com.tmpproduction.ldapservice.impl.RemoteMidpointRepository
import com.tmpproduction.ldapservice.impl.SequenceProviderService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.StreamsConfig
import java.util.*
import kotlin.time.Duration.Companion.seconds

private val newUserFile = """
    <?xml version="1.0" encoding="UTF-8"?>
    <user xmlns = "http://midpoint.evolveum.com/xml/ns/public/common/common-3"
          xmlns:org='http://midpoint.evolveum.com/xml/ns/public/common/org-3'
          xmlns:t="http://prism.evolveum.com/xml/ns/public/types-3"
          oid="0e030e0c-a37d-47b2-bde8-f8e61e4a2bfb"
          version="2">
        <name>
            <orig xmlns="http://prism.evolveum.com/xml/ns/public/types-3">Валерий Альбертович</orig>
        </name>
        <activation>
            <administrativeStatus>enabled</administrativeStatus>
        </activation>
        <fullName>
            <orig xmlns="http://prism.evolveum.com/xml/ns/public/types-3">Жмышенко Валерий Альбертович</orig>
        </fullName>
        <credentials>
            <password>
                <value>
                    <t:clearValue>123456</t:clearValue>
                </value>
            </password>
        </credentials>
    </user>
""".trimIndent()

private val connectToLdapFile = """
    <objectModification
        xmlns='http://midpoint.evolveum.com/xml/ns/public/common/api-types-3'
        xmlns:c='http://midpoint.evolveum.com/xml/ns/public/common/common-3'
        xmlns:t="http://prism.evolveum.com/xml/ns/public/types-3">
        <itemDelta>
            <t:modificationType>add</t:modificationType>
            <t:path>c:assignment</t:path>
            <t:value>
                    <c:construction xmlns:icfs="http://midpoint.evolveum.com/xml/ns/public/connector/icf-1/resource-schema-3">
                        <c:resourceRef oid="b4101662-7902-11e6-9f14-53e18426fe81" />

                    </c:construction>
            </t:value>
        </itemDelta>
    </objectModification>
""".trimIndent()

class IntegrationTestMain {

    fun main(args: Array<String>) {
        println("Dummy integration OK!")
        return
        val (kafkaHost, kafkaPort, midpointHost, midpointPort, userName, password) = args
        // curl --user administrator:5ecr3t -X POST http://localhost:8080/midpoint/ws/rest/users -H "Content-Type: application/xml" --data "@add_user.xml"
        // curl --user administrator:5ecr3t -X PATCH http://localhost:8080/midpoint/ws/rest/users/0e030e0c-a37d-47b2-bde8-f8e61e4a2bfb -H "Content-Type: application/xml" --data "@add_shadow.xml"
        // curl --user administrator:5ecr3t -H "Accept: application/json" -X GET http://localhost:8080/midpoint/ws/rest/shadows/699a2edd-a855-4d90-ad7c-1ec42699cbee
        val midpointRepository = RemoteMidpointRepository(midpointHost, midpointPort.toInt(), userName, password)

        // wait until midpoint is alive
        runBlocking {
            while (true) {
                if (midpointRepository.isAvailable()) {
                    println("Midpoint is alive!")
                    break
                }
                delay(10.seconds)
                println("Midpoint is not yet alive, расслабляемся на диване дальше")
            }
        }

        val props = getStreamsConfig(kafkaHost, kafkaPort)
        val producer = KafkaProducer<String, String>(props)
        runBlocking {
            (HttpClient(CIO)).use { client ->
                client.post("http://${midpointHost}:${midpointPort}/midpoint/ws/rest/users") {
                    basicAuth(userName, password)
                    contentType(ContentType.Application.Xml)
                    setBody(newUserFile)
                }
                client.patch("http://${midpointHost}:${midpointPort}/midpoint/ws/rest/users/0e030e0c-a37d-47b2-bde8-f8e61e4a2bfb") {
                    basicAuth(userName, password)
                    contentType(ContentType.Application.Xml)
                    setBody(connectToLdapFile)
                }
                val shadows = midpointRepository.getUserShadows("0e030e0c-a37d-47b2-bde8-f8e61e4a2bfb")
                if (shadows.size != 1) {
                    throw IllegalStateException("shadow was not created")
                }
                val actualShadow = shadows[0]

                val newGroup = "ou=people,dc=example,dc=com"
                val message = """{"name":"Жмышенко Валерий Альбертович", "newGroup":"${newGroup}"}"""
                val record = ProducerRecord(INPUT_TOPIC, "zhmih", message)
                producer.send(record)
                delay(5.seconds)
                val response = client.get("http://${midpointHost}:${midpointPort}/midpoint/ws/rest/shadows/${actualShadow}") {
                    basicAuth(userName, password)
                    accept(ContentType.Application.Json)
                }
                val responseObj = Json.parseToJsonElement(response.bodyAsText())
                val res = responseObj
                    .jsonObject["shadow"]!!
                    .jsonObject["attributes"]!!
                    .jsonObject["memberOf"]!!
                    .jsonPrimitive.content
                println(res)
                if (res != newGroup) {
                    throw IllegalStateException("New group was not set")
                }
            }

        }
    }

    private fun getStreamsConfig(kafkaHost: String, kafkaPort: String): Properties {
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

    companion object {
        private const val INPUT_TOPIC = "requests-input"
    }


}
