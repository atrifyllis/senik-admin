package gr.senik.admin

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import gr.alx.common.domain.model.DomainEvent
import jakarta.validation.ValidationException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.PropertyMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.KafkaListenerConfigurer
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.*
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.ProducerListener
import org.springframework.kafka.support.converter.RecordMessageConverter
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.util.backoff.BackOff
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

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

    /**
     * Used by our custom error handler bellow.
     */
    @Bean
    fun commonLoggingErrorHandler(): CommonLoggingErrorHandler {
        return CommonLoggingErrorHandler()
    }

    /**
     * Custom error handler which:
     * 1) retries with exponential backoff
     * 2) logs exception
     * 3) publishes message in dead letter topic.
     */
    @Bean
    fun defaultErrorHandler(
        kafkaTemplate: KafkaTemplate<String, Any>,
        commonLoggingErrorHandler: CommonLoggingErrorHandler
    ): DefaultErrorHandler {
        // Publish to dead letter topic any messages dropped after retries with back off
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate)
        // Spread out attempts over time, taking a little longer between each attempt
        // Set a max for retries below max.poll.interval.ms; default: 5m, as otherwise we trigger a consumer rebalance
        val exponentialBackOff = ExponentialBackOffWithMaxRetries(3)
        exponentialBackOff.initialInterval = 500L
        exponentialBackOff.multiplier = 1.5
        exponentialBackOff.maxInterval = 2000
        val errorHandler = DefaultLoggingErrorHandler(recoverer, exponentialBackOff, commonLoggingErrorHandler)
        // Do not try to recover from validation exceptions when validation has failed
        errorHandler.addNotRetryableExceptions(ValidationException::class.java)
        return errorHandler
    }

    /**
     * Creates custom JsonDeserializer that uses Spring Boot's objectMapper and TypeReference which results in usage of
     * the {@see DomainEventMixIn}. Moreover, it is wrapped by ErrorHandlingDeserializer, it was the only way to
     * leverage the DefaultErrorHandler configured above.
     *
     * TODO Could not find a way to use spring boot objectMapper, which relies on the DomainMixin to ser/deserialize event.
     */
    @Bean
    fun kafkaConsumerFactory(
        customizers: ObjectProvider<DefaultKafkaConsumerFactoryCustomizer>,
        properties: KafkaProperties,
        objectMapper: ObjectMapper
    ): DefaultKafkaConsumerFactory<*, *> {
        val factory = DefaultKafkaConsumerFactory<String, DomainEvent>(properties.buildConsumerProperties())
        customizers.orderedStream()
            .forEach { customizer: DefaultKafkaConsumerFactoryCustomizer -> customizer.customize(factory) }
        factory.setValueDeserializer(ErrorHandlingDeserializer(JsonDeserializer(object :
            TypeReference<DomainEvent>() {}, objectMapper)))
        return factory
    }

    /**
     * Copied from {@see KafkaAutoConfiguration} just to enable observation.
     */
    @Bean
    @ConditionalOnMissingBean(KafkaTemplate::class)
    fun kafkaTemplate(
        kafkaProducerFactory: ProducerFactory<Any, Any>,
        kafkaProducerListener: ProducerListener<Any, Any>,
        messageConverter: ObjectProvider<RecordMessageConverter?>,
        properties: KafkaProperties
    ): KafkaTemplate<*, *> {
        val map = PropertyMapper.get().alwaysApplyingWhenNonNull()
        val kafkaTemplate = KafkaTemplate(kafkaProducerFactory)
        messageConverter.ifUnique { kafkaTemplate.setMessageConverter(it!!) }
        map.from(kafkaProducerListener).to(kafkaTemplate::setProducerListener)
        map.from<String>(properties.template.defaultTopic).to(kafkaTemplate::setDefaultTopic)
        map.from<String>(properties.template.transactionIdPrefix).to(kafkaTemplate::setTransactionIdPrefix)
        kafkaTemplate.setObservationEnabled(true) // TODO any easier way to customise auto-configured kafkaTemplate?
        return kafkaTemplate
    }

    /**
     * Copied from {@see KafkaAutoConfiguration} to enable observation AND use our custom error handler.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["kafkaListenerContainerFactory"])
    fun kafkaListenerContainerFactory(
        configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
        kafkaConsumerFactory: ObjectProvider<ConsumerFactory<Any?, Any?>>,
        properties: KafkaProperties, errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val factory = ConcurrentKafkaListenerContainerFactory<Any, Any>()
        configurer.configure(factory, kafkaConsumerFactory
            .getIfAvailable { DefaultKafkaConsumerFactory<Any?, Any?>(properties.buildConsumerProperties()) })
        // TODO any easier way to customise auto-configured kafkaListenerContainerFactory?
        factory.containerProperties.isObservationEnabled = true
        factory.setCommonErrorHandler(errorHandler) // otherwise our custom error handler is not used!
        return factory
    }

    /**
     * Extends DefaultErrorHandler to also log exception (otherwise message is sent to dead letter topic and exception
     * is never logged).
     */
    class DefaultLoggingErrorHandler(
        recoverer: ConsumerRecordRecoverer,
        backOff: BackOff,
        private val commonLoggingErrorHandler: CommonLoggingErrorHandler
    ) : DefaultErrorHandler(recoverer, backOff) {
        override fun handleOne(
            thrownException: Exception,
            record: ConsumerRecord<*, *>,
            consumer: Consumer<*, *>,
            container: MessageListenerContainer
        ): Boolean {

            commonLoggingErrorHandler.handleOne(thrownException, record, consumer, container)

            return super.handleOne(thrownException, record, consumer, container)
        }
    }
}
