package dev.specwriter.domain.port;

import dev.specwriter.domain.attachment.Attachment;
import java.util.List;
import java.util.UUID;

/** Documents joints aux sessions (009) ; supprimés avec leur session. */
public interface AttachmentRepository {

    /** Documents d'une session, texte compris, dans l'ordre d'ajout (pour la connaissance transmise). */
    List<Attachment> list(UUID sessionId);

    /** Vues sans texte, dans l'ordre d'ajout. */
    List<Attachment.View> views(UUID sessionId);

    void add(Attachment attachment);

    /** Vrai si le document existait. */
    boolean remove(UUID sessionId, UUID attachmentId);
}
