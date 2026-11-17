package fr.kawamind.infrastructure.llm.ai.guardrails;

import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import fr.kawamind.domain.task.ProposedTasks;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.infrastructure.llm.UserMessages;
import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Garde-fou de {@code propose-tasks} (015, R8) : la forme est lue par {@link LlmOutputs#tasks}, puis
 * les règles qui dépendent des données (nombre de tâches, base citée, titres déjà pris, préalables)
 * sont vérifiées d'après le message de l'appel. Une réponse fautive est redemandée en nommant les
 * règles enfreintes, jamais le contenu ; le journal ne garde que leur nombre.
 */
@ApplicationScoped
public class TasksGuardrail extends JsonOutputGuardrail {

    private static final Logger LOG = Logger.getLogger(TasksGuardrail.class);

    public TasksGuardrail() {
        super(LlmOutputs::tasks);
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        OutputGuardrailResult shape = super.validate(request);
        if (!shape.isSuccess()) {
            return shape;
        }
        Object data = request.requestParams().variables().get("data");
        if (data == null) {
            return shape;
        }
        UserMessages.TasksContext context = UserMessages.tasksContext(data.toString());
        ProposedTasks tasks = (ProposedTasks) shape.successfulResult();
        List<String> problems = new TaskProposal(context.modules().isEmpty() ? TaskProposal.Basing.USE_CASES
                : TaskProposal.Basing.MODULES, tasks.tasks()).modelProblems(context.bounds(), context.modules(),
                        context.useCases(), context.existingTitles());
        if (problems.isEmpty()) {
            return shape;
        }
        LOG.warnf("LLM propose-tasks : %d règle(s) enfreinte(s)", problems.size());
        return reprompt("proposition hors des règles", "La proposition précédente ne respecte pas ces règles : "
                + String.join(" ", problems) + " Corrigez-la.");
    }
}
