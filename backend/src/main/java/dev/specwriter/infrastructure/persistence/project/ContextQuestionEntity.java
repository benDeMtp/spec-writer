package dev.specwriter.infrastructure.persistence.project;

import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "project_context_question")
public class ContextQuestionEntity {
    @Id
    public String id;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Column(nullable = false)
    public int number;

    @Column(nullable = false)
    public String sheet;

    @Column(nullable = false)
    public String label;

    @Column(nullable = false)
    public String rationale;

    @Column(nullable = false)
    public String answer;

    @Column(nullable = false)
    public String state;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "asked_at", nullable = false)
    public Instant askedAt;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "answered_at", nullable = false)
    public Instant answeredAt;
}
