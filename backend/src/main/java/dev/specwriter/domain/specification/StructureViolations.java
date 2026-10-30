package dev.specwriter.domain.specification;

import java.util.List;

/**
 * Écarts constatés sur une sortie du LLM. Les messages décrivent la structure, jamais le contenu
 * saisi : ils peuvent être renvoyés au LLM pour une nouvelle tentative (FR-012).
 */
public record StructureViolations(List<String> messages) {

    public StructureViolations {
        messages = List.copyOf(messages);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public StructureViolations and(StructureViolations other) {
        return new StructureViolations(java.util.stream.Stream.concat(messages.stream(), other.messages.stream()).toList());
    }
}
