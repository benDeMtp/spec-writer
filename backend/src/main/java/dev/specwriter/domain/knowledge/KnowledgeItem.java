package dev.specwriter.domain.knowledge;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Élément de l'index de connaissance d'un projet : ticket, section de documentation, ADR ou élément
 * d'architecture (data-model.md, KnowledgeItem). Son empreinte décide de la revectorisation.
 */
public record KnowledgeItem(
        UUID projectId,
        KnowledgeKind kind,
        String ref,
        int chunk,
        String title,
        String body,
        String status,
        String statusCategory,
        String supersededBy,
        String issueType,
        String elementType,
        ArchScope scope,
        boolean transport,
        KnowledgeRole role,
        String contentHash,
        Instant sourceUpdatedAt,
        Instant indexedAt) {

    public static final int MAX_TITLE = 500;
    public static final int MAX_BODY = 8_000;
    public static final String DONE = "done";

    public KnowledgeItem {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(kind);
        if (ref == null || ref.isBlank()) {
            throw invalid("La référence d'un élément de connaissance est obligatoire.");
        }
        if (chunk < 0) {
            throw invalid("Le numéro de morceau est négatif.");
        }
        if (title == null || title.isBlank()) {
            throw invalid("Le titre d'un élément de connaissance est obligatoire.");
        }
        if (title.length() > MAX_TITLE) {
            throw invalid("Le titre d'un élément de connaissance dépasse 500 caractères.");
        }
        body = body == null ? "" : body;
        if (body.length() > MAX_BODY) {
            throw invalid("Le corps d'un élément de connaissance dépasse 8 000 caractères.");
        }
        // l'empreinte est toujours celle du titre et du corps : une empreinte lue périmée est recalculée
        String computed = hash(title, body);
        contentHash = computed.equals(contentHash) ? contentHash : computed;
    }

    public static KnowledgeItem ticket(UUID projectId, String key, String title, String body, String status,
            String statusCategory, String issueType, Instant updated, Instant now) {
        return new KnowledgeItem(projectId, KnowledgeKind.TICKET, key, 0, title, body, status, statusCategory, null,
                issueType, null, null, false, null, null, updated, now);
    }

    public static KnowledgeItem docSection(UUID projectId, String ref, int chunk, String title, String body,
            KnowledgeRole role, Instant updated, Instant now) {
        return new KnowledgeItem(projectId, KnowledgeKind.DOC_SECTION, ref, chunk, title, body, null, null, null, null,
                null, null, false, role, null, updated, now);
    }

    public static KnowledgeItem adr(UUID projectId, String ref, int chunk, String title, String body,
            AdrStatus.Statement statement, Instant updated, Instant now) {
        return new KnowledgeItem(projectId, KnowledgeKind.ADR, ref, chunk, title, body, statement.status().name(),
                null, statement.supersededBy(), null, null, null, false, null, null, updated, now);
    }

    public static KnowledgeItem archElement(UUID projectId, ArchElement element, Instant updated, Instant now) {
        StringBuilder body = new StringBuilder(element.name());
        if (element.description() != null && !element.description().isBlank()) {
            body.append('\n').append(element.description().strip());
        }
        if (element.technology() != null && !element.technology().isBlank()) {
            body.append("\nTechnologie : ").append(element.technology().strip());
        }
        String text = body.length() > MAX_BODY ? body.substring(0, MAX_BODY) : body.toString();
        String title = element.shortName().length() > MAX_TITLE ? element.shortName().substring(0, MAX_TITLE)
                : element.shortName();
        return new KnowledgeItem(projectId, KnowledgeKind.ARCH_ELEMENT, element.name(), 0, title, text, null, null,
                null, null, element.type().name(), element.scope(), element.transport(), null, null, updated, now);
    }

    /** Ticket fermé : catégorie de statut Jira {@code done} (FR-035). */
    public boolean closed() {
        return kind == KnowledgeKind.TICKET && DONE.equals(statusCategory);
    }

    public AdrStatus adrStatus() {
        if (kind != KnowledgeKind.ADR || status == null) {
            return null;
        }
        try {
            return AdrStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return AdrStatus.UNKNOWN;
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message);
    }

    /** Empreinte d'un titre et d'un texte, comme celle d'un élément, sans limite de taille (008). */
    public static String hash(String title, String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(title.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
