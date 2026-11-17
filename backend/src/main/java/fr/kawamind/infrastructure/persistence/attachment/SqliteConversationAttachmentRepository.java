package fr.kawamind.infrastructure.persistence.attachment;

import fr.kawamind.domain.port.ConversationAttachmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.transaction.Transactional;

/** Documents joints aux conversations, table {@code conversation_attachment} (V18). */
@ApplicationScoped
@Transactional
@Typed(ConversationAttachmentRepository.class)
public class SqliteConversationAttachmentRepository extends SqlAttachments implements ConversationAttachmentRepository {

    public SqliteConversationAttachmentRepository() {
        super("conversation_attachment", "conversation_id");
    }
}
