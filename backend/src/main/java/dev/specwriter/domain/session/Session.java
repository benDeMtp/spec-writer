package dev.specwriter.domain.session;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un travail de spécification (data-model.md, Session). Immuable : chaque changement crée une copie.
 * Il appartient à exactement un projet (feature 002, FR-003).
 */
public record Session(
        UUID id,
        UUID projectId,
        String name,
        Need need,
        Language language,
        SessionOrigin origin,
        SessionState state,
        Instant createdAt,
        Instant updatedAt) {

    public static final int MAX_NAME = 120;

    public Session {
        Objects.requireNonNull(projectId);
        name = validName(name);
    }

    public static Session start(UUID id, UUID projectId, Need need, String name, Instant now) {
        String chosen = name == null || name.isBlank() ? need.defaultName() : name;
        return new Session(id, projectId, chosen, need, LanguageDetector.detect(need.text()), SessionOrigin.LOCAL,
                SessionState.QUESTIONING, now, now);
    }

    /** Session créée depuis un ticket Jira (FR-046) : sans questionnaire, sa première version vient de l'import. */
    public static Session imported(UUID id, UUID projectId, Need need, String name, Instant now) {
        String chosen = name == null || name.isBlank() ? need.defaultName() : name;
        return new Session(id, projectId, chosen.length() > MAX_NAME ? chosen.substring(0, MAX_NAME) : chosen, need,
                LanguageDetector.detect(need.text()), SessionOrigin.JIRA_IMPORT, SessionState.GENERATED, now, now);
    }

    public Session rename(String newName, Instant now) {
        return new Session(id, projectId, newName, need, language, origin, state, createdAt, now);
    }

    /** Déplacée vers un autre projet, avec ses versions (FR-004 de 002). */
    public Session movedTo(UUID newProjectId, Instant now) {
        return new Session(id, newProjectId, name, need, language, origin, state, createdAt, now);
    }

    public Session withLanguage(Language newLanguage, Instant now) {
        if (state == SessionState.GENERATED) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "La langue ne peut plus changer une fois la spécification générée.");
        }
        return new Session(id, projectId, name, need, newLanguage, origin, state, createdAt, now);
    }

    public Session readyToGenerate(Instant now) {
        return withState(SessionState.READY_TO_GENERATE, now);
    }

    public Session generated(Instant now) {
        return withState(SessionState.GENERATED, now);
    }

    /** Une réponse ajoutée ou modifiée : la session est sauvegardée et repasse en questionnaire. */
    public Session answerChanged(Instant now) {
        return withState(SessionState.QUESTIONING, now);
    }

    private Session withState(SessionState next, Instant now) {
        return new Session(id, projectId, name, need, language, origin, next, createdAt, now);
    }

    private static String validName(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.length() > MAX_NAME) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le nom doit faire entre 1 et 120 caractères.");
        }
        return value;
    }
}
