package dev.specwriter.domain.specification;

import java.util.List;

/** Use case d'une version (data-model.md, UseCase). Le code {@code UC-nn} est attribué par l'outil. */
public record UseCase(
        String code,
        String title,
        String actor,
        String goal,
        List<String> preconditions,
        List<String> nominalFlow,
        List<String> alternatives,
        SourceRef source) {

    public UseCase {
        preconditions = List.copyOf(preconditions);
        nominalFlow = List.copyOf(nominalFlow);
        alternatives = List.copyOf(alternatives);
    }
}
