package dev.specwriter.domain.port;

import java.util.UUID;

/** Générateur d'identifiants injecté, pour des tests déterministes (principe III). */
public interface IdGenerator {
    UUID next();
}
