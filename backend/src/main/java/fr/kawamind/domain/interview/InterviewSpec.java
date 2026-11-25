package fr.kawamind.domain.interview;

import java.util.Set;
import java.util.UUID;

/**
 * Une spécification d'une séance (021, data-model.md) : son rang, la version relue (fixée au
 * démarrage), les points écartés à la préparation, et la version créée par sa révision.
 */
public record InterviewSpec(UUID interviewId, UUID sessionId, int rank, Integer version, Set<String> excluded,
        Integer appliedVersion) {

    public InterviewSpec {
        excluded = excluded == null ? Set.of() : Set.copyOf(excluded);
    }

    public InterviewSpec withVersion(Integer relu) {
        return new InterviewSpec(interviewId, sessionId, rank, relu, excluded, appliedVersion);
    }

    public InterviewSpec withApplied(int number) {
        return new InterviewSpec(interviewId, sessionId, rank, version, excluded, number);
    }
}
