package dev.specwriter.infrastructure.llm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Trouve le Copilot CLI de l'utilisateur. Les installeurs n'embarquent pas le runtime Copilot, dont
 * des composants ne sont pas redistribuables (research.md R15) ; les builds de développement le
 * gardent sur le classpath. Ordre : chemin imposé (configuration, puis {@code COPILOT_CLI_PATH}),
 * runtime embarqué, {@code PATH}, puis les dossiers d'installation habituels : une application
 * lancée depuis le Finder ou le menu Démarrer ne reçoit pas le {@code PATH} du shell.
 */
final class CopilotCliLocator {

    static final String ENV = "COPILOT_CLI_PATH";

    /** Issue de la recherche ; {@code searched} liste les chemins examinés, pour le diagnostic. */
    sealed interface Resolution {
        record Bundled() implements Resolution {
        }

        record External(Path cli) implements Resolution {
        }

        record Missing(List<Path> searched) implements Resolution {
        }
    }

    private final Map<String, String> env;
    private final Path home;
    private final boolean windows;
    private final boolean bundled;
    private final List<Path> systemDirs;

    CopilotCliLocator(Map<String, String> env, Path home, boolean windows, boolean bundled, List<Path> systemDirs) {
        this.env = Map.copyOf(env);
        this.home = home;
        this.windows = windows;
        this.bundled = bundled;
        this.systemDirs = List.copyOf(systemDirs);
    }

    static CopilotCliLocator fromSystem() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        String resource = "native/" + platform(System.getProperty("os.name", ""), System.getProperty("os.arch", ""))
                + "/platform.properties";
        boolean bundled = CopilotCliLocator.class.getClassLoader().getResource(resource) != null;
        return new CopilotCliLocator(System.getenv(), Path.of(System.getProperty("user.home")), windows, bundled,
                systemDirs(windows));
    }

    Resolution resolve(Optional<String> configured) {
        Optional<String> explicit = configured.filter(s -> !s.isBlank()).or(() -> Optional.ofNullable(get(ENV)))
                .filter(s -> !s.isBlank());
        if (explicit.isPresent()) {
            Path cli = Path.of(explicit.get().strip());
            return isExecutable(cli) ? new Resolution.External(cli) : new Resolution.Missing(List.of(cli));
        }
        if (bundled) {
            return new Resolution.Bundled();
        }
        List<Path> searched = new ArrayList<>();
        for (Path dir : candidateDirs()) {
            for (String name : windows ? List.of("copilot.exe", "copilot.cmd") : List.of("copilot")) {
                Path cli = dir.resolve(name);
                searched.add(cli);
                if (isExecutable(cli)) {
                    return new Resolution.External(cli);
                }
            }
        }
        return new Resolution.Missing(searched);
    }

    private List<Path> candidateDirs() {
        List<Path> dirs = new ArrayList<>();
        String path = get("PATH");
        if (path != null) {
            for (String entry : path.split(File.pathSeparator.equals(";") || windows ? ";" : ":")) {
                if (!entry.isBlank()) {
                    dirs.add(Path.of(entry.strip()));
                }
            }
        }
        if (windows) {
            Optional.ofNullable(get("LOCALAPPDATA")).ifPresent(d -> dirs.add(Path.of(d, "Microsoft", "WinGet", "Links")));
            Optional.ofNullable(get("APPDATA")).ifPresent(d -> dirs.add(Path.of(d, "npm")));
        } else {
            dirs.add(home.resolve(".local/bin"));
            dirs.add(home.resolve(".npm-global/bin"));
            dirs.add(home.resolve("bin"));
        }
        dirs.addAll(systemDirs);
        return dirs.stream().distinct().toList();
    }

    private static final List<String> SYSTEM_DIRS =
            List.of("/opt/homebrew/bin", "/usr/local/bin", "/home/linuxbrew/.linuxbrew/bin");

    /** Homebrew (macOS arm64 et Intel, Linux) et dossier système habituel. */
    static List<Path> systemDirs(boolean windows) {
        return windows ? List.of()
                : SYSTEM_DIRS.stream().map(Path::of).toList();
    }

    /** Plateforme au sens du runtime Copilot : {@code darwin-arm64}, {@code linux-x64}, {@code win32-x64}… */
    static String platform(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String name = os.startsWith("mac") || os.startsWith("darwin") ? "darwin" : os.startsWith("windows") ? "win32" : "linux";
        String arch = osArch.toLowerCase(Locale.ROOT);
        return name + "-" + (arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : "x64");
    }

    /**
     * Environnement du CLI : son dossier passe en tête du {@code PATH}. Un CLI installé par npm est un
     * script {@code node}, et {@code node} est en général installé à côté.
     */
    static Map<String, String> childPath(Map<String, String> parent, Path cli, boolean windows) {
        Map<String, String> env = new HashMap<>(parent);
        String key = parent.keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
        Path parentDir = cli.toAbsolutePath().getParent();
        if (parentDir == null) {
            return env;
        }
        String dir = parentDir.toString();
        String current = parent.get(key);
        env.put(key, current == null || current.isEmpty() ? dir : dir + (windows ? ";" : ":") + current);
        return env;
    }

    private String get(String name) {
        return env.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    private static boolean isExecutable(Path file) {
        return Files.isRegularFile(file) && Files.isExecutable(file);
    }
}
