package dev.specwriter.application.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.knowledge.AnalyzeArchitecture;
import dev.specwriter.application.knowledge.SelectKnowledge;
import dev.specwriter.application.knowledge.StoryFamily;
import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.InMemoryAttachments;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 009, US2 : le texte gardé avec la session suffit à régénérer (FR-009) ; à documents joints
 * identiques, la version est réutilisée sans appel ; un texte différent donne une autre empreinte.
 */
class GenerateWithAttachmentsTest {

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final InMemoryAttachments attachments = new InMemoryAttachments();
    GenerateSpecification generate;
    Session session;

    @BeforeEach
    void setUp() {
        SelectKnowledge knowledge = new SelectKnowledge(w.projects, index, new FakeEmbedding(),
                new AnalyzeArchitecture(index, w.llm, w.policy), StoryFamily.none(), null, null,
                ModelCapacities.UNKNOWN, attachments);
        generate = new GenerateSpecification(w.sessions, w.versions, w.llm, w.policy, w.clock, w.ids, "test", knowledge);
        session = w.generatedSession();
        w.sessions.save(session.readyToGenerate(w.clock.now()), Questionnaire.empty());
    }

    Attachment join(String fileName, String text) {
        Attachment a = Attachment.create(UUID.randomUUID(), session.id(), attachments.list(session.id()).size() + 1, null,
                fileName, text, w.clock.now());
        attachments.add(a);
        return a;
    }

    @Test
    void documentsJointsIdentiquesVersionReutilisee() {
        join("CR COPIL.pdf", "Le délai de rétractation est de 14 jours.");
        w.llm.drafts.add(JiraWorld.draft("Avec le CR"));

        GenerateSpecification.Generated first = generate.run(session.id());
        GenerateSpecification.Generated again = generate.run(session.id());

        assertThat(first.reused()).isFalse();
        ProjectKnowledge sent = w.llm.generationRequests.getLast().knowledge();
        assertThat(sent.entries()).anySatisfy(e -> {
            assertThat(e.ref()).isEqualTo("joint:CR COPIL.pdf");
            assertThat(e.text()).isEqualTo("Le délai de rétractation est de 14 jours.");
            assertThat(e.designated()).isTrue();
        });
        assertThat(again.reused()).isTrue();
        assertThat(again.version().id()).isEqualTo(first.version().id());
        assertThat(w.llm.generationRequests).hasSize(1);
    }

    @Test
    void autreTexteAutreEmpreinte() {
        Attachment cr = join("CR COPIL.pdf", "Le délai de rétractation est de 14 jours.");
        w.llm.drafts.add(JiraWorld.draft("Avec le CR"));
        GenerateSpecification.Generated first = generate.run(session.id());

        attachments.remove(session.id(), cr.id());
        join("CR COPIL.pdf", "Le délai de rétractation est de 30 jours.");
        w.llm.drafts.add(JiraWorld.draft("Avec le nouveau CR"));
        GenerateSpecification.Generated second = generate.run(session.id());

        assertThat(second.reused()).isFalse();
        assertThat(second.version().inputFingerprint()).isNotEqualTo(first.version().inputFingerprint());
    }
}
