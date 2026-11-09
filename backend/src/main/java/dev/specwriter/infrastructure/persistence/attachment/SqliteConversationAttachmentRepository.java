package dev.specwriter.infrastructure.persistence.attachment;

import dev.specwriter.domain.port.ConversationAttachmentRepository;
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
