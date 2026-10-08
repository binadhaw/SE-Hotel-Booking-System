package com.Reservation.Hotel.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Put {@code @Convert(converter = EncryptedStringConverter.class)} on an entity field and it is stored
 * encrypted (AES-256-GCM) and read back as plain text - the rest of the code does not notice.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : FieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldEncryptor.decrypt(dbData);
    }
}
