package dev.specwriter.domain.conversation;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Conversation avec l'agent dans un projet (010, data-model.md) : elle ne voit que ce projet, et
 * disparaît avec lui. Le nom par défaut est le début du premier message.
 */
public record Conversation(UUID id, UUID projectId, String name, Instant createdAt, Instant updatedAt) {

    public static final int MAX_NAME = 120;
    static final int DEFAULT_NAME = 60;

    public Conversation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(projectId);
        name = name == null ? "" : name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le nom d'une conversation fait 1 à 120 caractères.");
        }
    }

    public static Conversation start(UUID id, UUID projectId, String firstMessage, Instant now) {
        return new Conversation(id, projectId, defaultName(firstMessage), now, now);
    }

    /** Début du premier message, sur une ligne, coupé à 60 caractères. */
    public static String defaultName(String firstMessage) {
        String line = firstMessage == null ? "" : firstMessage.strip().replaceAll("\\s+", " ");
        if (line.isEmpty()) {
            return "Nouvelle conversation";
        }
        return line.length() <= DEFAULT_NAME ? line : line.substring(0, DEFAULT_NAME - 1).stripTrailing() + "…";
    }

    public Conversation renamed(String newName) {
        return new Conversation(id, projectId, newName, createdAt, updatedAt);
    }

    public Conversation touched(Instant now) {
        return new Conversation(id, projectId, name, createdAt, now);
    }
}
