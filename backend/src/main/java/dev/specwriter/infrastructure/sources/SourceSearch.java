package dev.specwriter.infrastructure.sources;

import dev.specwriter.domain.port.SourceTree.Limits;
import dev.specwriter.domain.port.SourceTree.ModuleHit;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleId;
import dev.specwriter.domain.sources.Occurrence;
import dev.specwriter.domain.sources.PerimeterAnalysis.IncompleteReason;
import dev.specwriter.domain.sources.PerimeterModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/**
 * Lecture des fichiers retenus d'un parcours et recherche des termes (013, research.md R2) : sur des
 * fils virtuels, 16 lectures à la fois ; chaque fichier donne son résultat, fusionné ensuite dans
 * l'ordre des chemins pour un résultat identique d'une analyse à l'autre. Une ligne qui contient un
 * terme compte une occurrence de ce terme. Aucun contenu n'est journalisé.
 */
final class SourceSearch {

    static final int PARALLEL_READS = 16;
    /** Octets examinés pour reconnaître un fichier binaire (un octet nul). */
    static final int BINARY_PROBE = 8_192;
    static final int SKIPPED_EXAMPLES = 20;

    /** Fichier retenu et son module ; {@code module} nul hors de tout module. */
    record Candidate(java.nio.file.Path file, String path, Module module, long size) {
    }

    /** Ce que la recherche a vu : modules touchés, fichiers lus ou écartés, et arrêt éventuel. */
    record Result(List<ModuleHit> hits, int filesScanned, int filesSkipped, List<String> skippedExamples,
            IncompleteReason incomplete) {
    }

    private record FileResult(Candidate candidate, Map<String, Integer> counts, Map<String, List<Occurrence>> samples,
            boolean skipped, boolean tooLarge) {
    }

    private SourceSearch() {
    }

    static Result run(List<Candidate> candidates, List<String> terms, Module fallback, Limits limits, long deadline,
            BooleanSupplier cancelled) {
        List<String> lower = terms.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        Semaphore permits = new Semaphore(PARALLEL_READS);
        List<Future<FileResult>> futures = new ArrayList<>(candidates.size());
        IncompleteReason[] stop = {null};
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Candidate candidate : candidates) {
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        if (cancelled.getAsBoolean() || System.nanoTime() > deadline) {
                            return null;
                        }
                        return read(candidate, terms, lower, limits);
                    } finally {
                        permits.release();
                    }
                }));
            }
        }
        if (cancelled.getAsBoolean()) {
            stop[0] = IncompleteReason.CANCELLED;
        }
        List<FileResult> results = new ArrayList<>(futures.size());
        for (Future<FileResult> future : futures) {
            try {
                FileResult result = future.get();
                if (result == null) {
                    if (stop[0] == null) {
                        stop[0] = IncompleteReason.MAX_DURATION;
                    }
                } else {
                    results.add(result);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop[0] = IncompleteReason.CANCELLED;
            } catch (ExecutionException e) {
                // fichier illisible : compté parmi les fichiers écartés
                results.add(new FileResult(null, Map.of(), Map.of(), true, false));
            }
        }
        return merge(results, terms, fallback, stop[0]);
    }

    private static Result merge(List<FileResult> results, List<String> terms, Module fallback,
            IncompleteReason incomplete) {
        results.sort(Comparator.comparing((FileResult r) -> r.candidate() == null ? "" : r.candidate().path()));
        Map<ModuleId, Acc> byModule = new LinkedHashMap<>();
        int scanned = 0;
        int skipped = 0;
        List<String> examples = new ArrayList<>();
        for (FileResult r : results) {
            if (r.skipped()) {
                skipped++;
                if (r.tooLarge() && examples.size() < SKIPPED_EXAMPLES) {
                    examples.add(r.candidate().path());
                }
                continue;
            }
            scanned++;
            if (r.counts().isEmpty()) {
                continue;
            }
            Module module = r.candidate().module() == null ? fallback : r.candidate().module();
            Acc acc = byModule.computeIfAbsent(module.id(), id -> new Acc(module, terms));
            r.counts().forEach((term, n) -> acc.counts.merge(term, n, Integer::sum));
            r.samples().forEach((term, list) -> {
                List<Occurrence> kept = acc.samples.get(term);
                for (Occurrence o : list) {
                    if (kept.size() < PerimeterModule.MAX_SAMPLES_PER_TERM) {
                        kept.add(o);
                    }
                }
            });
        }
        List<ModuleHit> hits = byModule.values().stream().map(Acc::hit).toList();
        return new Result(hits, scanned, skipped, examples, incomplete);
    }

    private static FileResult read(Candidate candidate, List<String> terms, List<String> lower, Limits limits)
            throws IOException {
        if (candidate.size() > limits.maxFileBytes()) {
            return new FileResult(candidate, Map.of(), Map.of(), true, true);
        }
        byte[] bytes;
        try (InputStream in = Files.newInputStream(candidate.file(), LinkOption.NOFOLLOW_LINKS)) {
            bytes = in.readNBytes((int) Math.min(limits.maxFileBytes() + 1, Integer.MAX_VALUE - 8));
        }
        if (bytes.length > limits.maxFileBytes()) {
            return new FileResult(candidate, Map.of(), Map.of(), true, true);
        }
        for (int i = 0; i < Math.min(bytes.length, BINARY_PROBE); i++) {
            if (bytes[i] == 0) {
                return new FileResult(candidate, Map.of(), Map.of(), true, false);
            }
        }
        String text = decode(bytes);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, List<Occurrence>> samples = new LinkedHashMap<>();
        int lineNumber = 0;
        int start = 0;
        while (start <= text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = text.length();
            }
            lineNumber++;
            String line = text.substring(start, end);
            String lowered = line.toLowerCase(Locale.ROOT);
            for (int t = 0; t < terms.size(); t++) {
                if (lowered.contains(lower.get(t))) {
                    String term = terms.get(t);
                    counts.merge(term, 1, Integer::sum);
                    List<Occurrence> list = samples.computeIfAbsent(term, k -> new ArrayList<>());
                    if (list.size() < PerimeterModule.MAX_SAMPLES_PER_TERM) {
                        list.add(Occurrence.of(candidate.path(), lineNumber, term, line.replace("\r", "")));
                    }
                }
            }
            start = end + 1;
        }
        return new FileResult(candidate, counts, samples, false, false);
    }

    /** UTF-8, sinon ISO-8859-1 : un fichier ancien en Latin-1 reste lisible. */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /** Résultats d'un module, termes dans l'ordre de l'analyse. */
    private static final class Acc {
        final Module module;
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final Map<String, List<Occurrence>> samples = new LinkedHashMap<>();

        Acc(Module module, List<String> terms) {
            this.module = module;
            for (String term : terms) {
                counts.put(term, 0);
                samples.put(term, new ArrayList<>());
            }
        }

        ModuleHit hit() {
            Map<String, Integer> found = new LinkedHashMap<>();
            counts.forEach((term, n) -> {
                if (n > 0) {
                    found.put(term, n);
                }
            });
            List<Occurrence> all = new ArrayList<>();
            samples.values().forEach(all::addAll);
            return new ModuleHit(module, found, all);
        }
    }
}
