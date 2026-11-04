package dev.specwriter.domain.knowledge;

import java.util.Objects;

/** Relation du modèle entre deux éléments, par leurs noms canoniques (data-model.md, ArchRelation). */
public record ArchRelation(String source, String destination, String description, String technology) {

    public ArchRelation {
        Objects.requireNonNull(source);
        Objects.requireNonNull(destination);
    }

    /** Vrai si la relation relie {@code a} et {@code b} (ou leurs descendants), dans un sens ou dans l'autre. */
    public boolean links(String a, String b) {
        return ArchElement.within(source, a) && ArchElement.within(destination, b)
                || ArchElement.within(source, b) && ArchElement.within(destination, a);
    }
}
