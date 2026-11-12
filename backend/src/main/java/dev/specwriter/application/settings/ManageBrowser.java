package dev.specwriter.application.settings;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.BrowserPort;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.Browser;
import java.util.List;
import java.util.Optional;

/**
 * Navigateur où s'ouvre spec-writer (013, T057) : celui du système tant que rien n'est choisi, sinon
 * un des navigateurs détectés sur le poste. Un navigateur choisi puis désinstallé laisse la place à
 * celui du système.
 */
public class ManageBrowser {

    /**
     * Choix courant : {@code selected} vaut {@code null} pour le navigateur du système ;
     * {@code missing} signale un navigateur choisi qui n'est plus installé.
     */
    public record View(String selected, boolean missing, List<Browser> installed) {
    }

    /** Résultat d'une ouverture : le navigateur utilisé, {@code null} pour celui du système. */
    public record Opened(boolean opened, Browser browser) {
    }

    private final SettingsRepository settings;
    private final BrowserPort browsers;

    public ManageBrowser(SettingsRepository settings, BrowserPort browsers) {
        this.settings = settings;
        this.browsers = browsers;
    }

    public View get() {
        List<Browser> installed = browsers.installed();
        String selected = settings.browser();
        return new View(selected, selected != null && find(installed, selected).isEmpty(), installed);
    }

    /** Enregistre le choix ; vide ou {@code null} revient au navigateur du système. */
    public View save(String browserId) {
        String id = browserId == null || browserId.isBlank() ? null : browserId.strip();
        if (id != null && find(browsers.installed(), id).isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Ce navigateur n'est pas installé sur ce poste : choisissez-en un dans la liste.");
        }
        settings.saveBrowser(id);
        return get();
    }

    /** Ouvre l'adresse dans le navigateur choisi, ou dans celui du système s'il est absent ou échoue. */
    public Opened open(String url) {
        String selected = settings.browser();
        Optional<Browser> browser = selected == null ? Optional.empty() : find(browsers.installed(), selected);
        if (browser.isPresent() && browsers.open(browser.get().id(), url)) {
            return new Opened(true, browser.get());
        }
        return new Opened(browsers.open(null, url), null);
    }

    private static Optional<Browser> find(List<Browser> installed, String id) {
        return installed.stream().filter(b -> b.id().equals(id)).findFirst();
    }
}
