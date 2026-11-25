package fr.kawamind.application.lifecycle;

/** Arrêt du processus de l'outil (019, research.md R4) ; simulé en test. */
@FunctionalInterface
public interface ExitPort {

    void exit(int code);
}
