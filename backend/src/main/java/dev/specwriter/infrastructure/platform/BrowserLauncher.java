package dev.specwriter.infrastructure.platform;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Ouvre l'URL de l'application dans le navigateur par défaut (research.md R15) :
 * {@code Desktop.browse}, puis la commande propre à l'OS.
 */
public final class BrowserLauncher {

    /** Abstraction de {@link Desktop}, pour les tests. */
    public interface DesktopBrowser {
        boolean supported();

        void browse(URI uri) throws Exception;
    }

    /** Exécute une commande ; renvoie {@code true} si elle a réussi. */
    @FunctionalInterface
    public interface CommandRunner {
        boolean run(List<String> command);
    }

    private final String osName;
    private final DesktopBrowser desktop;
    private final CommandRunner runner;

    public BrowserLauncher(String osName, DesktopBrowser desktop, CommandRunner runner) {
        this.osName = osName.toLowerCase(Locale.ROOT);
        this.desktop = desktop;
        this.runner = runner;
    }

    public static BrowserLauncher system() {
        return new BrowserLauncher(System.getProperty("os.name", ""), new AwtDesktop(), BrowserLauncher::exec);
    }

    public boolean open(URI uri) {
        if (desktop.supported()) {
            try {
                desktop.browse(uri);
                return true;
            } catch (Exception ignored) {
                // repli sur la commande de l'OS
            }
        }
        return runner.run(command(uri));
    }

    private List<String> command(URI uri) {
        if (osName.startsWith("windows")) {
            return List.of("rundll32", "url.dll,FileProtocolHandler", uri.toString());
        }
        if (osName.startsWith("mac")) {
            return List.of("open", uri.toString());
        }
        return List.of("xdg-open", uri.toString());
    }

    private static boolean exec(List<String> command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static final class AwtDesktop implements DesktopBrowser {
        @Override
        public boolean supported() {
            try {
                return !java.awt.GraphicsEnvironment.isHeadless()
                        && Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public void browse(URI uri) throws Exception {
            Desktop.getDesktop().browse(uri);
        }
    }
}
