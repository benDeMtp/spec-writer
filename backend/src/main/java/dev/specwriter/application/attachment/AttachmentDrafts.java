package dev.specwriter.application.attachment;

import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.attachment.AttachmentDraft;
import dev.specwriter.domain.attachment.Attachments;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.AttachmentReader;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.port.IdGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Documents joints sur l'écran du besoin, avant que la session existe (009, research.md R3) : lus
 * aussitôt, gardés en mémoire 60 minutes, rattachés à la session au démarrage. Rien n'est écrit
 * tant que la session n'est pas démarrée ; un brouillon abandonné disparaît.
 */
public class AttachmentDrafts {

    static final Duration KEEP = Duration.ofMinutes(60);
    static final int MAX_DRAFTS = 200;

    private final AttachmentReader reader;
    private final ClockPort clock;
    private final IdGenerator ids;
    /** Dans l'ordre de création : le plus ancien part le premier. */
    private final Map<UUID, AttachmentDraft> drafts = new LinkedHashMap<>();

    public AttachmentDrafts(AttachmentReader reader, ClockPort clock, IdGenerator ids) {
        this.reader = reader;
        this.clock = clock;
        this.ids = ids;
    }

    /** Lit le fichier reçu ; refuse un format, un fichier illisible ou sans texte, avec la raison. */
    public AttachmentDraft create(String fileName, byte[] content) {
        String name = Attachments.fileName(fileName);
        Attachments.format(name);
        AttachmentReader.Reading reading = read(reader, name, content);
        AttachmentDraft draft = new AttachmentDraft(ids.next(), reading.title(), name, null, reading.text(), clock.now());
        synchronized (drafts) {
            purge();
            while (drafts.size() >= MAX_DRAFTS) {
                drafts.remove(drafts.keySet().iterator().next());
            }
            drafts.put(draft.id(), draft);
        }
        return draft;
    }

    /** Lecture d'un fichier reçu, refusée sans texte ; partagée avec l'ajout à une session en cours. */
    static AttachmentReader.Reading read(AttachmentReader reader, String fileName, byte[] content) {
        AttachmentReader.Reading reading = reader.read(fileName, content);
        if (!reading.ok()) {
            throw Attachments.unreadable(reading.failure());
        }
        if (reading.text() == null || reading.text().isBlank()) {
            throw Attachments.unreadable(UnreadReason.NO_TEXT);
        }
        return reading;
    }

    /** Brouillons encore présents parmi {@code wanted}, dans l'ordre demandé ; les autres sont ignorés. */
    public List<AttachmentDraft> find(List<UUID> wanted) {
        if (wanted == null) {
            return List.of();
        }
        synchronized (drafts) {
            purge();
            return wanted.stream().distinct().map(drafts::get).filter(Objects::nonNull).toList();
        }
    }

    /**
     * Brouillons à rattacher au démarrage, dans l'ordre demandé : tous présents, 5 au plus, sans
     * doublon ; sinon refus. Ils restent en mémoire jusqu'à {@link #forget} (démarrage réussi).
     */
    public List<AttachmentDraft> get(List<UUID> wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return List.of();
        }
        List<UUID> distinct = wanted.stream().distinct().toList();
        List<AttachmentDraft> found = find(distinct);
        if (found.size() != distinct.size()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Un document joint a expiré (60 minutes) : joignez-le à nouveau.");
        }
        List<Attachment> kept = new ArrayList<>();
        UUID session = new UUID(0, 0);
        for (AttachmentDraft d : found) {
            Attachments.requireRoom(kept, d.fileName(), d.contentHash());
            kept.add(d.attach(session, kept.size() + 1, d.createdAt()));
        }
        return found;
    }

    public void forget(List<UUID> wanted) {
        if (wanted == null) {
            return;
        }
        synchronized (drafts) {
            wanted.forEach(drafts::remove);
        }
    }

    private void purge() {
        drafts.values().removeIf(d -> d.createdAt().plus(KEEP).isBefore(clock.now()));
    }
}
