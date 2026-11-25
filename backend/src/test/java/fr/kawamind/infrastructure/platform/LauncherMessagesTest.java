package fr.kawamind.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T032 : messages d'un second lancement, console et boîte selon le système (019, R5). */
class LauncherMessagesTest {

    final ByteArrayOutputStream console = new ByteArrayOutputStream();
    final List<String> tried = new ArrayList<>();
    final List<String> dialogs = new ArrayList<>();

    LauncherMessages on(String os, Set<String> available) {
        return new LauncherMessages(new PrintStream(console, true, StandardCharsets.UTF_8), os, c -> {
            tried.add(c.getFirst());
            return available.contains(c.getFirst());
        }, (title, text) -> dialogs.add(text));
    }

    String out() {
        return console.toString(StandardCharsets.UTF_8);
    }

    @Test
    void ouvertOuAdresseSansBoite() {
        LauncherMessages m = on("Linux", Set.of("zenity"));
        m.opened();
        m.print("http://127.0.0.1:1/?launch=x");

        assertThat(out()).contains("un nouvel onglet a été ouvert").contains("launch=x");
        assertThat(tried).isEmpty();
        assertThat(dialogs).isEmpty();
    }

    @Test
    void macOsEtWindowsBoiteDeJava() {
        on("Mac OS X", Set.of()).notResponding();
        on("Windows 11", Set.of()).notResponding();

        assertThat(dialogs).hasSize(2);
        assertThat(dialogs.get(0)).contains("Moniteur d'activité");
        assertThat(dialogs.get(1)).contains("Gestionnaire des tâches");
        assertThat(tried).isEmpty();
    }

    @Test
    void linuxZenityDAbord() {
        on("Linux", Set.of("zenity", "kdialog")).notResponding();

        assertThat(tried).containsExactly("zenity");
        assertThat(out()).contains("pkill -f spec-writer");
    }

    @Test
    void linuxPuisKdialogPuisNotification() {
        on("Linux", Set.of("notify-send")).notResponding();

        assertThat(tried).containsExactly("zenity", "kdialog", "notify-send");
    }

    @Test
    void linuxSansRienLaConsoleSeule() {
        on("Linux", Set.of()).notResponding();

        assertThat(out()).contains("ne répond pas").contains("message affiché ici seulement");
        assertThat(dialogs).isEmpty();
    }
}
