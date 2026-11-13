package dev.specwriter.infrastructure.persistence.project;

import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Racine de sources d'un projet (013, V24). */
@Entity
@Table(name = "project_source_root")
public class SourceRootEntity {
    @Id
    public String id;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Column(nullable = false)
    public String location;

    /** Tableau JSON des motifs d'exclusion, {@code null} sans motif. */
    public String exclusions;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
