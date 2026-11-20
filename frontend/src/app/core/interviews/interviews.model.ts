/** Séances de relecture avec les sachants (021, contracts/rest-api.md). */

export type InterviewState = 'PREPARED' | 'IN_PROGRESS' | 'FINISHED';
export type PointOrigin = 'ASSUMPTION' | 'SOURCE_DEVIATION' | 'OPEN_QUESTION' | 'REMARK' | 'QUESTION';
export type Outcome = 'CONFIRMED' | 'CHANGE' | 'REMOVE' | 'DEFERRED';
export type StopKind = 'SUMMARY' | 'USE_CASE' | 'STEP' | 'ALTERNATIVE' | 'SCENARIO' | 'REQUIREMENT' | 'UNANCHORED';

export interface Anchor {
  element: string;
  step: number | null;
  alternative: boolean;
}

export interface DesignatedSource {
  kind: string;
  ref: string;
}

export interface PointView {
  id: string;
  sessionId: string;
  anchor: Anchor | null;
  origin: PointOrigin;
  originCode: string | null;
  text: string;
  outcome: Outcome | null;
  owner: string | null;
  due: string | null;
  source: DesignatedSource | null;
  createdAt: string;
  updatedAt: string;
}

export interface PointInput {
  sessionId: string;
  anchor: Anchor | null;
  origin: PointOrigin;
  originCode: string | null;
  text: string;
  outcome: Outcome | null;
  owner: string | null;
  due: string | null;
  source: DesignatedSource | null;
}

/** Un point à un arrêt : point de la spécification (code), ou point créé en séance. */
export interface StopItem {
  origin: PointOrigin;
  code: string | null;
  statement: string | null;
  point: PointView | null;
  pending: boolean;
}

/** Un arrêt du parcours, calculé par le serveur (research.md R6, ordre de FR-003). */
export interface Stop {
  sessionId: string;
  anchor: Anchor;
  kind: StopKind;
  label: string;
  text: string;
  items: StopItem[];
}

export interface Counts {
  handled: number;
  notes: number;
  pending: number;
}

export interface InterviewSpecView {
  sessionId: string;
  name: string;
  rank: number;
  version: number | null;
  latestVersion: number;
  excluded: string[];
  appliedVersion: number | null;
  revisionAvailable: boolean;
  revisionUnavailableReason: string | null;
}

export interface InterviewDetail {
  id: string;
  state: InterviewState;
  participants: string[];
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  cursor: { sessionId: string; anchor: Anchor | null } | null;
  specs: InterviewSpecView[];
  stops: Stop[];
  counts: Counts;
}

export interface InterviewSummary {
  id: string;
  state: InterviewState;
  participants: string[];
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  specifications: { sessionId: string; name: string }[];
  points: number;
}

export interface InterviewReport {
  html: string;
  text: string;
}

export interface RevisionStarted {
  turnId: string;
  conversationId: string;
}

/** Séance préparée : les spécifications dans l'ordre de passage, avec leurs points écartés (US5). */
export interface PrepareInput {
  specs: { sessionId: string; excluded: string[] }[];
  participants: string[];
}

/** « À reporter » : une décision à reporter à la main dans une source de vérité (US6). */
export interface SourceUpdate {
  id: string;
  source: { kind: string; ref: string; title: string | null; link: string | null };
  text: string;
  /** `SOURCE_CHANGED` : la source a changé depuis la décision, ou n'est plus indexée. */
  state: 'OPEN' | 'SOURCE_CHANGED';
  interviewId: string | null;
  decidedAt: string;
}
