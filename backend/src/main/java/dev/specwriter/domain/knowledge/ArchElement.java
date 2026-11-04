package dev.specwriter.domain.knowledge;

import java.util.Objects;

/**
 * Élément du modèle Structurizr, désigné par son nom canonique {@code Système/Conteneur/Composant}
 * (data-model.md, KnowledgeItem). {@code transport} : topic, file ou bus (research.md R22).
 */
public record ArchElement(String name, Type type, ArchScope scope, boolean transport, String description,
        String technology) {

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
