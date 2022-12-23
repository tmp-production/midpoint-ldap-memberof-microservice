package com.tmpproduction.ldapservice.apps

import com.tmpproduction.ldapservice.MemberOfService
import com.tmpproduction.ldapservice.impl.RemoteMidpointRepository
import com.tmpproduction.ldapservice.impl.RestApiProviderService
import io.ktor.client.*
import io.ktor.client.engine.cio.*

class RestApiMain {

    /**
     * args:
     *  - port on which to listen
     *  - midpoint host (ip)
     *  - midpoint port
     *  - midpoint username
     *  - midpoint password
     */
    fun main(args: Array<String>) {
        if (args.size != 5) {
            throw IllegalArgumentException("wrong amount of args")
        }
        val (port, midpointHost, midpointPort, userName, password) = args
        val service = MemberOfService(
            requestsProviderService = RestApiProviderService(port.toInt()),
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
