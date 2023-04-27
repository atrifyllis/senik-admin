package gr.senik.admin.domain.model

import gr.alx.common.domain.model.DomainEntityId
import gr.alx.common.domain.model.DomainEvent
import gr.alx.common.domain.model.Money
import java.util.*

data class IncomeCalculated(
    val incomeId: IncomeId,
    val individualId: LegalEntityId,
    val income: Money,
) : DomainEvent(aggregateId = incomeId, aggregateType = "income", type = "IncomeCalculated")

class IncomeId(id: UUID) : DomainEntityId(id)

class LegalEntityId(id: UUID) : DomainEntityId(id)
