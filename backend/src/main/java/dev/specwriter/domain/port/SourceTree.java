package dev.specwriter.domain.port;

import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.SourceRoot;
import java.time.Duration;
import java.util.List;

/**
 * Lecture des sources d'un projet sur le poste (013, research.md R2 à R4) : sans suivre les liens
 * symboliques, sans sortir de la racine, sans lire de fichier sensible ni de fichier généré. Rien
 * n'est écrit ni conservé.
 */
public interface SourceTree {

    /** Limites d'un parcours ; une limite atteinte arrête le parcours et le dit. */
    record Limits(int maxFiles, Duration maxDuration, long maxFileBytes, int maxModules) {
        public static final Limits DEFAULT = new Limits(50_000, Duration.ofSeconds(120), 1_048_576, 2_000);
    }

    /** Problème d'une racine qui empêche de la parcourir. */
    enum RootProblem { NOT_FOUND, NOT_A_FOLDER, ACCESS_DENIED }

    /**
     * Aperçu d'une racine : modules détectés (triés par chemin) et décompte des fichiers, sans en lire
     * d'autre que les descripteurs.
     */
    record Preview(List<Module> modules, int retainedFiles, int excludedFiles, int sensitiveFiles, int ignoredByGit,
            boolean limitReached, RootProblem problem) {

        public Preview {
            modules = List.copyOf(modules);
        }

        public static Preview of(RootProblem problem) {
            return new Preview(List.of(), 0, 0, 0, 0, false, problem);
        }
    }

    Preview preview(SourceRoot root, Limits limits);
}
