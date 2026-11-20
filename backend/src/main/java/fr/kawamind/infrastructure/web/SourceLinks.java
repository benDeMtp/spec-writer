package fr.kawamind.infrastructure.web;

import fr.kawamind.application.jira.JiraAccess;
import fr.kawamind.domain.specification.SourceRef;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.UseCase;
import fr.kawamind.infrastructure.web.SessionViews.ProposalView;
import fr.kawamind.infrastructure.web.SessionViews.SpecificationView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Adresses des sources de la connaissance d'une spécification (research.md R24) : un ticket vers
 * Jira, une page ou un ADR publiés vers leur URL. Une page lue dans un dossier n'a pas d'adresse.
 */
@ApplicationScoped
public class SourceLinks {

    private static final String DISCUSSION = "discussion:";

    @Inject
    JiraAccess jira;

    public SpecificationView complete(SpecificationView view) {
        // écarts aux ADR et entre sources (016) : les extraits sur le web s'ouvrent depuis la spécification
        List<String> refs = new java.util.ArrayList<>(view.adrDeviations().stream()
                .map(SpecificationContent.AdrDeviation::adrRef).toList());
        view.sourceDeviations().forEach(d -> {
            refs.add(d.reference().ref());
            refs.add(d.interpretation().ref());
        });
        return view.withLinks(links(view.useCases(), refs));
    }

    public ProposalView complete(ProposalView view) {
        return view.withLinks(complete(view.proposed()).links());
    }

    private Map<String, String> links(List<UseCase> useCases, List<String> adrs) {
        Optional<JiraAccess.Target> target = jira.target();
        Map<String, String> out = new LinkedHashMap<>();
        for (UseCase uc : useCases) {
            SourceRef s = uc.source();
            if (s == null || s.ref() == null) {
                continue;
            }
            if (s.kind() == SourceRef.Kind.JIRA_ISSUE) {
                target.ifPresent(t -> out.put(s.ref(), t.issueUrl(s.ref())));
            } else if (s.kind() == SourceRef.Kind.DOC_PAGE && web(s.ref())) {
                out.put(s.ref(), s.ref());
            } else if (s.kind() == SourceRef.Kind.DISCUSSION && s.ref().startsWith(DISCUSSION)) {
                // discussion:<conversation>#<numéro> : la conversation, dans l'outil (011)
                String rest = s.ref().substring(DISCUSSION.length());
                int hash = rest.indexOf('#');
                out.put(s.ref(), "/conversations/" + (hash < 0 ? rest : rest.substring(0, hash)));
            }
        }
        adrs.stream().filter(SourceLinks::web).forEach(ref -> out.put(ref, ref));
        return out;
    }

    /** Lien d'une source de vérité à mettre à jour (021, US6) : le ticket dans Jira, la page sur le web. */
    public String link(SourceRef.Kind kind, String ref) {
        if (kind == SourceRef.Kind.JIRA_ISSUE) {
            return jira.target().map(t -> t.issueUrl(ref)).orElse(null);
        }
        return kind == SourceRef.Kind.DOC_PAGE && web(ref) ? ref : null;
    }

    private static boolean web(String ref) {
        String lower = ref.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
