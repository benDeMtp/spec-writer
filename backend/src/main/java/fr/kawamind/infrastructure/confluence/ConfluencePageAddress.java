package fr.kawamind.infrastructure.confluence;

import fr.kawamind.domain.port.ConfluenceClient.ConfluenceException;
import fr.kawamind.domain.port.ConfluenceClient.Failure;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adresse d'une page Confluence saisie par l'utilisateur (016, contracts/confluence-client.md) :
 * {@code …/pages/viewpage.action?pageId=123}, {@code …/spaces/KEY/pages/123/Titre} ou
 * {@code …/display/KEY/Titre+de+la+page}, sur le serveur déclaré (chemin de contexte compris).
 * La dernière forme se résout par le titre.
 */
record ConfluencePageAddress(String pageId, String spaceKey, String title) {

    private static final Pattern PAGE_ID = Pattern.compile("(?:^|&)pageId=(\\d+)(?:&|$)");
    private static final Pattern SPACES = Pattern.compile("^/spaces/[^/]+/pages/(\\d+)(?:/.*)?$");
    private static final Pattern DISPLAY = Pattern.compile("^/display/([^/]+)/([^/]+)/?$");

    boolean byTitle() {
        return pageId == null;
    }

    static ConfluencePageAddress parse(String serverUrl, String address) {
        URI server = uri(serverUrl);
        URI page = uri(address == null ? "" : address.strip());
        if (server == null || page == null || page.getHost() == null) {
            throw new ConfluenceException(Failure.NOT_A_PAGE);
        }
        if (!sameHost(server, page)) {
            throw new ConfluenceException(Failure.OTHER_HOST);
        }
        String context = server.getRawPath() == null ? "" : server.getRawPath().replaceAll("/+$", "");
        String path = page.getRawPath() == null ? "" : page.getRawPath();
        if (!path.startsWith(context + "/")) {
            throw new ConfluenceException(Failure.OTHER_HOST);
        }
        String rest = path.substring(context.length());
        if (rest.equals("/pages/viewpage.action") && page.getRawQuery() != null) {
            Matcher m = PAGE_ID.matcher(page.getRawQuery());
            if (m.find()) {
                return new ConfluencePageAddress(m.group(1), null, null);
            }
        }
        Matcher spaces = SPACES.matcher(rest);
        if (spaces.matches()) {
            return new ConfluencePageAddress(spaces.group(1), null, null);
        }
        Matcher display = DISPLAY.matcher(rest);
        if (display.matches()) {
            String title = URLDecoder.decode(display.group(2), StandardCharsets.UTF_8).strip();
            if (!title.isEmpty()) {
                return new ConfluencePageAddress(null, URLDecoder.decode(display.group(1), StandardCharsets.UTF_8),
                        title);
            }
        }
        throw new ConfluenceException(Failure.NOT_A_PAGE);
    }

    private static URI uri(String raw) {
        try {
            return URI.create(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean sameHost(URI a, URI b) {
        String sa = a.getScheme() == null ? "" : a.getScheme().toLowerCase(Locale.ROOT);
        String sb = b.getScheme() == null ? "" : b.getScheme().toLowerCase(Locale.ROOT);
        return a.getHost().equalsIgnoreCase(b.getHost()) && port(sa, a.getPort()) == port(sb, b.getPort())
                && sa.equals(sb);
    }

    private static int port(String scheme, int port) {
        return port != -1 ? port : "https".equals(scheme) ? 443 : 80;
    }
}
