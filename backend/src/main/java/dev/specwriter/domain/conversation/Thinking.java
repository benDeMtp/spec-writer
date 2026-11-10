package dev.specwriter.domain.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Réflexion de l'agent pendant un tour (010) : ce que le modèle a résumé de son raisonnement, étape
 * par étape, et la durée du tour. Gardée avec la réponse, 20 000 caractères au plus ; le texte
 * n'apparaît jamais dans {@link #toString()}.
 */
public record Thinking(List<Part> parts, long durationMs) {

    public static final int MAX_TEXT = 20_000;

    /** Ce que fait l'agent : résumer sa mémoire, choisir une recherche, rédiger la réponse. */
    public enum Stage {
        MEMORY, PLAN, ANSWER
    }

    public record Part(Stage stage, String text) {

        public Part {
            Objects.requireNonNull(stage);
            text = text == null ? "" : text;
        }

        @Override
        public String toString() {
            return "Part[" + stage + ", " + text.length() + " caractères]";
        }
    }

    public Thinking {
        parts = bounded(parts == null ? List.of() : parts);
        durationMs = Math.max(0, durationMs);
    }

    public int length() {
        return parts.stream().mapToInt(p -> p.text().length()).sum();
    }

    /** Les étapes au-delà de {@link #MAX_TEXT} caractères sont coupées. */
    private static List<Part> bounded(List<Part> parts) {
        List<Part> out = new ArrayList<>();
        int left = MAX_TEXT;
        for (Part p : parts) {
            String text = p.text().length() > left ? p.text().substring(0, left) : p.text();
            left -= text.length();
            out.add(new Part(p.stage(), text));
        }
        return List.copyOf(out);
    }

    @Override
    public String toString() {
        return "Thinking[" + parts.size() + " étape(s), " + length() + " caractères, " + durationMs + " ms]";
    }
}
