package fr.kawamind.domain.specification;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Énoncé de story en tête d'une spécification (015, FR-023, R9) : « En tant que rôle, je veux
 * capacité, afin de bénéfice ». {@code assumed} : parties supposées, faute d'appui dans le besoin,
 * les réponses ou les documents joints.
 */
public record UserStory(String role, String capability, String benefit, Set<StoryPart> assumed) {

    public static final int MAX_PART = 300;
    /** Découpage proposé d'une story trop large (FR-024) : vide, ou de 2 à 5 stories. */
    public static final int MIN_SPLIT = 2;
    public static final int MAX_SPLIT = 5;
    public static final int MAX_SPLIT_TITLE = 120;

    public UserStory {
        role = part(role, "rôle");
        capability = part(capability, "capacité");
        benefit = part(benefit, "bénéfice");
        EnumSet<StoryPart> parts = EnumSet.noneOf(StoryPart.class);
        if (assumed != null) {
            parts.addAll(assumed);
        }
        assumed = Collections.unmodifiableSet(parts);
    }

    public boolean isAssumed(StoryPart part) {
        return assumed.contains(part);
    }

    /** Titres du découpage proposé, nettoyés ; {@code null} : aucun découpage. */
    public static List<String> split(List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            return List.of();
        }
        if (titles.size() < MIN_SPLIT || titles.size() > MAX_SPLIT) {
            throw invalid("Un découpage propose de " + MIN_SPLIT + " à " + MAX_SPLIT + " stories.");
        }
        return titles.stream().map(t -> {
            String title = t == null ? "" : t.strip();
            if (title.isEmpty() || title.length() > MAX_SPLIT_TITLE) {
                throw invalid("Le titre d'une story proposée fait de 1 à " + MAX_SPLIT_TITLE + " caractères.");
            }
            return title;
        }).toList();
    }

    private static String part(String value, String name) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > MAX_PART) {
            throw invalid("Le " + name + " d'une story fait de 1 à " + MAX_PART + " caractères.");
        }
        return text;
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message);
    }
}
