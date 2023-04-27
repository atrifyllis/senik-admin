package gr.senik.admin.adapters.primary.messaging

import gr.senik.admin.domain.model.IncomeCalculated
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * Primary adapter that listens to kafka topic for [CalculationCommand]s.
 *
 * Note that validation works exactly as in REST controller adapters!
 */
@Component
class IncomeCalculationListener {

    @KafkaListener(topics = ["senik.events"], groupId = "senik-admin-income-calculated-consumer-group")
    fun calculateIncome(@Payload event: IncomeCalculated) {
        log.info { event }
    }
}
