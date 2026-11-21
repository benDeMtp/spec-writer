package fr.kawamind.infrastructure.platform;

import fr.kawamind.application.lifecycle.ExitPort;
import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;

/** Arrêt du processus par Quarkus : les observateurs de {@code ShutdownEvent} passent (019, R4). */
@ApplicationScoped
public class QuarkusExit implements ExitPort {

    @Override
    public void exit(int code) {
        Quarkus.asyncExit(code);
    }
}
