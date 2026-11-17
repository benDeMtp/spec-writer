package fr.kawamind.application.settings;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.ConfluenceClient.ConfluenceException;
import fr.kawamind.domain.port.ConfluenceSettingsRepository;
import fr.kawamind.domain.port.SecretStore;
import fr.kawamind.domain.settings.ConfluenceSettings;
import fr.kawamind.domain.settings.ConfluenceTestResult;
import java.util.Optional;

/**
 * Test de connexion à Confluence (016, contracts/rest-api.md) : compte du jeton, puis version du
 * serveur si elle est lisible. Rien n'est envoyé à Confluence : lecture seule, aucun accord demandé.
 */
public class TestConfluenceConnection {

    public record Outcome(ConfluenceTestResult result, String account, String serverVersion, String message) {

        static Outcome of(ConfluenceTestResult result, String account, String version) {
            return new Outcome(result, account, version, result.userMessage());
        }
    }

    private final ConfluenceClient client;
    private final ConfluenceSettingsRepository settings;
    private final SecretStore secrets;
    private final ClockPort clock;

    public TestConfluenceConnection(ConfluenceClient client, ConfluenceSettingsRepository settings,
            SecretStore secrets, ClockPort clock) {
        this.client = client;
        this.settings = settings;
        this.secrets = secrets;
        this.clock = clock;
    }

    public Outcome run() {
        ConfluenceSettings confluence = settings.find()
                .orElseThrow(() -> new DomainException(ErrorCode.CONFLUENCE_NOT_CONFIGURED));
        Optional<String> token = secrets.get(SecretStore.CONFLUENCE_PAT);
        Outcome outcome = token.isEmpty() ? Outcome.of(ConfluenceTestResult.UNAUTHORIZED, null, null)
                : check(new ConfluenceClient.Endpoint(confluence.serverUrl(), token.get(),
                        confluence.caCertificatePath()));
        settings.save(confluence.withTest(clock.now(), outcome.result(), outcome.serverVersion()));
        return outcome;
    }

    private Outcome check(ConfluenceClient.Endpoint endpoint) {
        try {
            String account = client.currentUser(endpoint);
            String version = client.serverVersion(endpoint).orElse(null);
            return Outcome.of(ConfluenceTestResult.OK, account, version);
        } catch (ConfluenceException e) {
            ConfluenceTestResult result = switch (e.failure()) {
                case UNAUTHORIZED, FORBIDDEN -> ConfluenceTestResult.UNAUTHORIZED;
                case TLS_ERROR -> ConfluenceTestResult.TLS_ERROR;
                case NOT_CONFLUENCE, NOT_FOUND, REJECTED, OTHER_HOST, NOT_A_PAGE -> ConfluenceTestResult.NOT_CONFLUENCE;
                case UNREACHABLE, RATE_LIMITED -> ConfluenceTestResult.UNREACHABLE;
            };
            return Outcome.of(result, null, null);
        }
    }
}
