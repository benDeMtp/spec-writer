package dev.specwriter.infrastructure.platform;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Client HTTP qui passe par le proxy de {@link ProxySettings}, jamais par celui des propriétés du JDK (T139 de 001). */
public final class ProxiedHttp {

    private ProxiedHttp() {
    }

    public static HttpClient.Builder builder(ProxySettings proxy) {
        HttpClient.Builder builder = HttpClient.newBuilder().proxy(new SettingsProxySelector(proxy));
        authenticator(proxy).ifPresent(builder::authenticator);
        return builder;
    }

    private static final class SettingsProxySelector extends ProxySelector {
        private final ProxySettings settings;

        SettingsProxySelector(ProxySettings settings) {
            this.settings = settings;
        }

        @Override
        public List<Proxy> select(URI uri) {
            return settings.forUri(uri)
                    .map(p -> List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(p.host(), p.port()))))
                    .orElse(List.of(Proxy.NO_PROXY));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // rien à mémoriser : l'appelant signale la ressource injoignable
        }
    }

    private static Optional<Authenticator> authenticator(ProxySettings settings) {
        return Stream.of(URI.create("https://proxy.invalid"), URI.create("http://proxy.invalid"))
                .map(settings::forUri).flatMap(Optional::stream).filter(p -> p.user() != null).findFirst()
                .map(p -> new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return getRequestorType() == RequestorType.PROXY
                                ? new PasswordAuthentication(p.user(), (p.password() == null ? "" : p.password()).toCharArray())
                                : null;
                    }
                });
    }
}
