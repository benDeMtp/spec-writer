package dev.specwriter.support;

import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.port.AttachmentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InMemoryAttachments implements AttachmentRepository {

    private final List<Attachment> all = new ArrayList<>();

    @Override
    public List<Attachment> list(UUID sessionId) {
        return all.stream().filter(a -> a.sessionId().equals(sessionId))
                .sorted(Comparator.comparingInt(Attachment::ordinal)).toList();
    }

    @Override
    public List<Attachment.View> views(UUID sessionId) {
        return list(sessionId).stream().map(Attachment::view).toList();
    }

    @Override
    public void add(Attachment attachment) {
        all.add(attachment);
    }

    @Override
    public boolean remove(UUID sessionId, UUID attachmentId) {
        return all.removeIf(a -> a.sessionId().equals(sessionId) && a.id().equals(attachmentId));
    }
}
