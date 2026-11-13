package dev.specwriter.domain.port;

import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleDescriptor;
import dev.specwriter.domain.sources.ModuleId;
import dev.specwriter.domain.sources.Occurrence;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterTerms;
import dev.specwriter.domain.sources.SourceRoot;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

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

    /**
     * Module où un terme au moins a été trouvé : nombre exact de lignes par terme (termes trouvés
     * seulement, dans l'ordre de l'analyse) et échantillon de 20 occurrences au plus par terme.
     */
    record ModuleHit(Module module, Map<String, Integer> countByTerm, List<Occurrence> samples) {

        public ModuleHit {
            countByTerm = Map.copyOf(countByTerm);
            samples = List.copyOf(samples);
        }

        public int total() {
            return countByTerm.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Recherche dans toutes les racines : modules touchés, tous les modules détectés et les
     * descripteurs Maven (pour la propagation), état de chaque racine, et arrêt éventuel.
     */
    record Scan(List<ModuleHit> hits, List<Module> modules, Map<ModuleId, ModuleDescriptor> descriptors,
            List<PerimeterAnalysis.RootStatus> roots, int filesScanned, int filesSkipped, List<String> skippedExamples,
            PerimeterAnalysis.IncompleteReason incomplete) {

        public Scan {
            hits = List.copyOf(hits);
            modules = List.copyOf(modules);
            descriptors = Map.copyOf(descriptors);
            roots = List.copyOf(roots);
            skippedExamples = List.copyOf(skippedExamples);
        }
    }

    /**
     * Cherche chaque terme, littéralement et sans tenir compte de la casse, dans tous les fichiers
     * retenus de toutes les racines, sans limite de pertinence.
     */
    Scan search(List<SourceRoot> roots, PerimeterTerms terms, Limits limits, BooleanSupplier cancelled);
}
