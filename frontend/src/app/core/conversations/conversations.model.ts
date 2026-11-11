/** Agent conversationnel (010, contracts/api.md). */
export type SourceKind =
  'TICKET' | 'DOC_SECTION' | 'ADR' | 'ARCH_ELEMENT' | 'SPECIFICATION' | 'ATTACHMENT' | 'DISCUSSION' | 'MODULE';
export type MessageRole = 'USER' | 'AGENT';
export type MessageState = 'COMPLETE' | 'INCOMPLETE' | 'RUNNING';

/** Spécification discutée (011) ; `sessionId` nul : la spécification a été supprimée. */
export interface SpecificationRef {
  sessionId: string | null;
  name: string;
}

export interface ConversationSummary {
  id: string;
  projectId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  /** Présente pour la discussion d'une spécification (011). */
  specification?: SpecificationRef | null;
}

/** Discussion suivie d'une spécification (011). */
export interface DiscussionView {
  conversationId: string;
  specificationName: string;
  version: number;
}

/** Recherche lancée par l'agent pendant une réponse (5 au plus). */
export interface SearchStep {
  ordinal: number;
  query: string;
  kinds: SourceKind[];
  hits: number;
}

/** Source citée sous son numéro ; `url` : où l'ouvrir (Jira, page, version d'une spécification). */
export interface CitedSource {
  n: number;
  kind: SourceKind;
  ref: string;
  title: string;
  url: string | null;
}

/** Ce que fait l'agent : résumer sa mémoire, choisir une recherche, rédiger la réponse. */
export type ThinkingStage = 'MEMORY' | 'PLAN' | 'ANSWER' | 'REVISE';

/** Réflexion résumée par le modèle pour une étape du tour. */
export interface ThinkingPart {
  stage: ThinkingStage;
  text: string;
}

/** Réflexion de l'agent et durée du tour, gardées avec la réponse. */
export interface Thinking {
  parts: ThinkingPart[];
  durationMs: number;
}

export type Qualification = 'IMPACTED' | 'TO_CHECK' | 'NOT_IMPACTED' | 'UNQUALIFIED';

/** Raison de la présence d'un module dans le périmètre (013) ; seuls les champs de son type sont remplis. */
export interface PerimeterReason {
  type: 'TERM' | 'DEPENDENCY' | 'AGGREGATOR' | 'ARCHITECTURE';
  term: string | null;
  count: number | null;
  chain: { id: string; name: string }[] | null;
  of: { id: string; name: string } | null;
  element: string | null;
  related: string | null;
  description: string | null;
  technology: string | null;
}

export interface PerimeterOccurrence {
  path: string;
  line: number;
  term: string;
  excerpt: string;
}

export interface PerimeterModule {
  id: string;
  rootLabel: string;
  path: string;
  name: string;
  kind: 'MAVEN' | 'NPM' | 'HELM' | 'ROOT';
  occurrences: number;
  isNew: boolean;
  qualification: Qualification;
  justification: string | null;
  reasons: PerimeterReason[];
  /** Élément du modèle d'architecture correspondant ; nul si absent ou non applicable. */
  architecture: string | null;
  archStatus: 'MATCHED' | 'ABSENT' | null;
  samples: PerimeterOccurrence[];
}

/** Analyse de périmètre dans les sources du code (013, contracts/api.md). */
export interface PerimeterView {
  ordinal: number;
  terms: string[];
  rejectedTerms: { term: string; reason: 'TOO_SHORT' | 'TOO_LONG' | 'DUPLICATE' | 'OVER_LIMIT' }[];
  roots: { rootId: string; label: string; state: 'SCANNED' | 'MISSING' | 'UNREADABLE' }[];
  state: 'COMPLETE' | 'INCOMPLETE';
  incompleteReason: 'MAX_FILES' | 'MAX_DURATION' | 'ROOT_MISSING' | 'MAX_MODULES' | 'CANCELLED' | null;
  filesScanned: number;
  filesSkipped: number;
  skippedExamples: string[];
  durationMs: number;
  qualified: boolean;
  summary: { impacted: number; toCheck: number; notImpacted: number; unqualified: number };
  modules: PerimeterModule[];
  architectureOnly: { element: string; related: string; description: string | null; technology: string | null }[];
  unavailable: 'NO_ROOTS' | null;
}

export interface ConversationMessage {
  id: string;
  /** Numéro d'ordre dans la conversation (1, 2…). */
  ordinal: number;
  role: MessageRole;
  text: string;
  state: MessageState;
  historyTruncated: boolean;
  createdAt: string;
  steps: SearchStep[];
  sources: CitedSource[];
  /** Absente pour une réponse antérieure à la réflexion, ou pas encore finie. */
  thinking?: Thinking | null;
  /** Version de la spécification discutée transmise pour ce tour (011). */
  specificationVersion?: number | null;
  /** Analyses de périmètre du tour (013). */
  perimeters?: PerimeterView[];
}

export interface ConversationDetail extends ConversationSummary {
  messages: ConversationMessage[];
  sessions: { sessionId: string; name: string }[];
  /** Les messages jusqu'à ce numéro d'ordre sont transmis à l'agent sous forme de résumé. */
  memoryUntil: number | null;
}

/** Compactage de la mémoire pendant un tour. */
export type MemoryPhase = 'STARTED' | 'DONE' | 'FAILED';

export interface Started {
  userMessageId: string;
  turnId: string;
}

/** Événements du flux d'un tour (SSE). */
export type TurnEvent =
  | { type: 'started'; at: string }
  | { type: 'stage'; stage: ThinkingStage }
  | { type: 'reasoning'; text: string }
  | { type: 'step'; step: SearchStep }
  | { type: 'perimeter'; analysis: PerimeterView }
  | { type: 'delta'; text: string }
  | { type: 'sources'; sources: Omit<CitedSource, 'url'>[] }
  | { type: 'memory'; phase: MemoryPhase }
  | {
      type: 'done';
      state: MessageState;
      historyTruncated: boolean;
      durationMs: number;
      /** Proposition d'une nouvelle version (011) : sa référence, ou aucun changement et sa raison. */
      proposalId?: string | null;
      unchanged?: boolean;
      reason?: string | null;
    }
  | { type: 'error'; code: string; userMessage: string }
  | { type: 'gone' };

/** Point établi par la conversation, relu avant de démarrer la spécification (US2). */
export interface EstablishedPoint {
  theme: string;
  question: string;
  answer: string;
  basis: 'USER' | 'SOURCE';
  sourceRef: string | null;
}

export interface NeedDraft {
  need: string;
  points: EstablishedPoint[];
  /** Dernière analyse de périmètre de la conversation, que la spécification reprendra (013). */
  perimeter?: { moduleCount: number; ordinal: number } | null;
}
