package fr.kawamind.infrastructure.platform;

import fr.kawamind.application.lifecycle.RunningWork;
import fr.kawamind.infrastructure.web.TabOpener;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.desktop.AppReopenedListener;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * macOS (019, research.md R6) : un clic sur le Dock, ou une réouverture depuis le Finder, ouvre un
 * onglet ; ⌘Q et Quitter du Dock demandent confirmation si un travail est en cours, puis passent
 * par l'arrêt propre (crochet de la JVM). Seulement pour l'instance qui tient le verrou et ouvre le
 * navigateur : ni en dev mode, ni en test, ni en E2E.
 */
@ApplicationScoped
public class MacDesktopIntegration {

    private static final Logger LOG = Logger.getLogger(MacDesktopIntegration.class);

    /** Réponse à une demande de fin de macOS. */
    interface QuitResponse {
        void perform();

        void cancel();
    }

    /** Événements de l'application sous macOS ; {@link java.awt.Desktop} en vrai, simulés en test. */
    interface DesktopEvents {
        void onQuit(java.util.function.Consumer<QuitResponse> handler);

        void onReopen(Runnable handler);
    }

    /** Confirmation : vrai pour « Quitter quand même ». */
    @FunctionalInterface
    interface Confirm {
        boolean confirm(String title, String text);
    }

    @Inject
    RunningWork work;

    @Inject
    TabOpener opener;

    @ConfigProperty(name = "specwriter.open-browser", defaultValue = "true")
    boolean openBrowser;

    void onStart(@Observes StartupEvent event) {
        if (!applies(System.getProperty("os.name", ""), GraphicsEnvironment.isHeadless(), InstanceLock.held(), openBrowser)) {
            return;
        }
        try {
            install(new AwtDesktopEvents(), MacDesktopIntegration::swingConfirm);
        } catch (UnsupportedOperationException | SecurityException e) {
            LOG.warnf("Dock de macOS non relié (%s)", e.getClass().getSimpleName());
        }
    }

    /** Seulement l'instance qui tient le verrou, sous macOS, avec un écran, et qui ouvre le navigateur. */
    static boolean applies(String osName, boolean headless, boolean lockHeld, boolean openBrowser) {
        return osName.toLowerCase(Locale.ROOT).contains("mac") && !headless && lockHeld && openBrowser;
    }

    void install(DesktopEvents events, Confirm confirm) {
        events.onReopen(opener::open);
        events.onQuit(response -> {
            List<RunningWork.Item> items = work.items();
            if (items.isEmpty() || confirm.confirm("Quitter spec-writer ?", confirmation(items))) {
                response.perform();
            } else {
                response.cancel();
            }
        });
    }

    static String confirmation(List<RunningWork.Item> items) {
        return items.stream().map(MacDesktopIntegration::label).map(l -> "• " + l)
                .collect(Collectors.joining("\n", "Ces travaux sont en cours :\n", "\n\nQuitter quand même ?"));
    }

    /** Mêmes libellés que la confirmation de l'interface (contracts/ui.md). */
    static String label(RunningWork.Item item) {
        return switch (item.kind()) {
            case INDEXATION -> "Indexation" + of(" du projet ", item.projectName())
                    + " : elle s'arrête, à relancer depuis Connaissance du projet.";
            case AGENT_REPLY -> "Réponse de l'agent" + of(" dans ", item.conversationTitle())
                    + " : le texte déjà reçu est gardé, marqué incomplet.";
            case REVISION -> "Proposition de version" + of(" dans ", item.conversationTitle()) + " : elle est perdue, à relancer.";
            case GENERATION -> "Génération d'une spécification" + of(" du projet ", item.projectName())
                    + " : elle est perdue, à relancer.";
        };
    }

    private static String of(String prefix, String name) {
        return name == null ? "" : prefix + name;
    }

    private static boolean swingConfirm(String title, String text) {
        Object[] options = { "Quitter quand même", "Annuler" };
        return JOptionPane.showOptionDialog(null, text, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[1]) == 0;
    }

    /** {@link Desktop} : gestionnaire de fin et écoute de la réouverture. */
    static final class AwtDesktopEvents implements DesktopEvents {

        private final Desktop desktop = Desktop.getDesktop();

        @Override
        public void onQuit(java.util.function.Consumer<QuitResponse> handler) {
            desktop.setQuitHandler((event, response) -> handler.accept(new QuitResponse() {
                @Override
                public void perform() {
                    response.performQuit();
                }

                @Override
                public void cancel() {
                    response.cancelQuit();
                }
            }));
        }

        @Override
        public void onReopen(Runnable handler) {
            desktop.addAppEventListener((AppReopenedListener) event -> handler.run());
        }
    }
}
