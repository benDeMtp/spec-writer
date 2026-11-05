package dev.specwriter.infrastructure.persistence.specification;

import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "specification_version")
public class SpecificationVersionEntity {
    @Id
    public String id;

    @Column(name = "session_id", nullable = false)
    public String sessionId;

    @Column(nullable = false)
    public int number;

    @Column(nullable = false)
    public String title;

    @Column(name = "input_fingerprint", nullable = false)
    public String inputFingerprint;

    @Column(nullable = false)
    public String origin;

    @Column(nullable = false)
    public String content;

    @Column(nullable = false)
    public String markdown;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "context_hash")
    public String contextHash;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "context_updated_at")
    public Instant contextUpdatedAt;

    /** Sélection de connaissance (JSON, références et empreintes, sans contenu), {@code null} sans connaissance. */
    @Column(name = "knowledge")
    public String knowledge;
}
