package dev.specwriter.spike;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entité jetable du spike S2 (research.md R7). */
@Entity
@Table(name = "spike_note")
public class SpikeNote {
    @Id
    public String id;
    public String title;
    public String body;
}
