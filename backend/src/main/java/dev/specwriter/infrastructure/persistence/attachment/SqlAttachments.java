package dev.specwriter.infrastructure.persistence.attachment;

import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.attachment.AttachmentFormat;
import dev.specwriter.domain.port.AttachmentRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Documents joints en SQL natif : table {@code session_attachment} (V17) ou
 * {@code conversation_attachment} (V18), de même forme ; seule la colonne du propriétaire change.
 */
abstract class SqlAttachments implements AttachmentRepository {

    @Inject
    EntityManager em;

    private final String selectAll;
    private final String selectViews;
    private final String insert;
    private final String delete;

    /** {@code table} et {@code owner} sont des constantes des sous-classes, jamais une saisie. */
    SqlAttachments(String table, String owner) {
        this.selectAll = "select id, ordinal, title, file_name, format, content_hash, text, added_at from " + table
                + " where " + owner + " = ?1 order by ordinal";
        this.selectViews = "select id, title, file_name, format, chars, added_at from " + table + " where " + owner
                + " = ?1 order by ordinal";
        this.insert = "insert into " + table + " (id, " + owner + ", ordinal, title, file_name, format, chars,"
                + " content_hash, text, added_at) values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)";
        this.delete = "delete from " + table + " where " + owner + " = ?1 and id = ?2";
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Attachment> list(UUID sessionId) {
        List<Object[]> rows = em.createNativeQuery(selectAll)
                .setParameter(1, sessionId.toString()).getResultList();
        return rows.stream().map(r -> new Attachment(UUID.fromString((String) r[0]), sessionId,
                ((Number) r[1]).intValue(), (String) r[2], (String) r[3], AttachmentFormat.valueOf((String) r[4]),
                (String) r[5], (String) r[6], Instant.parse((String) r[7]))).toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Attachment.View> views(UUID sessionId) {
        List<Object[]> rows = em.createNativeQuery(selectViews)
                .setParameter(1, sessionId.toString()).getResultList();
        return rows.stream().map(r -> new Attachment.View(UUID.fromString((String) r[0]), (String) r[1],
                (String) r[2], AttachmentFormat.valueOf((String) r[3]), ((Number) r[4]).intValue(),
                Instant.parse((String) r[5]))).toList();
    }

    @Override
    @Transactional
    public void add(Attachment a) {
        em.createNativeQuery(insert)
                .setParameter(1, a.id().toString()).setParameter(2, a.sessionId().toString())
                .setParameter(3, a.ordinal()).setParameter(4, a.title()).setParameter(5, a.fileName())
                .setParameter(6, a.format().name()).setParameter(7, a.chars()).setParameter(8, a.contentHash())
                .setParameter(9, a.text()).setParameter(10, a.addedAt().toString()).executeUpdate();
    }

    @Override
    @Transactional
    public boolean remove(UUID sessionId, UUID attachmentId) {
        return em.createNativeQuery(delete)
                .setParameter(1, sessionId.toString()).setParameter(2, attachmentId.toString()).executeUpdate() > 0;
    }
}
