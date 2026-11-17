package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.port.TurnTools;
import fr.kawamind.infrastructure.llm.UserMessages;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Outils de l'agent conversationnel (014, contracts/agent-tools.md). Chaque outil appelle le
 * {@link TurnTools} du tour, transmis par {@link InvocationParameters}, et rend au modèle des données
 * balisées et échappées. Le journal ne garde que le nom de l'outil et sa durée, jamais la requête.
 */
@ApplicationScoped
public class AgentTools {

    private static final Logger LOG = Logger.getLogger(AgentTools.class);
    /** Clé du {@link TurnTools} du tour dans les paramètres d'invocation. */
    public static final String TURN = "turn";
    /**
     * Tour en cours sur ce fil, fixé par l'adaptateur. En flux, quarkus-langchain4j 2.0.0.Beta1 ne
     * transmet pas les paramètres d'invocation aux outils (research R5) ; les outils s'exécutent sur
     * le fil de l'appel, qui porte donc le tour.
     */
    public static final ScopedValue<TurnTools> CURRENT = ScopedValue.newInstance();
    static final String FAILED = "l'outil a échoué ; réponds avec ce que tu as";
    static final int MAX_QUERY = 200;
    static final int MAX_TERMS = 12;
    static final int MIN_TERM = 2;
    static final int MAX_TERM = 80;
    /** Sortes que la recherche sait trouver ; les pièces jointes et la discussion arrivent autrement. */
    static final Set<SourceKind> SEARCHABLE = EnumSet.of(SourceKind.TICKET, SourceKind.DOC_SECTION, SourceKind.ADR,
            SourceKind.ARCH_ELEMENT, SourceKind.SPECIFICATION);

    @Tool(name = "search_knowledge", value = "Recherche dans la connaissance du projet : tickets Jira, documentation, "
            + "ADR, architecture, spécifications. Renvoie des sources numérotées, à citer par leur numéro. "
            + "Une recherche par sujet ; 5 recherches au plus par réponse.")
    @ToolInputGuardrails(SearchCap.class)
    public String searchKnowledge(
            @P("Quelques mots-clés précis (10 mots au plus), dans le vocabulaire du projet, différents des recherches "
                    + "déjà faites.") String query,
            @P("Sortes utiles parmi TICKET, DOC_SECTION, ADR, ARCH_ELEMENT, SPECIFICATION ; liste vide : toutes.")
            List<String> kinds,
            InvocationParameters parameters) {
        TurnTools turn = turn(parameters);
        if (turn.cancelled()) {
            return "";
        }
        String q = query == null ? "" : query.strip();
        if (q.isEmpty() || q.length() > MAX_QUERY) {
            return error("la recherche demande de 1 à " + MAX_QUERY + " caractères");
        }
        long start = System.nanoTime();
        try {
            TurnTools.Found found = turn.search(q, kinds(kinds));
            return UserMessages.found(found, turn.stepsLeft());
        } catch (DomainException e) {
            return error(e.userMessage());
        } catch (RuntimeException e) {
            LOG.warnf("Outil search_knowledge : échec (%s)", e.getClass().getSimpleName());
            return error(FAILED);
        } finally {
            LOG.infof("Outil search_knowledge : %d ms", (System.nanoTime() - start) / 1_000_000);
        }
    }

    @Tool(name = "analyze_perimeter", value = "Analyse exhaustive des sources du code du projet pour une évolution : "
            + "recense les modules qui contiennent les termes, propage par les dépendances et qualifie chaque module. "
            + "À utiliser pour une question de périmètre, de modules touchés, de tâches techniques ou de ce qu'il "
            + "faut modifier. Une seule analyse par réponse.")
    @ToolInputGuardrails(SearchCap.class)
    public String analyzePerimeter(
            @P("De 1 à 12 termes techniques tirés de la question, de l'échange et des sources (noms de classes, "
                    + "d'entités, d'énumérations, clés de configuration, files ou topics, chemins d'API), avec leurs "
                    + "variantes d'écriture (ContractStatus, CONTRACT_STATUS, contract.status). Évite les mots "
                    + "courants (id, status, data).") List<String> terms,
            InvocationParameters parameters) {
        TurnTools turn = turn(parameters);
        if (turn.cancelled()) {
            return "";
        }
        List<String> valid = terms == null ? List.of() : terms.stream().filter(t -> t != null)
                .map(String::strip).filter(t -> t.length() >= MIN_TERM && t.length() <= MAX_TERM).distinct()
                .limit(MAX_TERMS).toList();
        if (valid.isEmpty()) {
            return error("l'analyse demande de 1 à " + MAX_TERMS + " termes de " + MIN_TERM + " à " + MAX_TERM
                    + " caractères");
        }
        long start = System.nanoTime();
        try {
            return UserMessages.perimeter(turn.perimeter(valid));
        } catch (DomainException e) {
            return error(e.userMessage());
        } catch (RuntimeException e) {
            LOG.warnf("Outil analyze_perimeter : échec (%s)", e.getClass().getSimpleName());
            return error(FAILED);
        } finally {
            LOG.infof("Outil analyze_perimeter : %d ms", (System.nanoTime() - start) / 1_000_000);
        }
    }

    static TurnTools turn(InvocationParameters parameters) {
        return current(parameters == null ? null : parameters.get(TURN))
                .orElseThrow(() -> new IllegalStateException("Outil de l'agent appelé hors d'un tour"));
    }

    /** Le tour : celui des paramètres d'invocation, sinon celui du fil. */
    static Optional<TurnTools> current(Object parameter) {
        if (parameter instanceof TurnTools t) {
            return Optional.of(t);
        }
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    static Set<SourceKind> kinds(List<String> kinds) {
        Set<SourceKind> out = EnumSet.noneOf(SourceKind.class);
        for (String k : kinds == null ? List.<String>of() : kinds) {
            SEARCHABLE.stream().filter(s -> s.name().equals(k)).findFirst().ifPresent(out::add);
        }
        return out;
    }

    static String error(String message) {
        return "<erreur>" + UserMessages.data(message) + "</erreur>";
    }
}
