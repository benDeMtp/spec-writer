package dev.specwriter.infrastructure.web;

import java.util.Set;
import java.util.EnumSet;
import dev.specwriter.domain.project.ContextSheet;
import dev.specwriter.domain.project.ContextQuestion;
import dev.specwriter.application.context.ManageContextQuestions;
import dev.specwriter.application.context.ContextProposalStore;
import dev.specwriter.application.context.AnswerContextQuestion;
import dev.specwriter.application.context.AcceptContextProposal;
import dev.specwriter.application.context.ContextProposalStore.ContextProposal;
import dev.specwriter.application.context.EditContext;
import dev.specwriter.application.context.ManageDocSources;
import dev.specwriter.application.context.PreviewContextSources;
import dev.specwriter.application.context.ContextGenerations;
import dev.specwriter.application.context.ContextGenerations.Snapshot;
import dev.specwriter.application.context.GenerationError;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.infrastructure.web.ProjectResource.DocSourceView;
import dev.specwriter.infrastructure.web.ProjectResource.ProjectView;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contexte d'un projet : fiche modifiée à la main, sources de documentation et propositions de
 * l'IA (FR-013 à FR-013e). Les pages et les tickets lus ne sont jamais renvoyés ni journalisés.
 */
@Path("/api/projects/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class ContextResource {

    public record ContextInput(String markdown) {
    }

    public record DocSourceInput(String kind, String location) {
    }

    public record PreviewView(int pages, int tickets, List<Unread> unreadable) {
    }

    public record QuestionAnswerInput(String answer, Boolean skip) {

        boolean skipped() {
            return Boolean.TRUE.equals(skip);
        }
    }

    public record AcceptInput(List<String> sheets) {
    }

    /** Fiche proposée, sans son hash de base (FR-008a). */
    public record ProposedSheetView(String markdown, String unifiedDiff, boolean changed, boolean accepted) {

        static ProposedSheetView of(ContextProposalStore.ProposedSheet s) {
            return new ProposedSheetView(s.markdown(), s.unifiedDiff(), s.changed(), s.accepted());
        }
    }

    public record ProposalQuestionView(UUID id, String sheet, String label, String rationale, List<String> suggestions,
            String state, Integer number, String answer) {

        static ProposalQuestionView of(ContextProposalStore.ProposalQuestion q) {
            return new ProposalQuestionView(q.id(), q.sheet().name(), q.label(), q.rationale(), q.suggestions(),
                    q.state().name(), q.number(), q.answer());
        }
    }

    public record ContextProposalView(UUID proposalId, ProposedSheetView functional, ProposedSheetView technical,
            List<String> sources, List<Unread> unread, List<ProposalQuestionView> questions) {

        static ContextProposalView of(ContextProposal p) {
            return new ContextProposalView(p.id(), ProposedSheetView.of(p.functional()), ProposedSheetView.of(p.technical()),
                    p.sources(), p.unread(), p.questions().stream().map(ProposalQuestionView::of).toList());
        }
    }

    /** Question répondue ou passée, citable sous {@code Q-number} (FR-013i). */
    public record ContextQuestionView(int number, String sheet, String label, String rationale, String answer, String state,
            Instant askedAt, Instant answeredAt) {

        static ContextQuestionView of(ContextQuestion q) {
            return new ContextQuestionView(q.number(), q.sheet().name(), q.label(), q.rationale(), q.answer(),
                    q.state().name(), q.askedAt(), q.answeredAt());
        }
    }

    /** État d'une génération : étape, élément en cours, avancement, erreurs et proposition (FR-013f, FR-013g). */
    public record GenerationView(String status, String step, String current, int done, int total, Instant startedAt,
            Instant finishedAt, List<GenerationError> errors, ContextProposalView proposal) {

        /** {@code proposal} : son état courant (questions répondues, fiches acceptées), absente si elle a expiré. */
        static GenerationView of(Snapshot s, ContextProposal proposal) {
            return new GenerationView(s.status().name(), s.step() == null ? null : s.step().name(), s.current(), s.done(),
                    s.total(), s.startedAt(), s.finishedAt(), s.errors(),
                    proposal == null ? null : ContextProposalView.of(proposal));
        }
    }

    @Inject
    ProjectResource projectViews;
    @Inject
    EditContext editContext;
    @Inject
    ManageDocSources manageDocSources;
    @Inject
    PreviewContextSources previewSources;
    @Inject
    ContextGenerations generations;
    @Inject
    AcceptContextProposal acceptProposal;
    @Inject
    AnswerContextQuestion answerQuestion;
    @Inject
    ManageContextQuestions manageQuestions;
    @Inject
    ContextProposalStore proposals;

    @PUT
    @Path("/context/{sheet}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProjectView editContext(@PathParam("id") String id, @PathParam("sheet") String sheet, ContextInput input) {
        return projectViews.view(editContext.run(ProjectResource.projectId(id), pathSheet(sheet),
                input == null ? null : input.markdown()));
    }

    @GET
    @Path("/context-questions")
    public List<ContextQuestionView> questions(@PathParam("id") String id) {
        return manageQuestions.list(ProjectResource.projectId(id)).stream().map(ContextQuestionView::of).toList();
    }

    @PUT
    @Path("/context-questions/{number}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ContextQuestionView updateQuestion(@PathParam("id") String id, @PathParam("number") int number,
            QuestionAnswerInput input) {
        QuestionAnswerInput in = input == null ? new QuestionAnswerInput(null, null) : input;
        return ContextQuestionView.of(manageQuestions.update(ProjectResource.projectId(id), number, in.answer(), in.skipped()));
    }

    @PUT
    @Path("/context-proposals/{proposalId}/questions/{questionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ContextQuestionView answer(@PathParam("id") String id, @PathParam("proposalId") String proposalId,
            @PathParam("questionId") String questionId, QuestionAnswerInput input) {
        QuestionAnswerInput in = input == null ? new QuestionAnswerInput(null, null) : input;
        return ContextQuestionView.of(answerQuestion.run(ProjectResource.projectId(id), Ids.parse(proposalId),
                Ids.parse(questionId), in.answer(), in.skipped()));
    }

    @POST
    @Path("/context-proposals/{proposalId}/revise")
    public Response revise(@PathParam("id") String id, @PathParam("proposalId") String proposalId) {
        Snapshot s = generations.revise(ProjectResource.projectId(id), Ids.parse(proposalId));
        return Response.status(Response.Status.ACCEPTED).entity(view(s)).build();
    }

    @POST
    @Path("/doc-sources")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addSource(@PathParam("id") String id, DocSourceInput input) {
        DocSourceInput in = input == null ? new DocSourceInput(null, null) : input;
        DocSource source = manageDocSources.add(ProjectResource.projectId(id), kind(in.kind()), in.location());
        return Response.status(Response.Status.CREATED).entity(DocSourceView.of(source)).build();
    }

    @DELETE
    @Path("/doc-sources/{sourceId}")
    public void deleteSource(@PathParam("id") String id, @PathParam("sourceId") String sourceId) {
        manageDocSources.delete(ProjectResource.projectId(id), Ids.parse(sourceId));
    }

    @GET
    @Path("/context-proposals/preview")
    public PreviewView preview(@PathParam("id") String id) {
        PreviewContextSources.Preview p = previewSources.run(ProjectResource.projectId(id));
        return new PreviewView(p.pages(), p.tickets(), p.unreadable());
    }

    /** Démarre la génération en arrière-plan (research.md R14) ; son état se suit par interrogation. */
    @POST
    @Path("/context-generations")
    public Response startGeneration(@PathParam("id") String id) {
        Snapshot s = generations.start(ProjectResource.projectId(id));
        return Response.status(Response.Status.ACCEPTED).entity(view(s)).build();
    }

    @GET
    @Path("/context-generations/current")
    public GenerationView currentGeneration(@PathParam("id") String id) {
        return generations.current(ProjectResource.projectId(id)).map(this::view)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Aucune génération du contexte pour ce projet."));
    }

    @DELETE
    @Path("/context-generations/current")
    public void cancelGeneration(@PathParam("id") String id) {
        generations.cancel(ProjectResource.projectId(id));
    }

    @POST
    @Path("/context-proposals/{proposalId}/accept")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProjectView accept(@PathParam("id") String id, @PathParam("proposalId") String proposalId, AcceptInput input) {
        Set<ContextSheet> sheets = EnumSet.noneOf(ContextSheet.class);
        for (String sheet : input == null || input.sheets() == null ? List.<String>of() : input.sheets()) {
            sheets.add(sheet(sheet));
        }
        return projectViews.view(acceptProposal.run(ProjectResource.projectId(id), Ids.parse(proposalId), sheets));
    }

    private GenerationView view(Snapshot s) {
        ContextProposal current = s.proposal() == null ? null
                : proposals.find(s.projectId(), s.proposal().id()).orElse(null);
        return GenerationView.of(s, current);
    }

    private static ContextSheet pathSheet(String raw) {
        return switch (raw == null ? "" : raw) {
            case "functional" -> ContextSheet.FUNCTIONAL;
            case "technical" -> ContextSheet.TECHNICAL;
            default -> throw new DomainException(ErrorCode.NOT_FOUND, "Fiche inconnue : functional ou technical.");
        };
    }

    private static ContextSheet sheet(String raw) {
        try {
            return ContextSheet.valueOf(raw == null ? "" : raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Fiche inconnue : FUNCTIONAL ou TECHNICAL.");
        }
    }

    private static DocSource.Kind kind(String raw) {
        try {
            return DocSource.Kind.valueOf(raw == null ? "" : raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez une adresse (URL) ou un dossier (FOLDER).");
        }
    }
}
