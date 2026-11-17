package fr.kawamind.domain.task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Proposition de tâches, relue et corrigée par l'utilisateur avant d'être validée (015, FR-002 à
 * FR-004, R8). Elle vise un état d'avancement : quelques tâches, chacune avec un résultat vérifiable.
 */
public record TaskProposal(Basing basing, List<ProposedTask> tasks) {

    /** Ce sur quoi la proposition s'appuie : les modules touchés (013) ou les cas d'usage. */
    public enum Basing { MODULES, USE_CASES }

    /** Nombre de tâches attendu du modèle. */
    public record Bounds(int min, int max) {

        public static final int FIRST_MIN = 3;
        public static final int MAX = 8;

        /** 3 à 8 pour une première proposition ; 1 à 8 moins les tâches existantes pour un complément. */
        public static Bounds forExisting(int existing) {
            return existing == 0 ? new Bounds(FIRST_MIN, MAX) : new Bounds(1, Math.max(1, MAX - existing));
        }
    }

    public TaskProposal {
        Objects.requireNonNull(basing);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    /** Défauts de forme d'une tâche, quel que soit son auteur : longueurs et préalables antérieurs. */
    public List<String> shapeProblems() {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            ProposedTask t = tasks.get(i);
            String which = "Tâche " + (i + 1);
            if (t.title().isEmpty() || t.title().length() > SpecTask.MAX_TITLE) {
                problems.add(which + " : le titre fait de 1 à " + SpecTask.MAX_TITLE + " caractères.");
            }
            if (t.outcome().isEmpty() || t.outcome().length() > SpecTask.MAX_OUTCOME) {
                problems.add(which + " : le résultat attendu fait de 1 à " + SpecTask.MAX_OUTCOME + " caractères.");
            }
            for (int d : t.dependsOn()) {
                if (d < 0 || d >= i) {
                    problems.add(which + " : un préalable doit désigner une tâche qui la précède.");
                    break;
                }
            }
        }
        return problems;
    }

    /**
     * Défauts d'une réponse du modèle (garde-fou de {@code propose-tasks}) : nombre hors bornes, forme,
     * base inconnue, titre déjà pris par une tâche existante. Vide si la réponse est acceptable.
     */
    public List<String> modelProblems(Bounds bounds, Set<String> modules, Set<String> useCases,
            Set<String> existingTitles) {
        List<String> problems = new ArrayList<>();
        if (tasks.size() < bounds.min() || tasks.size() > bounds.max()) {
            problems.add("Proposez de " + bounds.min() + " à " + bounds.max() + " tâches, pas " + tasks.size() + ".");
        }
        problems.addAll(shapeProblems());
        Set<String> taken = new HashSet<>();
        existingTitles.forEach(t -> taken.add(key(t)));
        for (int i = 0; i < tasks.size(); i++) {
            ProposedTask t = tasks.get(i);
            String which = "Tâche " + (i + 1);
            Basis b = t.basis();
            boolean known = switch (b.kind()) {
                case MODULE -> modules.contains(b.ref());
                case USE_CASE -> useCases.contains(b.ref());
                case NONE -> false;
            };
            if (!known) {
                problems.add(which + " : la base doit être " + (modules.isEmpty() ? "un code de cas d'usage"
                        : "un module de <modules>") + " cité tel quel.");
            }
            if (!taken.add(key(t.title()))) {
                problems.add(which + " : ce titre existe déjà.");
            }
        }
        return problems;
    }

    private static String key(String title) {
        return title == null ? "" : title.strip().toLowerCase(Locale.ROOT);
    }
}
