package dev.specwriter.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.specwriter.application.knowledge.FindRelatedIssues;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.ProjectRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T118 : prépare le jeu de référence réel (research.md R27). Pour chaque besoin de
 * {@code cases.json}, écrit dans {@code candidates.json} ce que l'outil retrouve : les vingt tickets
 * candidats, les ADR transmis et les éléments d'architecture candidats, pour que l'utilisateur n'ait
 * qu'à choisir les attendus. Un besoin sans attendu suffit :
 * {@code [{"need": "..."}]}. Lancement :
 * {@code ./mvnw -pl backend test -Preference -Dtest=ReferenceSetPreparer -Dspecwriter.reference-set=<dossier>}.
 */
@QuarkusTest
@Tag("reference")
class ReferenceSetPreparer {

    private static final Logger LOG = Logger.getLogger(ReferenceSetPreparer.class);

    @Inject
    ProjectRepository projects;
    @Inject
    KnowledgeIndex index;
    @Inject
    EmbeddingPort embedding;
    @Inject
    FindRelatedIssues related;

    @Test
    void ecrireLesCandidats() throws Exception {
        Path dir = KnowledgeReferenceSetIT.referenceSet();
        List<Map<String, Object>> out = new ArrayList<>();
        try (ReferenceSet set = new ReferenceSet(dir)) {
            set.index();
            for (ReferenceSet.Case c : set.cases) {
                ReferenceSet.Case open = new ReferenceSet.Case(c.need(), null, null, null, null, null, null);
                ReferenceSet.Measure m = set.measure(open, projects, index, embedding, related);
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("need", c.need());
                one.put("ticketCandidates", m.candidates());
                one.put("adrs", m.adrs());
                one.put("moduleCandidates", m.moduleCandidates());
                out.add(one);
            }
        }
        Path file = dir.resolve("candidates.json");
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(Files.newBufferedWriter(file), out);
        LOG.infof("Jeu de référence : candidats écrits dans %s", file);
    }
}
