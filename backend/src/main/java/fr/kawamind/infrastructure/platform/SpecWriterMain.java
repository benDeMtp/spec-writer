package fr.kawamind.infrastructure.platform;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Point d'entrée de l'outil (research.md R3). Le jar, les installeurs et le dev mode passent par
 * ce {@code main} ; le dev mode le rappelle à chaque rechargement à chaud.
 */
@QuarkusMain
public final class SpecWriterMain {

    private SpecWriterMain() {
    }

    public static void main(String... args) {
        Quarkus.run(args);
    }
}
