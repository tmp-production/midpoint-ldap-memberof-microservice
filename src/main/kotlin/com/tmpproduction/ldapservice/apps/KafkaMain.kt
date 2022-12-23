package com.tmpproduction.ldapservice.apps

import com.tmpproduction.ldapservice.MemberOfService
import com.tmpproduction.ldapservice.impl.KafkaProviderService
import com.tmpproduction.ldapservice.impl.RemoteMidpointRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*

class KafkaMain {

    /**
     * args:
     *  - kafka host (ip)
     *  - kafka port
     *  - midpoint host (ip)
     *  - midpoint port
     *  - midpoint username
     *  - midpoint password
     */
    fun main(args: Array<String>) {
        if (args.size != 6) {
            throw IllegalArgumentException("wrong amount of args")
        }
        val (kafkaHost, kafkaPort, midpointHost, midpointPort, userName, password) = args
        val service = MemberOfService(
            requestsProviderService = KafkaProviderService(kafkaHost, kafkaPort.toInt()),
            midpointStateProvider = RemoteMidpointRepository(
                RemoteMidpointRepository.Config(
                    midpointHost,
                    midpointPort.toInt(),
                    userName,
                    password
                ),
                HttpClient(CIO)
            )
        )
        service.start()
    }

}

internal operator fun <T> Array<T>.component6() = this[5]
