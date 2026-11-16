package fr.kawamind.infrastructure.persistence.attachment;

import fr.kawamind.domain.port.AttachmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.transaction.Transactional;

/** Documents joints dans la table {@code session_attachment} (migration V17, research.md R4). */
@ApplicationScoped
@Transactional
@Typed(AttachmentRepository.class)
public class SqliteAttachmentRepository extends SqlAttachments {

    public SqliteAttachmentRepository() {
        super("session_attachment", "session_id");
    }
}
