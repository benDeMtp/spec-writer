package fr.kawamind.domain.sync;

/** Éléments nouveaux, modifiés et retirés d'une source par une synchronisation (017, FR-008). */
public record SourceChange(int added, int modified, int removed) {

    public static final SourceChange NONE = new SourceChange(0, 0, 0);

    public SourceChange {
        if (added < 0 || modified < 0 || removed < 0) {
            throw new IllegalArgumentException("nombre négatif");
        }
    }

    public boolean changed() {
        return added + modified + removed > 0;
    }
}
