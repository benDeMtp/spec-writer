package fr.kawamind.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Lecture de Confluence Server / Data Center (016, contracts/confluence-client.md) : lecture seule,
 * jeton personnel, débit borné. Aucun contenu de page n'est journalisé ni conservé hors de l'index.
 */
public interface ConfluenceClient {

    /** Serveur et jeton ; le jeton n'apparaît jamais dans {@link #toString()}. */
    record Endpoint(String serverUrl, String token, String caCertificatePath) {

        @Override
        public String toString() {
            return "Endpoint[" + serverUrl + "]";
        }
    }

    /** Page : identifiant, titre, numéro de version et date de cette version. */
    record PageInfo(String id, String title, int version, Instant updatedAt) {
    }

    /** Une page de la descendance et le nombre total annoncé. */
    record Descendants(List<PageInfo> pages, int total) {

        public Descendants {
            pages = List.copyOf(pages);
        }
    }

    enum Failure {
        UNREACHABLE, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, RATE_LIMITED, TLS_ERROR, NOT_CONFLUENCE, REJECTED,
        /** Adresse d'un autre hôte que le serveur déclaré. */
        OTHER_HOST,
        /** Adresse qui ne désigne pas une page. */
        NOT_A_PAGE
    }

    /** Échec d'un appel, sans contenu ni jeton dans son message. */
    final class ConfluenceException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final Failure failure;
        private final Integer status;

        public ConfluenceException(Failure failure, Integer status, Throwable cause) {
            super("Confluence : " + failure + (status == null ? "" : " (HTTP " + status + ")"), cause);
            this.failure = failure;
            this.status = status;
        }

        public ConfluenceException(Failure failure) {
            this(failure, null, null);
        }

        public Failure failure() {
            return failure;
        }

        public Integer status() {
            return status;
        }
    }

    /** Nom du compte du jeton ({@code 401} : {@link Failure#UNAUTHORIZED}). */
    String currentUser(Endpoint endpoint);

    /** Version du serveur, si elle est lisible. */
    Optional<String> serverVersion(Endpoint endpoint);

    /**
     * Identifiant de la page désignée par une adresse (trois formes, même hôte que le serveur) ;
     * {@link Failure#OTHER_HOST} ou {@link Failure#NOT_A_PAGE} sinon.
     */
    String resolve(Endpoint endpoint, String address);

    PageInfo page(Endpoint endpoint, String id);

    /** Descendance d'une page à toute profondeur, à partir de {@code start}. */
    Descendants descendants(Endpoint endpoint, String id, int start, int limit);

    /** Contenu rendu d'une page, découpé en sections indexables sous la référence {@code ref}. */
    DocumentationReader.Page content(Endpoint endpoint, String id, String ref);

    /** Identifiant de page d'une référence indexée ({@code …pageId=<n>…}), s'il y en a un. */
    static Optional<String> pageId(String ref) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]pageId=(\\d+)").matcher(ref);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Référence indexée d'une page : son adresse canonique (R3). */
    static String pageRef(String serverUrl, String id) {
        return serverUrl + "/pages/viewpage.action?pageId=" + id;
    }
}
