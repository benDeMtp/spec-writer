package dev.specwriter.infrastructure.github;

import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.GitHubAuthenticator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GitHub simulé pour les tests E2E (tâche T033), sélectionné uniquement si
 * {@code specwriter.github.fake=true} ; StartupGuard interdit ce mode hors des profils test et e2e.
 */
public class FakeGitHubAuthenticator implements GitHubAuthenticator {

    public static final String LOGIN = "e2e-user";

    private final int port;
    private final boolean noCopilot;
    private final ClockPort clock;
    private final Map<String, AtomicInteger> polls = new ConcurrentHashMap<>();
    private volatile String connected;

    public FakeGitHubAuthenticator(int port, String scenario, ClockPort clock) {
        this.port = port;
        this.noCopilot = "no-copilot".equals(scenario);
        this.clock = clock;
    }

    @Override
    public ConnectionStart startConnection() {
        String flowId = "fake-flow-" + (polls.size() + 1);
        polls.put(flowId, new AtomicInteger());
        return new ConnectionStart(flowId, "http://127.0.0.1:" + port + "/e2e/fake-github-authorize",
                List.of("read:user", "read:org", "repo", "gist", "codespace", "write:plugin_gateway_connections"),
                clock.now().plus(Duration.ofMinutes(15)));
    }

    @Override
    public ConnectionStatus poll(String flowId) {
        AtomicInteger count = polls.get(flowId);
        if (count == null) {
            return new ConnectionStatus(State.ERROR, null);
        }
        if (count.incrementAndGet() < 2) {
            return new ConnectionStatus(State.PENDING, null);
        }
        if (noCopilot) {
            return new ConnectionStatus(State.NO_COPILOT, LOGIN);
        }
        connected = LOGIN;
        return new ConnectionStatus(State.CONNECTED, LOGIN);
    }

    @Override
    public void disconnect() {
        connected = null;
    }

    @Override
    public Optional<String> connectedLogin() {
        return Optional.ofNullable(connected);
    }

    @Override
    public List<ModelOption> listModels() {
        // 008 : un modèle sans limite connue (choisi par défaut), un petit et un grand
        return List.of(new ModelOption("model-a", "Model A", true),
                new ModelOption("model-b", "Model B", true, 32_000, 64_000, 0.33),
                new ModelOption("model-c", "Grand modèle", true, 128_000, 200_000, 1.0));
    }
}
