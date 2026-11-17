package fr.kawamind.infrastructure.llm;

import dev.langchain4j.model.chat.request.ChatRequest;

/** Accès de test au message système et au message utilisateur que reçoit le modèle. */
public final class PromptOf {

    private PromptOf() {
    }

    public static String[] of(ChatRequest request) {
        CopilotSdkChatModel.Prompt prompt = CopilotSdkChatModel.Prompt.of(request);
        return new String[] {prompt.system(), prompt.user()};
    }
}
