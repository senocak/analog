package com.github.senocak.analog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class AnalogApplication

fun main(args: Array<String>) {
    println(
        """
 █████╗ ╗███╗   ██╗ █████╗ ██╗      ██████╗  ██████╗
██╔══██╗║████╗  ██║██╔══██╗██║     ██╔═══██╗██╔════╝
███████║║██╔██╗ ██║███████║██║     ██║   ██║██║  ███╗
██║  ██║╝██║ ╚████║██║  ██║███████╗╚██████╔╝╚██████╔╝
╚═╝  ╚═╝ ╚═╝  ╚═══╝╚═╝  ╚═╝╚══════╝ ╚═════╝  ╚═════╝
        """.trimIndent(),
    )
    runApplication<AnalogApplication>(*args)
}
