package dev.specwriter.infrastructure.platform;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Proxy d'entreprise (T139). Le client REST de Quarkus (Vert.x) ignore les propriétés
 * {@code https.proxyHost} de Java : elles sont donc lues ici, avant les variables
 * {@code HTTPS_PROXY}, {@code HTTP_PROXY} et {@code NO_PROXY}. La boucle locale ne passe jamais par
 * le proxy. Le mot de passe éventuel n'apparaît dans aucun {@code toString}.
 */
public final class ProxySettings {

    private final Optional<Proxy> https;
    private final Optional<Proxy> http;
    /** Exclusions issues de {@code http.nonProxyHosts}, transmises au Copilot CLI. */
    private final List<String> javaExclusions;
    private final List<String> exclusions;

    private ProxySettings(Optional<Proxy> https, Optional<Proxy> http, List<String> javaExclusions, List<String> envExclusions) {
        this.https = https;
        this.http = http;
        this.javaExclusions = List.copyOf(javaExclusions);
        List<String> all = new ArrayList<>(javaExclusions);
        all.addAll(envExclusions);
        this.exclusions = List.copyOf(all);
    }

    public static ProxySettings fromSystem() {
        return from(System.getenv(), System.getProperties());
    }

    public static ProxySettings from(Map<String, String> env, Properties props) {
        Optional<Proxy> https = fromProperties(props, "https", 443).or(() -> fromUrl(env(env, "HTTPS_PROXY")));
        Optional<Proxy> http = fromProperties(props, "http", 80).or(() -> fromUrl(env(env, "HTTP_PROXY")));
        List<String> java = split(props.getProperty("http.nonProxyHosts"), "\\|");
        List<String> fromEnv = split(env(env, "NO_PROXY"), ",");
        return new ProxySettings(https, http, java.stream().map(ProxySettings::normalize).toList(),
                fromEnv.stream().map(ProxySettings::normalize).toList());
    }

    /** Proxy à utiliser pour joindre {@code uri}, ou vide pour une connexion directe. */
    public Optional<Proxy> forUri(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (isLoopback(host) || excluded(host)) {
            return Optional.empty();
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? http : https;
    }

    /**
     * Environnement du Copilot CLI lancé par le SDK : les variables déjà présentes sont gardées, celles
     * qui manquent sont déduites des propriétés Java.
     */
    public Map<String, String> childEnvironment(Map<String, String> parent) {
        Map<String, String> env = new HashMap<>(parent);
        https.ifPresent(p -> putIfMissing(env, "HTTPS_PROXY", p.url()));
        http.ifPresent(p -> putIfMissing(env, "HTTP_PROXY", p.url()));
        if (!javaExclusions.isEmpty()) {
            putIfMissing(env, "NO_PROXY", String.join(",", javaExclusions));
        }
        return env;
    }

    public boolean isEmpty() {
        return https.isEmpty() && http.isEmpty();
    }

    /** Pour le journal : hôtes et ports seulement. */
    public String describe() {
        return "https=" + https.map(Proxy::toString).orElse("direct") + ", http=" + http.map(Proxy::toString).orElse("direct");
    }

    private boolean excluded(String host) {
        for (String rule : exclusions) {
            if ("*".equals(rule)) {
                return true;
            }
            String domain = rule.startsWith(".") ? rule.substring(1) : rule;
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host) || host.startsWith("127.") || "::1".equals(host) || "[::1]".equals(host);
    }

    /** {@code *.exemple.fr}, {@code .exemple.fr} et {@code exemple.fr:443} visent le même domaine. */
    private static String normalize(String rule) {
        String r = rule.strip().toLowerCase(Locale.ROOT);
        if (r.startsWith("*.")) {
            r = r.substring(1);
        }
        int colon = r.lastIndexOf(':');
        if (colon > 0 && r.indexOf(':') == colon && r.substring(colon + 1).chars().allMatch(Character::isDigit)) {
            r = r.substring(0, colon);
        }
        return r;
    }

    private static Optional<Proxy> fromProperties(Properties props, String scheme, int defaultPort) {
        String host = props.getProperty(scheme + ".proxyHost");
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        String port = props.getProperty(scheme + ".proxyPort");
        try {
            return Optional.of(new Proxy(host.strip(), port == null || port.isBlank() ? defaultPort : Integer.parseInt(port.strip()),
                    null, null));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Proxy> fromUrl(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String v = value.strip();
        try {
            URI uri = new URI(v.contains("://") ? v : "http://" + v);
            if (uri.getHost() == null) {
                return Optional.empty();
            }
            int port = uri.getPort() > 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            String user = null;
            String password = null;
            if (uri.getUserInfo() != null) {
                String[] parts = uri.getUserInfo().split(":", 2);
                user = parts[0];
                password = parts.length > 1 ? parts[1] : null;
            }
            return Optional.of(new Proxy(uri.getHost(), port, user, password));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static String env(Map<String, String> env, String name) {
        String upper = env.get(name);
        return upper != null ? upper : env.get(name.toLowerCase(Locale.ROOT));
    }

    private static List<String> split(String value, String separator) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(separator)).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static void putIfMissing(Map<String, String> env, String name, String value) {
        if (env(env, name) == null) {
            env.put(name, value);
        }
    }

    /** Proxy HTTP(S) ; {@code user} et {@code password} sont facultatifs. */
    public record Proxy(String host, int port, String user, String password) {

        String url() {
            String credentials = user == null ? ""
                    : encode(user) + (password == null ? "" : ":" + encode(password)) + "@";
            return "http://" + credentials + host + ":" + port;
        }

        private static String encode(String value) {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return host + ":" + port + (user == null ? "" : " (authentifié)");
        }
    }
}
