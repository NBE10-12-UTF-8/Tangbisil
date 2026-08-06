package com.back.domain.member.member.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class IndustryConverter : AttributeConverter<Industry, String> {
    override fun convertToDatabaseColumn(attribute: Industry?): String? = attribute?.label

    override fun convertToEntityAttribute(dbData: String?): Industry? = dbData?.let { Industry.fromLabel(it) }
}
