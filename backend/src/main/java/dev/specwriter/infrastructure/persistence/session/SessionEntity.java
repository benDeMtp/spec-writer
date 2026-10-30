package dev.specwriter.infrastructure.persistence.session;

import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import dev.specwriter.infrastructure.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "session")
public class SessionEntity {
    @Id
    public String id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String need;

    @Column(nullable = false)
    public String language;

    @Column(nullable = false)
    public String origin;

    @Column(nullable = false)
    public String state;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "covered_themes", nullable = false)
    public List<String> coveredThemes;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
