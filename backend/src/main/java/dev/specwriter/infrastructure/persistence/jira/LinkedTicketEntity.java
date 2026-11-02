package dev.specwriter.infrastructure.persistence.jira;

import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "linked_jira_ticket")
public class LinkedTicketEntity {
    @Id
    @Column(name = "session_id")
    public String sessionId;

    @Column(name = "issue_key", nullable = false)
    public String issueKey;

    @Column(name = "issue_url", nullable = false)
    public String issueUrl;

    @Column(name = "issue_type")
    public String issueType;

    @Column(name = "linked_version_number", nullable = false)
    public int linkedVersionNumber;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "last_known_updated")
    public Instant lastKnownUpdated;

    @Column(name = "last_known_description_hash")
    public String lastKnownDescriptionHash;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "created_or_imported_at", nullable = false)
    public Instant createdOrImportedAt;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "last_pushed_at")
    public Instant lastPushedAt;

    @Column(name = "sync_state", nullable = false)
    public String syncState;
}
