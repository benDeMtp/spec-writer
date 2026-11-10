package dev.specwriter.domain.port;

/**
 * Documents joints aux conversations (010, US3) : mêmes règles que ceux des sessions (009), le
 * propriétaire ({@code Attachment.sessionId}) est la conversation ; supprimés avec elle.
 */
public interface ConversationAttachmentRepository extends AttachmentRepository {
}
