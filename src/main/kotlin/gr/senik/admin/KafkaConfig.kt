package gr.senik.admin

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import gr.alx.common.domain.model.DomainEvent
import jakarta.validation.ValidationException
import mu.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.KafkaListenerConfigurer
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean


const val CALCULATION_COMMANDS = "calculation.commands"
const val CALCULATION_COMMANDS_DLT = "calculation.commands.DLT"

private val log = KotlinLogging.logger {}


@Configuration
@EnableKafka
class KafkaConfig(
        @Autowired
        private var validator: LocalValidatorFactoryBean,
) : KafkaListenerConfigurer {

    /**
     * This is not auto-configured for some reason, so no validation is performed without it.
     */
    override fun configureKafkaListeners(registrar: KafkaListenerEndpointRegistrar) {
        registrar.setValidator(this.validator)
    }

    @Bean
    fun defaultErrorHandler(kafkaTemplate: KafkaTemplate<String, Any>): DefaultErrorHandler {
        // Publish to dead letter topic any messages dropped after retries with back off
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate)
        // Spread out attempts over time, taking a little longer between each attempt
        // Set a max for retries below max.poll.interval.ms; default: 5m, as otherwise we trigger a consumer rebalance
        val exponentialBackOff = ExponentialBackOffWithMaxRetries(3)
        exponentialBackOff.initialInterval = 500L
        exponentialBackOff.multiplier = 1.5
        exponentialBackOff.maxInterval = 2000
        val errorHandler = DefaultErrorHandler(recoverer, exponentialBackOff)
        // Do not try to recover from validation exceptions when validation has failed
        errorHandler.addNotRetryableExceptions(ValidationException::class.java)
        return errorHandler
    }


    /**
     * Creates custom JsonDeserializer that uses Spring Boot's objectMapper and TypeReference which results in usage of
     * the {@see DomainEventMixIn}. Moreover, it is wrapped by ErrorHandlingDeserializer, it was the only way to
     * leverage the DefaultErrorHandler configured above.
     *
     *TODO Could not find a way to use spring boot objectMapper, which relies on the DomainMixin to ser/deserialize event.
     */

    @Bean
    fun kafkaConsumerFactory(customizers: ObjectProvider<DefaultKafkaConsumerFactoryCustomizer>, properties: KafkaProperties, objectMapper: ObjectMapper): DefaultKafkaConsumerFactory<*, *> {
        val factory = DefaultKafkaConsumerFactory<String, DomainEvent>(properties.buildConsumerProperties())
        customizers.orderedStream().forEach { customizer: DefaultKafkaConsumerFactoryCustomizer -> customizer.customize(factory) }
        factory.setValueDeserializer(ErrorHandlingDeserializer(JsonDeserializer(object : TypeReference<DomainEvent>() {}, objectMapper)))
        return factory
    }


}
