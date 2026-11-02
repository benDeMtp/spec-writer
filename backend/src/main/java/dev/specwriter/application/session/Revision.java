package dev.specwriter.application.session;

import java.util.List;

/** Effet d'une réponse modifiée : questions retirées et ajoutées, par libellé (US3-2). */
public record Revision(List<String> removed, List<String> added) {

    public static final Revision NONE = new Revision(List.of(), List.of());

    public Revision {
        removed = List.copyOf(removed);
        added = List.copyOf(added);
    }
}
