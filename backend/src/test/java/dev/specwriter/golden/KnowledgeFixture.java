package dev.specwriter.golden;

import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchDeployment;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Index de connaissance d'un cas du golden set, lu dans {@code knowledge.json} (T111) : tickets,
 * pages, ADR, éléments, relations et déploiements. Sans fichier, l'index est vide.
 */
final class KnowledgeFixture {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant T = Instant.EPOCH;

    private KnowledgeFixture() {
    }

    static InMemoryKnowledgeIndex read(Path file, UUID projectId) throws IOException {
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        if (!Files.exists(file)) {
            return index;
        }
        JsonNode root = JSON.readTree(Files.readString(file));
        for (JsonNode t : root.path("tickets")) {
            index.save(KnowledgeItem.ticket(projectId, t.path("key").asString(), t.path("title").asString(),
                    t.path("description").asString(), t.path("status").asString(), t.path("statusCategory").asString(),
                    "Story", T, T), null, null);
        }
        for (JsonNode p : root.path("pages")) {
            String role = p.path("role").asString("");
            index.save(KnowledgeItem.docSection(projectId, p.path("ref").asString(), 0, p.path("title").asString(),
                    p.path("body").asString(), role.isEmpty() ? null : KnowledgeRole.valueOf(role), T, T), null, null);
        }
        for (JsonNode a : root.path("adrs")) {
            String successor = a.path("supersededBy").asString("");
            index.save(KnowledgeItem.adr(projectId, a.path("ref").asString(), 0, a.path("title").asString(),
                    a.path("body").asString(), AdrStatus.Statement.of(AdrStatus.valueOf(a.path("status").asString()),
                            successor.isEmpty() ? null : successor), T, T), null, null);
        }
        for (JsonNode e : root.path("elements")) {
            index.save(KnowledgeItem.archElement(projectId, new ArchElement(e.path("name").asString(),
                    ArchElement.Type.valueOf(e.path("type").asString()), ArchScope.valueOf(e.path("scope").asString()),
                    e.path("transport").asBoolean(false), e.path("description").asString(""),
                    e.path("technology").asString("")), T, T), null, null);
        }
        List<ArchRelation> relations = new ArrayList<>();
        for (JsonNode r : root.path("relations")) {
            relations.add(new ArchRelation(r.path("source").asString(), r.path("destination").asString(),
                    r.path("description").asString(""), null));
        }
        List<ArchDeployment> deployments = new ArrayList<>();
        for (JsonNode d : root.path("deployments")) {
            deployments.add(new ArchDeployment(d.path("element").asString(), d.path("environment").asString(),
                    d.path("rootNode").asString(), d.path("nodePath").asString(), d.path("instances").asInt(1),
                    d.path("shared").asBoolean(false)));
        }
        index.replaceArchitecture(projectId, new ArchitectureModel(List.of(), relations, deployments));
        return index;
    }
}
