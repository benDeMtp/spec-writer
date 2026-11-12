package dev.specwriter.infrastructure.platform;

import dev.specwriter.domain.port.BrowserPort;
import dev.specwriter.domain.settings.Browser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Navigateurs installés sur le poste (013, T057), cherchés à des emplacements connus : applications
 * de {@code /Applications} sous macOS, exécutables des dossiers de programmes sous Windows,
 * commandes du {@code PATH} sous Linux. Aucune commande n'est lancée pour les détecter, et seule
 * une adresse de spec-writer est passée au navigateur lancé.
 */
public final class DesktopBrowsers implements BrowserPort {

    /** Lance une commande ; {@code wait} attend sa fin (macOS {@code open}), sinon la laisse tourner. */
    @FunctionalInterface
    public interface Launcher {
        boolean run(List<String> command, boolean wait);
    }

    /** Navigateur connu et ses emplacements par système. */
    record Known(String id, String name, List<String> macApps, List<String> windowsExes, List<String> linuxCommands) {
    }

    static final List<Known> KNOWN = List.of(
            new Known("chrome", "Google Chrome", List.of("Google Chrome.app"),
                    List.of("Google/Chrome/Application/chrome.exe"), List.of("google-chrome", "google-chrome-stable")),
            new Known("firefox", "Firefox", List.of("Firefox.app"), List.of("Mozilla Firefox/firefox.exe"),
                    List.of("firefox")),
            new Known("edge", "Microsoft Edge", List.of("Microsoft Edge.app"),
                    List.of("Microsoft/Edge/Application/msedge.exe"), List.of("microsoft-edge", "microsoft-edge-stable")),
            new Known("safari", "Safari", List.of("Safari.app"), List.of(), List.of()),
            new Known("brave", "Brave", List.of("Brave Browser.app"),
                    List.of("BraveSoftware/Brave-Browser/Application/brave.exe"), List.of("brave-browser", "brave")),
            new Known("vivaldi", "Vivaldi", List.of("Vivaldi.app"), List.of("Vivaldi/Application/vivaldi.exe"),
                    List.of("vivaldi", "vivaldi-stable")),
            new Known("opera", "Opera", List.of("Opera.app"), List.of("Programs/Opera/opera.exe"), List.of("opera")),
            new Known("arc", "Arc", List.of("Arc.app"), List.of(), List.of()),
            new Known("chromium", "Chromium", List.of("Chromium.app"), List.of(), List.of("chromium", "chromium-browser")));

    private final String os;
    private final Map<String, String> env;
    private final Path home;
    private final Predicate<Path> exists;
    private final Launcher launcher;
    private final BrowserLauncher systemDefault;

    public DesktopBrowsers(String osName, Map<String, String> env, Path home, Predicate<Path> exists, Launcher launcher,
            BrowserLauncher systemDefault) {
        this.os = osName.toLowerCase(Locale.ROOT);
        this.env = Map.copyOf(env);
        this.home = home;
        this.exists = exists;
        this.launcher = launcher;
        this.systemDefault = systemDefault;
    }

    public static DesktopBrowsers system() {
        return new DesktopBrowsers(System.getProperty("os.name", ""), System.getenv(),
                Path.of(System.getProperty("user.home", "")), DesktopBrowsers::present, DesktopBrowsers::launch,
                BrowserLauncher.system());
    }

    @Override
    public List<Browser> installed() {
        return KNOWN.stream().filter(k -> location(k).isPresent()).map(k -> new Browser(k.id(), k.name())).toList();
    }

    @Override
    public boolean open(String browserId, String url) {
        if (browserId == null) {
            return systemDefault.open(java.net.URI.create(url));
        }
        Optional<Known> known = KNOWN.stream().filter(k -> k.id().equals(browserId)).findFirst();
        Optional<Path> where = known.flatMap(this::location);
        if (where.isEmpty()) {
            return false;
        }
        if (mac()) {
            return launcher.run(List.of("open", "-a", where.get().toString(), url), true);
        }
        return launcher.run(List.of(where.get().toString(), url), false);
    }

    /** Premier emplacement existant du navigateur sur ce système. */
    Optional<Path> location(Known k) {
        return candidates(k).stream().filter(exists).findFirst();
    }

    private List<Path> candidates(Known k) {
        List<Path> out = new ArrayList<>();
        if (mac()) {
            for (String app : k.macApps()) {
                out.add(Path.of("/Applications", app));
                out.add(home.resolve("Applications").resolve(app));
            }
        } else if (os.startsWith("windows")) {
            for (String var : List.of("ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA")) {
                String dir = env.get(var);
                if (dir != null && !dir.isBlank()) {
                    k.windowsExes().forEach(exe -> out.add(Path.of(dir).resolve(exe)));
                }
            }
        } else {
            String path = env.getOrDefault("PATH", "");
            for (String command : k.linuxCommands()) {
                for (String dir : path.split(File.pathSeparator)) {
                    if (!dir.isBlank()) {
                        out.add(Path.of(dir, command));
                    }
                }
            }
        }
        return out;
    }

    private boolean mac() {
        return os.startsWith("mac");
    }

    private static boolean present(Path p) {
        return Files.isDirectory(p) || Files.isExecutable(p);
    }

    /**
     * Lance la commande. Un navigateur lancé directement reste ouvert : on attend deux secondes, et
     * on ne le considère en échec que s'il s'est arrêté en erreur.
     */
    private static boolean launch(List<String> command, boolean wait) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            boolean ended = p.waitFor(wait ? 10 : 2, TimeUnit.SECONDS);
            return ended ? p.exitValue() == 0 : !wait;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
