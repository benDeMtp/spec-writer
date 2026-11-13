package dev.specwriter.domain.knowledge;

import java.util.Objects;

/**
 * Élément du modèle Structurizr, désigné par son nom canonique {@code Système/Conteneur/Composant}
 * (data-model.md, KnowledgeItem). {@code transport} : topic, file ou bus (research.md R22).
 * {@code moduleHint} : nom ou chemin du module de code correspondant, lu dans la propriété
 * {@code spec-writer.module} (013, research.md R8) ; nul si absent.
 */
public record ArchElement(String name, Type type, ArchScope scope, boolean transport, String description,
        String technology, String moduleHint) {

    public enum Type {
        PERSON,
        SOFTWARE_SYSTEM,
        CONTAINER,
        COMPONENT
    }

    public ArchElement {
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        Objects.requireNonNull(scope);
        moduleHint = moduleHint == null || moduleHint.isBlank() ? null : moduleHint.strip();
    }

    public ArchElement(String name, Type type, ArchScope scope, boolean transport, String description,
            String technology) {
        this(name, type, scope, transport, description, technology, null);
    }

    /** Dernier segment du nom canonique. */
    public String shortName() {
        return name.substring(name.lastIndexOf('/') + 1);
    }

    /** Vrai si {@code name} désigne cet élément ou l'un de ses descendants. */
    public static boolean within(String name, String ancestor) {
        return name.equals(ancestor) || name.startsWith(ancestor + "/");
    }
}
