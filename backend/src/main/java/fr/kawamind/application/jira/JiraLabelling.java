package fr.kawamind.application.jira;

import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.JiraLabel;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.port.JiraLabelRepository;
import fr.kawamind.domain.port.TicketLabels;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Pose du label de reconnaissance (024, US1, research R3) : dans le corps d'une création, ou ajouté
 * à un ticket repris. Un refus ne bloque jamais : il donne un avertissement, écrit au journal. Ce que
 * spec-writer pose est noté aussitôt ({@link TicketLabels}), sans attendre la synchronisation.
 */
public class JiraLabelling {

    private static final Logger LOG = Logger.getLogger(JiraLabelling.class);

    private final JiraLabelRepository labels;
    private final TicketLabels tickets;
    private final IssueTracker tracker;

    public JiraLabelling(JiraLabelRepository labels, TicketLabels tickets, IssueTracker tracker) {
        this.labels = labels;
        this.tickets = tickets;
        this.tracker = tracker;
    }

    /** Label du projet ; vide sans réglage. */
    public Optional<JiraLabel> label(UUID projectId) {
        return labels.find(projectId);
    }

    /** Labels à envoyer à la création : ceux demandés, puis le label du projet, sans doublon. */
    public List<String> forCreation(UUID projectId, Collection<?> requested) {
        List<String> all = new ArrayList<>();
        if (requested != null) {
            requested.stream().filter(java.util.Objects::nonNull).map(String::valueOf).filter(l -> !all.contains(l))
                    .forEach(all::add);
        }
        label(projectId).map(JiraLabel::value).filter(l -> !all.contains(l)).ifPresent(all::add);
        return all;
    }

    /** Une création refusée à cause du champ des labels (absent de l'écran de création). */
    public static boolean refusedLabels(IssueTrackerException e) {
        return e.kind() == IssueTrackerException.Kind.REJECTED
                && e.details().stream().anyMatch(d -> d.startsWith("labels"));
    }

    /** Labels relus dans Jira pour un ticket (relecture des tâches, 024 FR-013). */
    public void read(UUID projectId, String key, Collection<String> read) {
        tickets.replace(projectId, key, read);
    }

    /** Le ticket vient d'être créé avec le label du projet. */
    public void created(UUID projectId, String key) {
        label(projectId).ifPresent(l -> tickets.add(projectId, key, l.value()));
    }

    /**
     * Reprise d'un ticket : le label est ajouté s'il manque ({@code known} : labels lus dans Jira,
     * nul s'ils ne l'ont pas été). Rend l'avertissement d'un refus, nul sinon.
     */
    public String tag(UUID projectId, JiraEndpoint endpoint, String key, Collection<String> known) {
        Optional<JiraLabel> label = label(projectId);
        if (label.isEmpty()) {
            return null;
        }
        String value = label.get().value();
        if (tickets.has(projectId, key, value)) {
            return null;
        }
        if (known != null && known.contains(value)) {
            tickets.add(projectId, key, value);
            return null;
        }
        try {
            tracker.addLabel(endpoint, key, value);
            tickets.add(projectId, key, value);
            return null;
        } catch (IssueTrackerException e) {
            return warning(value, key, e);
        }
    }

    /** Avertissement d'un label refusé, écrit au journal avec la clé et le motif. */
    public static String warning(String label, String key, IssueTrackerException e) {
        String why = e.details().isEmpty() ? e.kind() == IssueTrackerException.Kind.UNREACHABLE
                ? "Jira est injoignable" : "Jira l'a refusé" : String.join(" ; ", e.details());
        LOG.warnf("Label %s non posé sur %s : %s", label, key, why);
        return "Le label " + label + " n'a pas pu être posé sur " + key + " : " + why + ". Ajoutez-le dans Jira.";
    }
}
