package dev.specwriter.infrastructure.persistence.project;

import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "project_doc_source")
public class DocSourceEntity {
    @Id
    public String id;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Column(nullable = false)
    public String kind;

    @Column(nullable = false)
    public String location;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "last_read_at")
    public Instant lastReadAt;

    /** JSON {@code {pagesRead, pagesInError, limitReached}}, sans contenu. */
    @Column(name = "last_result")
    public String lastResult;
}
