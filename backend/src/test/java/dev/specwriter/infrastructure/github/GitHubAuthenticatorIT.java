package dev.specwriter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.GitHubAuthenticator.ConnectionStart;
import dev.specwriter.domain.port.GitHubAuthenticator.ModelOption;
import dev.specwriter.domain.port.GitHubAuthenticator.State;
import dev.specwriter.infrastructure.github.CopilotAccountGateway.LoginResult;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.SequentialIdGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Voie A (spike S1) : runtime Copilot simulé derrière {@link CopilotAccountGateway}. */
class GitHubAuthenticatorIT {

    static final String URL = "https://github.com/login/oauth/authorize?client_id=Ov23&redirect_uri=http%3A%2F%2F127.0.0.1%3A55609%2Fcallback"
            + "&scope=read%3Auser+read%3Aorg+repo+gist&state=s";

    static class FakeGateway implements CopilotAccountGateway {
        CompletableFuture<LoginResult> completion = new CompletableFuture<>();
        final AtomicInteger cancels = new AtomicInteger();
        List<ModelOption> models = List.of(new ModelOption("claude-sonnet-5.5", "Claude Sonnet 5.5", true));
        String user;
        int logouts;

        @Override
        public LoginFlow beginLogin() {
            completion = new CompletableFuture<>();
            return new LoginFlow(URL, completion, cancels::incrementAndGet);
        }

        @Override
        public Optional<String> connectedUser() {
            return Optional.ofNullable(user);
        }

        @Override
        public List<ModelOption> models() {
            return models;
        }

        @Override
        public void logoutAll() {
            logouts++;
            user = null;
        }
    }

    private final FakeGateway gateway = new FakeGateway();
    private final FixedClock clock = new FixedClock();
    private final CopilotCliAuthenticator auth = new CopilotCliAuthenticator(gateway, clock, new SequentialIdGenerator());

    @Test
    void autorisationDansLeNavigateurPuisConnecte() {
        ConnectionStart start = auth.startConnection();
        assertThat(start.authorizationUrl()).isEqualTo(URL);
        assertThat(start.requestedScopes()).containsExactly("read:user", "read:org", "repo", "gist");
        assertThat(start.expiresAt()).isEqualTo(clock.now().plus(Duration.ofMinutes(15)));
        assertThat(auth.poll(start.flowId()).state()).isEqualTo(State.PENDING);

        gateway.completion.complete(new LoginResult(LoginResult.Outcome.COMPLETED, "benjamin-raimondi-prestataire_bscc"));
        var status = auth.poll(start.flowId());
        assertThat(status.state()).isEqualTo(State.CONNECTED);
        assertThat(status.login()).isEqualTo("benjamin-raimondi-prestataire_bscc");
    }

    @Test
    void refusDeLUtilisateur() {
        ConnectionStart start = auth.startConnection();
        gateway.completion.complete(new LoginResult(LoginResult.Outcome.DECLINED, null));
        assertThat(auth.poll(start.flowId()).state()).isEqualTo(State.DENIED);
    }

    @Test
    void erreurDuFlux() {
        ConnectionStart start = auth.startConnection();
        gateway.completion.completeExceptionally(new IllegalStateException("x"));
        assertThat(auth.poll(start.flowId()).state()).isEqualTo(State.ERROR);
    }

    @Test
    void expirationApresQuinzeMinutes() {
        ConnectionStart start = auth.startConnection();
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThat(auth.poll(start.flowId()).state()).isEqualTo(State.EXPIRED);
        assertThat(gateway.cancels.get()).isEqualTo(1);
    }

    @Test
    void compteSansCopilot() {
        gateway.models = List.of();
        ConnectionStart start = auth.startConnection();
        gateway.completion.complete(new LoginResult(LoginResult.Outcome.COMPLETED, "benDeMtp"));
        assertThat(auth.poll(start.flowId()).state()).isEqualTo(State.NO_COPILOT);
    }

    @Test
    void nouvelleConnexionAnnuleLaPrecedente() {
        auth.startConnection();
        auth.startConnection();
        assertThat(gateway.cancels.get()).isEqualTo(1);
    }

    @Test
    void deconnexion() {
        gateway.user = "ada";
        assertThat(auth.connectedLogin()).contains("ada");
        auth.disconnect();
        assertThat(gateway.logouts).isEqualTo(1);
        assertThat(auth.connectedLogin()).isEmpty();
    }

    @Test
    void fluxInconnu() {
        assertThat(auth.poll("inconnu").state()).isEqualTo(State.ERROR);
    }

    @Test
    void permissionsLuesDansLUrl() {
        assertThat(CopilotCliAuthenticator.scopes("https://x/?a=1")).isEmpty();
        assertThat(new ArrayList<>(CopilotCliAuthenticator.scopes(URL))).contains("repo");
    }
}
