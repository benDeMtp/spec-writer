/** Agent conversationnel (010, contracts/api.md). */
export type SourceKind = 'TICKET' | 'DOC_SECTION' | 'ADR' | 'ARCH_ELEMENT' | 'SPECIFICATION' | 'ATTACHMENT';
export type MessageRole = 'USER' | 'AGENT';
export type MessageState = 'COMPLETE' | 'INCOMPLETE' | 'RUNNING';

export interface ConversationSummary {
  id: string;
  projectId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
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
  | { type: 'step'; step: SearchStep }
  | { type: 'delta'; text: string }
  | { type: 'sources'; sources: Omit<CitedSource, 'url'>[] }
  | { type: 'memory'; phase: MemoryPhase }
  | { type: 'done'; state: MessageState; historyTruncated: boolean }
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
}
