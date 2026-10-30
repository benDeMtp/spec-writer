package dev.specwriter.infrastructure.persistence.session;

import dev.specwriter.infrastructure.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

@Entity
@Table(name = "question")
public class QuestionEntity {
    @Id
    public String id;

    @Column(name = "session_id", nullable = false)
    public String sessionId;

    @Column(nullable = false)
    public int ordinal;

    @Column(nullable = false)
    public String theme;

    @Column(nullable = false)
    public int batch;

    @Column(nullable = false)
    public String label;

    @Column(nullable = false)
    public String rationale;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false)
    public List<String> suggestions;

    @Column(name = "default_value")
    public String defaultValue;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "depends_on", nullable = false)
    public List<String> dependsOn;

    @Column(nullable = false)
    public String state;
}
