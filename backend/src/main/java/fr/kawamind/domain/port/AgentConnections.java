package fr.kawamind.domain.port;

/**
 * Connexions des agents (015, research R4) : chaque lancement de la passerelle MCP s'annonce par
 * {@code <pid>-<début du processus en ms>}. Une connexion est fermée quand aucun processus du poste
 * n'a ce pid avec ce début.
 */
public interface AgentConnections {

    boolean alive(String connection);
}
