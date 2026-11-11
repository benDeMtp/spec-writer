package dev.specwriter.infrastructure.sources;

import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.sources.GitIgnoreRules;
import dev.specwriter.domain.sources.GitIgnoreStack;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleDescriptor;
import dev.specwriter.domain.sources.ModuleId;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.SourceExclusions;
import dev.specwriter.domain.sources.SourceRoot;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sources d'un projet lues sur le poste (013, research.md R2 à R4) : parcours sans suivre les liens
 * symboliques, dossiers générés et cachés écartés, fichiers sensibles jamais ouverts, puis
 * {@code .gitignore} et motifs de l'utilisateur. Les modules sont reconnus à leur descripteur
 * ({@code pom.xml}, {@code package.json}, {@code Chart.yaml}). Rien n'est écrit ni journalisé.
 */
public final class LocalSourceTree implements SourceTree {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Taille maximale d'un descripteur ou d'un {@code .gitignore} lu. */
    private static final long MAX_DESCRIPTOR = 1_048_576;

    @Override
    public Preview preview(SourceRoot root, Limits limits) {
        Path dir = Path.of(root.location());
        RootProblem problem = problem(dir);
        if (problem != null) {
            return Preview.of(problem);
        }
        Walk walk = new Walk(root, limits, () -> false, null);
        walk.run();
        return new Preview(walk.sortedModules(), walk.retained, walk.excluded, walk.sensitive, walk.ignoredByGit,
                walk.stop != null || walk.unreadable, null);
    }

    public static RootProblem problem(Path dir) {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return RootProblem.NOT_FOUND;
        }
        if (!Files.isDirectory(dir)) {
            return RootProblem.NOT_A_FOLDER;
        }
        if (!Files.isReadable(dir) || !Files.isExecutable(dir)) {
            return RootProblem.ACCESS_DENIED;
        }
        return null;
    }

    /** Raison de l'arrêt d'un parcours avant sa fin. */
    enum Stop { MAX_FILES, MAX_DURATION, MAX_MODULES, CANCELLED }

    /** Fichier retenu, à lire par la recherche. */
    record Candidate(Path file, String path, Module module, long size) {
    }

    /** Parcours d'une racine : modules, décomptes, et fichiers retenus confiés à {@code onFile}. */
    static final class Walk extends SimpleFileVisitor<Path> {

        private record Frame(GitIgnoreStack ignores, Module module) {
        }

        private final SourceRoot source;
        private final Path root;
        private final Limits limits;
        private final long deadline;
        private final BooleanSupplier cancelled;
        private final Consumer<Candidate> onFile;
        private final Deque<Frame> frames = new ArrayDeque<>();
        final List<Module> modules = new ArrayList<>();
        final Map<ModuleId, ModuleDescriptor> descriptors = new LinkedHashMap<>();
        int retained;
        int excluded;
        int sensitive;
        int ignoredByGit;
        Stop stop;
        /** La racine est devenue illisible pendant le parcours. */
        boolean unreadable;

        Walk(SourceRoot source, Limits limits, BooleanSupplier cancelled, Consumer<Candidate> onFile) {
            this.source = source;
            this.root = Path.of(source.location()).toAbsolutePath().normalize();
            this.limits = limits;
            this.deadline = System.nanoTime() + limits.maxDuration().toNanos();
            this.cancelled = cancelled;
            this.onFile = onFile;
        }

        void run() {
            try {
                Files.walkFileTree(root, this);
            } catch (IOException e) {
                // racine devenue illisible pendant le parcours : ce qui a été vu est gardé
                unreadable = true;
            }
        }

        List<Module> sortedModules() {
            return modules.stream().sorted(Comparator.comparing(Module::path)).toList();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            Frame parent = frames.peek();
            String path = relative(dir);
            if (parent != null) {
                if (SourceExclusions.ignoredDirectory(name(dir))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (parent.ignores().ignored(path, true)) {
                    ignoredByGit++;
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (source.exclusions().excludes(path) || source.exclusions().excludes(path + "/")) {
                    excluded++;
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
            if (stopped()) {
                return FileVisitResult.TERMINATE;
            }
            GitIgnoreStack ignores = (parent == null ? GitIgnoreStack.EMPTY : parent.ignores()).push(gitignore(dir, path));
            Module module = detect(dir, path);
            if (module != null && modules.size() >= limits.maxModules()) {
                stop = Stop.MAX_MODULES;
                return FileVisitResult.TERMINATE;
            }
            if (module != null) {
                modules.add(module);
            }
            frames.push(new Frame(ignores, module != null ? module : parent == null ? null : parent.module()));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException e) {
            frames.pop();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!attrs.isRegularFile()) {
                // lien symbolique ou fichier spécial : jamais suivi
                return FileVisitResult.CONTINUE;
            }
            String name = name(file);
            if (SourceExclusions.sensitive(name)) {
                if (!name.startsWith(".git")) {
                    sensitive++;
                }
                return FileVisitResult.CONTINUE;
            }
            String path = relative(file);
            Frame frame = frames.peek();
            if (frame != null && frame.ignores().ignored(path, false)) {
                ignoredByGit++;
                return FileVisitResult.CONTINUE;
            }
            if (source.exclusions().excludes(path)) {
                excluded++;
                return FileVisitResult.CONTINUE;
            }
            if (retained >= limits.maxFiles()) {
                stop = Stop.MAX_FILES;
                return FileVisitResult.TERMINATE;
            }
            if (stopped()) {
                return FileVisitResult.TERMINATE;
            }
            retained++;
            if (onFile != null) {
                Module module = frame == null || frame.module() == null ? null : frame.module();
                onFile.accept(new Candidate(file, path, module, attrs.size()));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
            // fichier ou dossier illisible : le reste est parcouru
            return FileVisitResult.CONTINUE;
        }

        private boolean stopped() {
            if (cancelled.getAsBoolean()) {
                stop = Stop.CANCELLED;
            } else if (System.nanoTime() > deadline) {
                stop = Stop.MAX_DURATION;
            }
            return stop != null;
        }

        private GitIgnoreRules gitignore(Path dir, String path) {
            String content = small(dir.resolve(".gitignore"));
            return content == null ? GitIgnoreRules.parse(path, "") : GitIgnoreRules.parse(path, content);
        }

        /** Module dont le dossier porte un descripteur : {@code pom.xml}, puis {@code package.json}, puis {@code Chart.yaml}. */
        private Module detect(Path dir, String path) {
            Path pom = dir.resolve("pom.xml");
            if (regular(pom)) {
                ModuleDescriptor descriptor = pomDescriptor(pom);
                Module module = new Module(source.id(), path, orElse(descriptor.artifactId(), dir), ModuleKind.MAVEN,
                        descriptor.coordinates());
                descriptors.put(module.id(), descriptor);
                return module;
            }
            Path npm = dir.resolve("package.json");
            if (regular(npm)) {
                return new Module(source.id(), path, orElse(npmName(npm), dir), ModuleKind.NPM, null);
            }
            Path chart = dir.resolve("Chart.yaml");
            if (regular(chart)) {
                return new Module(source.id(), path, orElse(chartName(chart), dir), ModuleKind.HELM, null);
            }
            return null;
        }

        private String orElse(String name, Path dir) {
            if (name != null && !name.isBlank()) {
                return name;
            }
            String dirName = name(dir);
            return dirName.isEmpty() ? source.label() : dirName;
        }

        private String relative(Path path) {
            return root.relativize(path).toString().replace('\\', '/');
        }
    }

    private static ModuleDescriptor pomDescriptor(Path pom) {
        if (size(pom) > MAX_DESCRIPTOR) {
            return ModuleDescriptor.unreadable();
        }
        try (InputStream in = Files.newInputStream(pom, LinkOption.NOFOLLOW_LINKS)) {
            return MavenPomReader.read(in);
        } catch (IOException e) {
            return ModuleDescriptor.unreadable();
        }
    }

    private static String npmName(Path packageJson) {
        String content = small(packageJson);
        if (content == null) {
            return null;
        }
        try {
            JsonNode name = JSON.readTree(content).get("name");
            return name != null && name.isString() ? name.asString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Ligne {@code name:} de premier niveau, sans bibliothèque YAML (research.md R4). */
    static String chartName(Path chart) {
        String content = small(chart);
        if (content == null) {
            return null;
        }
        for (String line : content.split("\r?\n")) {
            if (line.startsWith("name:")) {
                String value = line.substring(5).strip();
                int comment = value.indexOf(" #");
                if (comment >= 0) {
                    value = value.substring(0, comment).strip();
                }
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static String small(Path file) {
        if (!regular(file) || size(file) > MAX_DESCRIPTOR) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean regular(Path file) {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    static String name(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }
}
