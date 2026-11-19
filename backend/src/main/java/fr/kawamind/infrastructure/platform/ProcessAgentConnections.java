package fr.kawamind.infrastructure.platform;

import fr.kawamind.domain.port.AgentConnections;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;

/**
 * Connexion d'agent vivante (015, research R4) : un processus du poste a le pid annoncé et le même
 * début, à la milliseconde. Un pid réutilisé par un autre programme a un autre début : la connexion
 * est fermée. Un identifiant mal formé est une connexion fermée.
 */
@ApplicationScoped
public class ProcessAgentConnections implements AgentConnections {

    @Override
    public boolean alive(String connection) {
        if (connection == null) {
            return false;
        }
        int dash = connection.indexOf('-');
        if (dash <= 0) {
            return false;
        }
        long pid;
        long started;
        try {
            pid = Long.parseLong(connection.substring(0, dash));
            started = Long.parseLong(connection.substring(dash + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive).flatMap(p -> p.info().startInstant())
                .map(Instant::toEpochMilli).filter(s -> s == started).isPresent();
    }

    /** Identifiant d'un processus : {@code <pid>-<début en ms>}, vide si le système ne donne pas le début. */
    public static Optional<String> identify(ProcessHandle process) {
        return process.info().startInstant().map(s -> process.pid() + "-" + s.toEpochMilli());
    }
}
