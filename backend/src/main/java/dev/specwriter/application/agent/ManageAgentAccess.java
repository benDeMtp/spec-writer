package dev.specwriter.application.agent;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.AgentAccessRepository;
import dev.specwriter.domain.port.AgentEndpoint;
import dev.specwriter.domain.port.ClockPort;
import java.time.Duration;
import java.util.List;

/**
 * Accès des agents depuis la configuration (FR-024, FR-028) : activation, régénération du secret,
 * journal paginé. Au démarrage, le fichier de la passerelle est réécrit et le journal purgé.
 */
public class ManageAgentAccess {

    public static final int PAGE_SIZE = 25;
    static final Duration RETENTION = Duration.ofDays(90);

    public record LogPage(int startAt, int total, List<AgentAccessRepository.Line> entries) {
    }

    private final AgentAccessRepository repository;
    private final AgentEndpoint endpoint;
    private final ClockPort clock;

    public ManageAgentAccess(AgentAccessRepository repository, AgentEndpoint endpoint, ClockPort clock) {
        this.repository = repository;
        this.endpoint = endpoint;
        this.clock = clock;
    }

    public boolean enabled() {
        return repository.enabled();
    }

    public void setEnabled(boolean enabled) {
        repository.setEnabled(enabled);
        if (enabled) {
            endpoint.publish();
        } else {
            endpoint.withdraw();
        }
    }

    public void regenerate() {
        if (!repository.enabled()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Activez d'abord l'accès des agents.");
        }
        endpoint.regenerate();
    }

    public LogPage log(int startAt) {
        if (startAt < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La position de la page doit être positive.");
        }
        return new LogPage(startAt, repository.count(), repository.page(startAt, PAGE_SIZE));
    }

    /** Au démarrage : fichier réécrit si l'accès est activé (le port a pu changer), journal purgé (R11). */
    public void startup() {
        repository.purgeBefore(clock.now().minus(RETENTION));
        if (repository.enabled()) {
            endpoint.publish();
        }
    }
}
