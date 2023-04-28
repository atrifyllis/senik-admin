package gr.senik.admin

import gr.alx.common.domain.model.DomainEvent
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
class JacksonConfig {
    /**
     * Using MixIn only to avoid package cycles between common and netcalculator packages.
     */
    @Bean
    fun jsonCustomizer(): Jackson2ObjectMapperBuilderCustomizer? {
        return Jackson2ObjectMapperBuilderCustomizer { builder: Jackson2ObjectMapperBuilder ->
            builder.mixIn(DomainEvent::class.java, DomainEventMixIn::class.java)
        }
    }
}
