package fr.kawamind.infrastructure.persistence.session;

import fr.kawamind.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "answer")
public class AnswerEntity {
    @Id
    @Column(name = "question_id")
    public String questionId;

    @Column(nullable = false)
    public String value;

    @Column(nullable = false)
    public boolean assumption;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "answered_at", nullable = false)
    public Instant answeredAt;
}
