package com.back.domain.match.matchRequest.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class SituationConverter : AttributeConverter<Situation, String> {
    override fun convertToDatabaseColumn(attribute: Situation?): String? = attribute?.label

    override fun convertToEntityAttribute(dbData: String?): Situation? = dbData?.let { Situation.fromLabel(it) }
}
