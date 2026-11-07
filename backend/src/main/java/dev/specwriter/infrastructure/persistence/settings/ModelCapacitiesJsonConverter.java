package dev.specwriter.infrastructure.persistence.settings;

import dev.specwriter.domain.settings.ModelCapacity;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Capacités des modèles Copilot (008) stockées en tableau JSON dans une colonne TEXT. */
@Converter
public class ModelCapacitiesJsonConverter implements AttributeConverter<List<ModelCapacity>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<ModelCapacity>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<ModelCapacity> attribute) {
        return JSON.writeValueAsString(attribute == null ? List.of() : attribute);
    }

    @Override
    public List<ModelCapacity> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? List.of() : JSON.readValue(dbData, TYPE);
    }
}
