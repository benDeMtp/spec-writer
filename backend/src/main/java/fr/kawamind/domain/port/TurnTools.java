package fr.kawamind.domain.port;

import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.conversation.TurnSource;
import java.util.List;
import java.util.Set;

/**
 * Outils d'un tour de l'agent conversationnel (014, research R5), fournis par l'application : la
 * logique du tour (sources numérotées en continu, étapes enregistrées et publiées, plafond, arrêt)
 * reste dans l'application, la boucle d'outils dans l'adaptateur.
 */
public interface TurnTools {

    /**
     * Sources d'une recherche, numérotées dans la continuité du tour ; {@code notCompared} : titres
     * des sources écartées faute de place.
     */
    record Found(List<TurnSource> sources, List<String> notCompared) {

        public Found {
            sources = List.copyOf(sources);
            notCompared = List.copyOf(notCompared);
        }
    }

    /** Analyse de périmètre : modules devenus sources du tour, ou indisponible faute de racine de sources. */
    record PerimeterOutcome(boolean unavailable, List<TurnSource> modules) {

        public PerimeterOutcome {
            modules = List.copyOf(modules);
        }
    }

    /** Recherche dans la connaissance du projet ; l'étape est enregistrée et publiée. */
    Found search(String query, Set<SourceKind> kinds);

    /** Analyse de périmètre dans les sources du code (013) ; une au plus par tour. */
    PerimeterOutcome perimeter(List<String> terms);

    /** Recherches et analyses encore possibles dans ce tour (plafond 5). */
    int stepsLeft();

    /** Une analyse de périmètre a déjà été faite dans ce tour. */
    boolean perimeterDone();

    /** L'utilisateur a arrêté le tour. */
    boolean cancelled();
}
