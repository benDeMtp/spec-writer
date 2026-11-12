package dev.specwriter.domain.port;

import dev.specwriter.domain.settings.Browser;
import java.util.List;

/** Navigateurs du poste (013, T057) : ceux qui sont installés, et l'ouverture d'une adresse. */
public interface BrowserPort {

    /** Navigateurs détectés, dans un ordre stable ; aucune commande n'est lancée pour les trouver. */
    List<Browser> installed();

    /**
     * Ouvre l'adresse dans le navigateur {@code browserId}, ou dans celui du système si
     * {@code browserId} est {@code null}.
     *
     * @return {@code true} si le navigateur a été lancé
     */
    boolean open(String browserId, String url);
}
