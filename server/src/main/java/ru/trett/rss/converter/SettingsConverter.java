package ru.trett.rss.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.trett.rss.models.Settings;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.io.IOException;

@Converter
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
