package gr.senik.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SenikAdminApplication

fun main(args: Array<String>) {
    runApplication<SenikAdminApplication>(*args)
}
