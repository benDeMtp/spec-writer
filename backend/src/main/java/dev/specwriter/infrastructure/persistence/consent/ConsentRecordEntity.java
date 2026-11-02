package dev.specwriter.infrastructure.persistence.consent;

import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "consent_record")
public class ConsentRecordEntity {
    @Id
    public String id;

    @Column(nullable = false)
    public String service;

    @Column(nullable = false)
    public boolean granted;

    @Convert(converter = InstantTextConverter.class)
    @Column(nullable = false)
    public Instant at;
}
