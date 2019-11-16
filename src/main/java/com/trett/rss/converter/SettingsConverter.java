package com.trett.rss.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trett.rss.models.Settings;

import javax.persistence.AttributeConverter;
import java.io.IOException;

public class SettingsConverter implements AttributeConverter<Settings, String> {

    @Override
    public String convertToDatabaseColumn(Settings attribute) {
        try {
            return new ObjectMapper().writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Parse error", e);
        }
    }

    @Override
    public Settings convertToEntityAttribute(String dbData) {
        try {
            return new ObjectMapper().readValue(dbData, Settings.class);
        } catch (IOException e) {
            throw new RuntimeException("Parse error", e);
        }
    }
}
