package dev.specwriter.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Liste de chaînes stockée en tableau JSON dans une colonne TEXT. */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return JSON.writeValueAsString(attribute == null ? List.of() : attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? List.of() : JSON.readValue(dbData, TYPE);
    }
}
