package dev.specwriter.infrastructure.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rend un schéma JSON conforme au mode strict exigé par Copilot (spike S1, research.md R4) :
 * {@code additionalProperties: false} et toutes les propriétés requises, à chaque niveau objet.
 */
final class StrictJsonSchema {

    private StrictJsonSchema() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> strict(Map<String, Object> schema) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : schema.entrySet()) {
            copy.put(e.getKey(), strictValue(e.getValue()));
        }
        if ("object".equals(copy.get("type")) || copy.containsKey("properties")) {
            copy.put("additionalProperties", false);
            Object props = copy.get("properties");
            if (props instanceof Map<?, ?> map) {
                copy.put("required", new ArrayList<>((java.util.Set<String>) map.keySet()));
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object strictValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return strict((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object o : list) {
                out.add(strictValue(o));
            }
            return out;
        }
        return value;
    }
}
