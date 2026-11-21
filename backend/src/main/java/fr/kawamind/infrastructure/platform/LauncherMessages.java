package fr.kawamind.infrastructure.platform;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/**
 * Messages d'un second lancement (019, research.md R5) : toujours sur la sortie standard ; et, pour
 * une instance qui ne répond pas, une boîte visible sans terminal. Sous Linux, pas de bibliothèque
 * graphique : zenity, kdialog ou une notification, s'ils sont là.
 */
public class LauncherMessages {

    static final String OPENED = "spec-writer est déjà lancé : un nouvel onglet a été ouvert.";
    static final String TITLE = "spec-writer";

    /** Lance une commande ; vrai si elle a abouti. */
    @FunctionalInterface
    interface Commands {
        boolean run(List<String> command);
    }

    /** Boîte de dialogue de Java (macOS, Windows). */
    @FunctionalInterface
    interface Dialog {
        void warn(String title, String text);
    }

    private final PrintStream out;
    private final String os;
    private final Commands commands;
    private final Dialog dialog;

    LauncherMessages(PrintStream out, String osName, Commands commands, Dialog dialog) {
        this.out = out;
        this.os = osName.toLowerCase(Locale.ROOT);
        this.commands = commands;
        this.dialog = dialog;
    }

    /** Messages réels : console, boîte de Java hors mode sans écran, commandes du bureau sous Linux. */
    public static LauncherMessages system() {
        return new LauncherMessages(System.out, System.getProperty("os.name", ""), LauncherMessages::exec,
                (title, text) -> {
                    if (!java.awt.GraphicsEnvironment.isHeadless()) {
                        javax.swing.JOptionPane.showMessageDialog(null, text, title, javax.swing.JOptionPane.WARNING_MESSAGE);
                    }
                });
    }

    void opened() {
        out.println(OPENED);
    }

    void print(String url) {
        out.println("spec-writer est déjà lancé. Ouvrez-le dans votre navigateur : " + url);
    }

    void notResponding() {
        String text = "spec-writer est déjà lancé mais ne répond pas. " + howToStop();
        out.println(text);
        if (os.contains("mac") || os.contains("win")) {
            dialog.warn(TITLE, text);
            return;
        }
        boolean shown = commands.run(List.of("zenity", "--warning", "--title=" + TITLE, "--text=" + text))
                || commands.run(List.of("kdialog", "--title", TITLE, "--sorry", text))
                || commands.run(List.of("notify-send", TITLE, text));
        if (!shown) {
            out.println("(aucune boîte de dialogue disponible : message affiché ici seulement)");
        }
    }

    String howToStop() {
        if (os.contains("mac")) {
            return "Arrêtez-le depuis le Moniteur d'activité (processus spec-writer, ou java), puis relancez-le.";
        }
        if (os.contains("win")) {
            return "Arrêtez-le depuis le Gestionnaire des tâches (spec-writer, ou Java), puis relancez-le.";
        }
        return "Arrêtez-le avec la commande « pkill -f spec-writer », puis relancez-le.";
    }

    private static boolean exec(List<String> command) {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true).start().waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
