package dev.specwriter.domain.conversation;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tour de parole (010, data-model.md) : message de l'utilisateur (8 000 caractères au plus) ou
 * réponse de l'agent (100 000 au plus). Le texte n'apparaît jamais dans {@link #toString()}.
 */
public record ConversationMessage(UUID id, UUID conversationId, int ordinal, MessageRole role, String text,
        MessageState state, boolean historyTruncated, Instant createdAt, Integer specificationVersion) {

    public static final int MAX_USER = 8_000;
    public static final int MAX_AGENT = 100_000;

    public ConversationMessage {
        Objects.requireNonNull(id);
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(state);
        text = text == null ? "" : text;
    }

    public static ConversationMessage user(UUID id, UUID conversationId, int ordinal, String text, Instant now) {
        String kept = text == null ? "" : text.strip();
        if (kept.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Écrivez un message.");
        }
        if (kept.length() > MAX_USER) {
            throw new DomainException(ErrorCode.MESSAGE_TOO_LONG);
        }
        return new ConversationMessage(id, conversationId, ordinal, MessageRole.USER, kept, MessageState.COMPLETE, false,
                now, null);
    }

    public static ConversationMessage agentStarted(UUID id, UUID conversationId, int ordinal, Instant now) {
        return new ConversationMessage(id, conversationId, ordinal, MessageRole.AGENT, "", MessageState.RUNNING, false,
                now, null);
    }

    /** Texte de la réponse, coupé à {@link #MAX_AGENT}. */
    public ConversationMessage withText(String newText) {
        String kept = newText == null ? "" : newText;
        return new ConversationMessage(id, conversationId, ordinal, role,
                kept.length() > MAX_AGENT ? kept.substring(0, MAX_AGENT) : kept, state, historyTruncated, createdAt,
                specificationVersion);
    }

    public ConversationMessage finished(MessageState newState, boolean truncated) {
        return new ConversationMessage(id, conversationId, ordinal, role, text, newState, truncated, createdAt,
                specificationVersion);
    }

    /** Version de la spécification discutée transmise pour ce tour (011) ; nulle hors discussion. */
    public ConversationMessage about(Integer version) {
        return new ConversationMessage(id, conversationId, ordinal, role, text, state, historyTruncated, createdAt,
                version);
    }

    @Override
    public String toString() {
        return "ConversationMessage[" + id + ", " + role + ", " + state + ", " + text.length() + " caractères]";
    }
}
