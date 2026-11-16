package fr.kawamind.infrastructure.github;

import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.GitHubAuthenticator;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.infrastructure.llm.CopilotRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** GitHub simulé seulement si {@code specwriter.github.fake=true} (StartupGuard le limite aux tests). */
@ApplicationScoped
public class GitHubAuthenticatorProducer {

    @ConfigProperty(name = "specwriter.github.fake", defaultValue = "false")
    boolean fake;

    @ConfigProperty(name = "specwriter.github.fake-scenario", defaultValue = "ok")
    String scenario;

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "47615")
    int port;

    @Produces
    @ApplicationScoped
    GitHubAuthenticator authenticator(CopilotRuntime runtime, ClockPort clock, IdGenerator ids) {
        if (fake) {
            return new FakeGitHubAuthenticator(port, scenario, clock);
        }
        return new CopilotCliAuthenticator(new SdkCopilotAccountGateway(runtime), clock, ids);
    }
}
