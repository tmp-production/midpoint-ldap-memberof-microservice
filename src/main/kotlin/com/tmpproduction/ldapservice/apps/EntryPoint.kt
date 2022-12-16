package com.tmpproduction.ldapservice.apps

fun main(args: Array<String>) {
    val otherArgs = args.sliceArray((1 until args.size))
    when (args[0]) {
        "integration-test" -> {
            IntegrationTestMain().main(otherArgs)
        }
        "kafka" -> {
            KafkaMain().main(otherArgs)
        }
        "rest" -> {
            RestApiMain().main(otherArgs)
        }
        else -> {
            throw IllegalArgumentException("Bad run option")
        }
    }
}
