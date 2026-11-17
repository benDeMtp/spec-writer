package fr.kawamind.infrastructure.web;

import fr.kawamind.domain.error.ErrorAction;
import fr.kawamind.domain.error.ErrorCode;

/** Réponse d'erreur au format RFC 9457 (contracts/openapi.yaml, Problem). */
public record Problem(String type, String title, int status, String code, String userMessage, ErrorAction action) {

    public static final String MEDIA_TYPE = "application/problem+json";

    public static Problem of(ErrorCode code, String userMessage) {
        return new Problem("urn:spec-writer:error:" + code.name().toLowerCase(java.util.Locale.ROOT),
                code.name(), code.status(), code.name(), userMessage, code.action());
    }

    public static Problem of(ErrorCode code) {
        return of(code, code.defaultMessage());
    }
}
