package dev.specwriter.infrastructure.github;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.generated.rpc.AccountAllUsers;
import com.github.copilot.generated.rpc.AccountLogoutParams;
import com.github.copilot.generated.rpc.AuthLoginResultDto;
import com.github.copilot.generated.rpc.AuthLoginResultStatus;
import com.github.copilot.generated.rpc.AuthLoginStep;
import com.github.copilot.generated.rpc.AuthLoginStepCompleted;
import com.github.copilot.generated.rpc.AuthLoginStepError;
import com.github.copilot.generated.rpc.AuthLoginStepOpenUrl;
import com.github.copilot.generated.rpc.LoginProviderKind;
import com.github.copilot.generated.rpc.SessionAccountsLoginAdvanceParams;
import com.github.copilot.generated.rpc.SessionAccountsLoginBeginParams;
import com.github.copilot.generated.rpc.SessionAccountsLoginBeginResult;
import com.github.copilot.generated.rpc.SessionAccountsLoginCancelParams;
import com.github.copilot.rpc.GetAuthStatusResponse;
import com.github.copilot.rpc.ModelInfo;
import com.github.copilot.rpc.ModelLimits;
import dev.specwriter.domain.port.GitHubAuthenticator.ModelOption;
import dev.specwriter.infrastructure.llm.CopilotRuntime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.logging.Logger;

/**
 * Opérations de compte sur le vrai runtime Copilot (spike S1). Les messages d'erreur du SDK ne sont
 * pas journalisés : seule leur classe l'est.
 */
public class SdkCopilotAccountGateway implements CopilotAccountGateway {

    private static final Logger LOG = Logger.getLogger(SdkCopilotAccountGateway.class);
    private static final long POLL_MILLIS = 2000;

    private final CopilotRuntime runtime;

    public SdkCopilotAccountGateway(CopilotRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public LoginFlow beginLogin() {
        CopilotClient client = runtime.client();
        CopilotSession session = await(client.createSession(CopilotRuntime.toolLessSession(null, null)), 60);
        try {
            SessionAccountsLoginBeginResult begin = await(session.getRpc().accounts.login
                    .begin(new SessionAccountsLoginBeginParams(null, LoginProviderKind.GITHUBDOTCOM)), 60);
            String url = openUrl(begin.step());
            AtomicBoolean cancelled = new AtomicBoolean();
            CompletableFuture<LoginResult> completion = CompletableFuture
                    .supplyAsync(() -> advanceUntilDone(session, begin.flowId(), cancelled))
                    .whenComplete((r, e) -> closeAndDelete(client, session))
                    .thenApply(result -> {
                        if (result.outcome() == LoginResult.Outcome.COMPLETED) {
                            runtime.restart();
                        }
                        return result;
                    });
            Runnable cancel = () -> {
                cancelled.set(true);
                session.getRpc().accounts.login.cancel(new SessionAccountsLoginCancelParams(null, begin.flowId()));
            };
            return new LoginFlow(url, completion, cancel);
        } catch (RuntimeException e) {
            closeAndDelete(client, session);
            throw e;
        }
    }

    private static LoginResult advanceUntilDone(CopilotSession session, String flowId, AtomicBoolean cancelled) {
        while (!cancelled.get()) {
            AuthLoginStep step = await(session.getRpc().accounts.login
                    .advance(new SessionAccountsLoginAdvanceParams(null, flowId, null)), 16 * 60);
            if (step instanceof AuthLoginStepCompleted completed) {
                AuthLoginResultDto result = completed.getResult();
                if (result == null || result.status() == AuthLoginResultStatus.DECLINED) {
                    return new LoginResult(LoginResult.Outcome.DECLINED, null);
                }
                return result.status() == AuthLoginResultStatus.COMPLETED
                        ? new LoginResult(LoginResult.Outcome.COMPLETED, result.login())
                        : new LoginResult(LoginResult.Outcome.ERROR, null);
            }
            if (step instanceof AuthLoginStepError) {
                return new LoginResult(LoginResult.Outcome.ERROR, null);
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new LoginResult(LoginResult.Outcome.ERROR, null);
            }
        }
        return new LoginResult(LoginResult.Outcome.ERROR, null);
    }

    /** L'étape initiale arrive typée ou en Map selon la désérialisation du SDK. */
    static String openUrl(Object step) {
        if (step instanceof AuthLoginStepOpenUrl open) {
            return open.getUrl();
        }
        if (step instanceof Map<?, ?> map && "open-url".equals(map.get("kind")) && map.get("url") instanceof String url) {
            return url;
        }
        throw CopilotRuntime.classify(new IllegalStateException("étape de connexion inattendue"));
    }

    @Override
    public Optional<String> connectedUser() {
        GetAuthStatusResponse status = await(runtime.client().getAuthStatus(), 30);
        // La CLI gh (type gh-cli) n'est pas une connexion faite dans l'outil (spike S1)
        if (status.isAuthenticated() && "user".equals(status.getAuthType())) {
            return Optional.ofNullable(status.getLogin());
        }
        return Optional.empty();
    }

    @Override
    public List<ModelOption> models() {
        List<ModelInfo> models = await(runtime.client().listModels(), 60);
        return models.stream().map(SdkCopilotAccountGateway::option).toList();
    }

    /** Limites et coefficient lus sans planter s'ils manquent : inconnus (008, research.md R1). */
    static ModelOption option(ModelInfo m) {
        boolean enabled = m.getPolicy() == null || m.getPolicy().getState() == null
                || "enabled".equalsIgnoreCase(m.getPolicy().getState());
        ModelLimits limits = m.getCapabilities() == null ? null : m.getCapabilities().getLimits();
        Integer prompt = limits == null ? null : limits.getMaxPromptTokens();
        Integer context = limits == null || limits.getMaxContextWindowTokens() <= 0 ? null
                : limits.getMaxContextWindowTokens();
        OptionalDouble multiplier = m.getBilling() == null ? OptionalDouble.empty() : m.getBilling().getMultiplierOpt();
        return new ModelOption(m.getId(), m.getName(), enabled, prompt, context,
                multiplier.isPresent() ? multiplier.getAsDouble() : null);
    }

    @Override
    public void logoutAll() {
        CopilotClient client = runtime.client();
        List<AccountAllUsers> users = await(client.getRpc().account.getAllUsers(), 30);
        for (AccountAllUsers user : users) {
            await(client.getRpc().account.logout(new AccountLogoutParams(user.selectionId(), user.authInfo())), 30);
        }
    }

    private static void closeAndDelete(CopilotClient client, CopilotSession session) {
        String id = session.getSessionId();
        try {
            session.close();
            client.deleteSession(id).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warnf("Session de connexion Copilot non supprimée (%s)", e.getClass().getSimpleName());
        }
    }

    private static <T> T await(CompletableFuture<T> future, long seconds) {
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw CopilotRuntime.classify(e);
        }
    }
}
