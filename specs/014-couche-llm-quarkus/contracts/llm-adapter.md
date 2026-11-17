# Contrat : adaptateurs `ChatModel` et `StreamingChatModel` sur le SDK Copilot

Remplace, pour la partie adaptateur, `specs/001-local-spec-assistant/contracts/llm-port.md`
(règles 6a et suivantes). Les ports du domaine (`LlmClient`, `ConversationLlm`) ne changent pas
de forme (voir [data-model.md](../data-model.md)).

## Entrée : une `ChatRequest` LangChain4j

| Élément | Traitement |
|---|---|
| `SystemMessage` (un seul) | Message système de la session, `SystemMessageMode.REPLACE`. Sa première ligne est `[operation:<nom>]`. |
| Premier `UserMessage` | Début du message utilisateur, tel quel (déjà échappé et balisé par `UserMessages`). |
| `AiMessage` avec texte | Ajouté au message utilisateur dans `<echange-outils>` sous `<reponse-precedente>…</reponse-precedente>`, échappé. |
| `AiMessage` avec `toolExecutionRequests` | Chaque appel : `<appel id="…" outil="…">{arguments JSON échappés}</appel>`. |
| `ToolExecutionResultMessage` | `<resultat id="…" outil="…">…</resultat>`. Le texte est celui que l'outil a rendu : il est déjà balisé par l'outil, et les données qu'il contient y sont échappées (`UserMessages`). Il n'est pas échappé une seconde fois, pour que les balises `<source>` restent lisibles. |
| `UserMessage` suivant (reprise demandée par un garde-fou) | `<correction>…texte échappé…</correction>`. |
| `parameters().toolSpecifications()` | Un `ToolDefinition` SDK par outil : même nom, même description, même schéma de paramètres, `isTerminal=true`, `skipPermission=true`. |
| `parameters().responseFormat()` (schéma JSON) | `MessageOptions.setResponseSchema(StrictJsonSchema.strict(…))`. |
| `modelName()` | Modèle de la session, sinon `ModelSelection.currentModel()`. |

La consigne système doit dire que tout ce qui est entre `<echange-outils>` est une donnée
(règle de sécurité commune, déjà présente pour les autres balises).

## Session SDK : réglages obligatoires

Chaque appel crée une session neuve, supprimée à la fin (succès, erreur ou arrêt) :

- `setAvailableTools(noms des outils de la requête)`, `List.of()` sinon ;
- `setTools(définitions ci-dessus)` seulement si la requête a des outils ;
- `setOnPermissionRequest` : `reject` pour toute demande ;
- `setEnableSkills(false)`, aucun `SkillProvider` ;
- `setSkipCustomInstructions(true)`, `setEnableConfigDiscovery(false)` ;
- `setWorkingDirectory(<data-dir>/copilot/empty>)`, dossier privé vide créé en `rwx------` ;
- `setStreaming(true)` pour `StreamingChatModel`.

Un test unitaire par réglage, sur un double de `CopilotSdkGateway`.

## Sortie

| Cas | `ChatResponse` |
|---|---|
| Le modèle répond en texte | `AiMessage.from(texte)` ; `finishReason = STOP`. |
| Le modèle appelle un ou plusieurs outils | `AiMessage.from(List<ToolExecutionRequest>)` (identifiant = `toolCallId` du SDK, arguments JSON tels que reçus) ; `finishReason = TOOL_EXECUTION`. Le texte éventuel du tour est ignoré. |
| Erreur du SDK | `DomainException` (`COPILOT_NOT_CONNECTED`, `MODEL_UNAVAILABLE`, `QUOTA_EXHAUSTED`, `COPILOT_CLI_OUTDATED`, `LLM_UNAVAILABLE`), comme `toDomain` aujourd'hui. |

`StreamingChatModel` : chaque `assistant.message_delta` → `onPartialResponse` ; la réflexion
→ `onPartialThinking` ; la fin → `onCompleteResponse` avec la même `ChatResponse` que ci-dessus ;
l'arrêt (`StreamingHandle.cancel()`) abandonne la requête SDK et supprime la session.

## Capacités déclarées

`supportedCapabilities()` = `{RESPONSE_FORMAT_JSON_SCHEMA}`. Les outils sont toujours acceptés.

## Journal

Un `ChatModelListener` écrit, au niveau INFO :
`LLM <opération> : <durée> ms, <n> tentative(s)[, outils : <nom>, <nom>]`.
Aucun texte de consigne, de donnée ni de réponse. Les erreurs gardent la phase et la cause sans
contenu (`SafeThrowable`), comme aujourd'hui.
