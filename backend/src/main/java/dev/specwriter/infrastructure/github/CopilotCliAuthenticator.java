package dev.specwriter.infrastructure.github;

import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.GitHubAuthenticator;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.infrastructure.github.CopilotAccountGateway.LoginFlow;
import dev.specwriter.infrastructure.github.CopilotAccountGateway.LoginResult;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connexion GitHub Copilot par le runtime Copilot (voie A retenue au spike S1, research.md R4) :
 * autorisation OAuth dans le navigateur, retour automatique, jeton gardé par le runtime dans le
 * trousseau du système. spec-writer ne voit jamais le jeton.
 */
public class CopilotCliAuthenticator implements GitHubAuthenticator {

    static final Duration FLOW_TIMEOUT = Duration.ofMinutes(15);

    private record Pending(LoginFlow flow, Instant expiresAt) {
    }

    private final CopilotAccountGateway gateway;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final Map<String, Pending> flows = new ConcurrentHashMap<>();

    public CopilotCliAuthenticator(CopilotAccountGateway gateway, ClockPort clock, IdGenerator ids) {
        this.gateway = gateway;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public ConnectionStart startConnection() {
        flows.values().forEach(p -> p.flow().cancel().run());
        flows.clear();
        LoginFlow flow = gateway.beginLogin();
        String flowId = ids.next().toString();
        Instant expires = clock.now().plus(FLOW_TIMEOUT);
        flows.put(flowId, new Pending(flow, expires));
        return new ConnectionStart(flowId, flow.authorizationUrl(), scopes(flow.authorizationUrl()), expires);
    }

    @Override
    public ConnectionStatus poll(String flowId) {
        Pending pending = flows.get(flowId);
        if (pending == null) {
            return new ConnectionStatus(State.ERROR, null);
        }
        if (!pending.flow().completion().isDone()) {
            if (clock.now().isAfter(pending.expiresAt())) {
                pending.flow().cancel().run();
                flows.remove(flowId);
                return new ConnectionStatus(State.EXPIRED, null);
            }
            return new ConnectionStatus(State.PENDING, null);
        }
        flows.remove(flowId);
        LoginResult result;
        try {
            result = pending.flow().completion().join();
        } catch (RuntimeException e) {
            return new ConnectionStatus(State.ERROR, null);
        }
        return switch (result.outcome()) {
            case DECLINED -> new ConnectionStatus(State.DENIED, null);
            case ERROR -> new ConnectionStatus(State.ERROR, null);
            case COMPLETED -> gateway.models().isEmpty()
                    ? new ConnectionStatus(State.NO_COPILOT, result.login())
                    : new ConnectionStatus(State.CONNECTED, result.login());
        };
    }

    @Override
    public void disconnect() {
        gateway.logoutAll();
    }

    @Override
    public Optional<String> connectedLogin() {
        return gateway.connectedUser();
    }

    @Override
    public List<ModelOption> listModels() {
        return gateway.models();
    }

    /** Permissions demandées, lues dans l'URL d'autorisation pour les afficher avant redirection (FR-030a). */
    static List<String> scopes(String authorizationUrl) {
        String query = URI.create(authorizationUrl).getRawQuery();
        if (query == null) {
            return List.of();
        }
        for (String part : query.split("&")) {
            if (part.startsWith("scope=")) {
                String value = URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
                return Arrays.stream(value.split("[ ,]+")).filter(s -> !s.isBlank()).toList();
            }
        }
        return List.of();
    }
}
