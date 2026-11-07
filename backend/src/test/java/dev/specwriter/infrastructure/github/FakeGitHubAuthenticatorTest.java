package dev.specwriter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.GitHubAuthenticator.State;
import dev.specwriter.support.FixedClock;
import org.junit.jupiter.api.Test;

class FakeGitHubAuthenticatorTest {

    @Test
    void connecteAuSecondPoll() {
        FakeGitHubAuthenticator fake = new FakeGitHubAuthenticator(1234, null, new FixedClock());
        var start = fake.startConnection();
        assertThat(start.authorizationUrl()).isEqualTo("http://127.0.0.1:1234/e2e/fake-github-authorize");
        assertThat(start.requestedScopes()).contains("repo");
        assertThat(fake.poll(start.flowId()).state()).isEqualTo(State.PENDING);
        assertThat(fake.poll(start.flowId()).state()).isEqualTo(State.CONNECTED);
        assertThat(fake.connectedLogin()).contains(FakeGitHubAuthenticator.LOGIN);
        assertThat(fake.listModels()).extracting(m -> m.id()).containsExactly("model-a", "model-b", "model-c");
        assertThat(fake.listModels()).extracting(m -> m.capacity().promptTokens()).containsExactly(null, 32_000, 128_000);
        fake.disconnect();
        assertThat(fake.connectedLogin()).isEmpty();
    }

    @Test
    void scenarioSansCopilot() {
        FakeGitHubAuthenticator fake = new FakeGitHubAuthenticator(1, "no-copilot", new FixedClock());
        var start = fake.startConnection();
        fake.poll(start.flowId());
        assertThat(fake.poll(start.flowId()).state()).isEqualTo(State.NO_COPILOT);
    }
}
