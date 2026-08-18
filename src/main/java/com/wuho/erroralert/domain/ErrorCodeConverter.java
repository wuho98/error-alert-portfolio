package com.wuho.erroralert.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ErrorCodeConverter implements AttributeConverter<ErrorCode, String> {

    @Override
    public String convertToDatabaseColumn(ErrorCode attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public ErrorCode convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return ErrorCode.fromCode(dbData);
    }
}
