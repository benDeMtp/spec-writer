package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;

/** Entrée de {@code editSpecification} : l'instruction est une donnée, jamais une consigne système. */
public record EditRequest(DraftSpecification current, String instruction, Language language) {
}
