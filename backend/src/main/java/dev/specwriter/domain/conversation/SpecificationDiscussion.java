package dev.specwriter.domain.conversation;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Discussion d'une spécification (011, data-model.md) : une conversation rattachée à une
 * spécification. Au plus une discussion suivie ({@code current}) par spécification ; quand la
 * spécification est supprimée et la discussion gardée, le rattachement disparaît ({@code sessionId}
 * nul) et le nom reste.
 */
public record SpecificationDiscussion(UUID conversationId, UUID sessionId, String specificationName,
        boolean current, Instant createdAt) {

    public static final int MAX_NAME = 200;

    public SpecificationDiscussion {
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(createdAt);
        specificationName = name(specificationName);
    }

    /** Nom gardé : sans espaces autour, 200 caractères au plus ; un nom vide est refusé. */
    public static String name(String specificationName) {
        String name = specificationName == null ? "" : specificationName.strip();
        if (name.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La spécification discutée n'a pas de nom.");
        }
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    /** Faux quand la spécification a été supprimée. */
    public boolean attached() {
        return sessionId != null;
    }

    /** Nouvelle discussion suivie de la spécification. */
    public static SpecificationDiscussion start(UUID conversationId, UUID sessionId, String specificationName,
            Instant now) {
        return new SpecificationDiscussion(conversationId, Objects.requireNonNull(sessionId), specificationName, true, now);
    }

    /** Remplacée par une nouvelle discussion : elle reste dans la liste des conversations. */
    public SpecificationDiscussion retired() {
        return new SpecificationDiscussion(conversationId, sessionId, specificationName, false, createdAt);
    }

    /** Spécification supprimée, discussion gardée : elle devient une conversation ordinaire. */
    public SpecificationDiscussion detached() {
        return new SpecificationDiscussion(conversationId, null, specificationName, false, createdAt);
    }
}
