package ru.nsu.fit.ldap.apps

import ru.nsu.fit.ldap.MemberOfService
import ru.nsu.fit.ldap.impl.KafkaProviderService
import ru.nsu.fit.ldap.impl.RemoteMidpointRepository

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
            midpointHost,
            midpointPort.toInt(),
            userName,
            password
        )
    )
    service.start()
}

private operator fun <T> Array<T>.component6() = this[5]
