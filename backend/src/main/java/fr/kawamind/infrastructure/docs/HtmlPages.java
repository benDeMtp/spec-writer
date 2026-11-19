package fr.kawamind.infrastructure.docs;

import fr.kawamind.domain.port.DocumentationReader.Page;
import java.time.Instant;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * Page HTML rendue par un autre outil que Antora (016 : contenu {@code body.view} de Confluence) :
 * texte structuré, puis sections coupées à ses titres comme un document (006). Le contenu reste une
 * donnée : il n'est ni exécuté ni journalisé.
 */
public final class HtmlPages {

    private HtmlPages() {
    }

    public static Page of(String ref, String title, String html, Instant updatedAt) {
        Element body = Jsoup.parseBodyFragment(html == null ? "" : html).body();
        String text = StructuredText.of(body);
        return new Page(ref, title, text, Sections.ofText(ref, title, text), updatedAt);
    }
}
