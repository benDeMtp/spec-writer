package dev.specwriter.domain.port;

/**
 * Point d'entrée des agents (research.md R2) : un fichier {@code {port, secret}} lisible par le seul
 * utilisateur, que la passerelle MCP lit. Le secret n'est jamais rendu à l'interface.
 */
public interface AgentEndpoint {

    /** Écrit le fichier ; garde le secret existant, ou en tire un s'il n'y en a pas. */
    void publish();

    /** Supprime le fichier et oublie le secret : les agents sont coupés. */
    void withdraw();

    /** Nouveau secret, réécrit dans le fichier : les accès avec l'ancien sont coupés. */
    void regenerate();
}
