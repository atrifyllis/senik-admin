package gr.senik.admin

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import gr.senik.admin.domain.model.IncomeCalculated

/**
 * All even types should be declared here, to be able to ser/deserialize them.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "className")
// for one subtype this is not required, but leaving for future use:
@JsonSubTypes(JsonSubTypes.Type(value = IncomeCalculated::class))
interface DomainEventMixIn {

}
