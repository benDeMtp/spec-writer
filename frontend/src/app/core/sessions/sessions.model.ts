import type { SourceOrigin } from '../knowledge/provenance';
/** Sessions, questions et versions, telles que renvoyées par l'API (contracts/openapi.yaml). */
export type SessionState = 'QUESTIONING' | 'READY_TO_GENERATE' | 'GENERATED';
export type QuestionState = 'PENDING' | 'ANSWERED' | 'SKIPPED';
export type Language = 'fr' | 'en';

export interface Question {
  id: string;
  theme: string;
  label: string;
  rationale: string;
  suggestions: string[];
  defaultValue: string | null;
  state: QuestionState;
  answer: string | null;
}

/** Statut d'implémentation (FR-026 de 002). */
export type ImplementationStatus = 'DRAFT' | 'READY' | 'IN_PROGRESS' | 'IMPLEMENTED';

export const IMPLEMENTATION_LABELS: Record<ImplementationStatus, string> = {
  DRAFT: 'Brouillon',
  READY: 'Prête à implémenter',
  IN_PROGRESS: 'En cours',
  IMPLEMENTED: 'Implémentée',
};

export interface Implementation {
  status: ImplementationStatus;
  /** Date du passage à « implémentée ». */
  implementedAt: string | null;
  /** Spécification d'origine d'une évolution (FR-026b). */
  evolvedFromSessionId: string | null;
}

export interface SessionSummary {
  id: string;
  name: string;
  state: SessionState;
  updatedAt: string;
  latestVersion: number | null;
  jiraKey: string | null;
  /** Faux si la sauvegarde est endommagée : la session ne peut plus être ouverte. */
  readable: boolean;
  /** Absent du détail, qui porte `implementation`. */
  implementationStatus?: ImplementationStatus;
  /** Points à traiter de la dernière version (021) ; nul sans version lisible. */
  pending?: PendingPoints | null;
}

export interface PendingPoints {
  assumptions: number;
  openQuestions: number;
  sourceDeviations: number;
}

export type SyncState = 'IN_SYNC' | 'LOCAL_AHEAD' | 'CONFLICT';

export interface LinkedTicket {
  issueKey: string;
  issueUrl: string;
  syncState: SyncState;
  linkedVersionNumber: number;
  /** Ticket fermé lié par « relates to » et état du lien (FR-035 de 002). */
  relatesTo?: string | null;
  relatesToLink?: 'PENDING' | 'LINKED' | 'FAILED' | null;
}

export interface SessionDetail extends SessionSummary {
  /** Projet de la spécification (feature 002). */
  projectId: string;
  linkedTicket: LinkedTicket | null;
  origin: 'LOCAL' | 'JIRA_IMPORT';
  need: string;
  language: Language;
  progress: { answered: number; skipped: number; total: number };
  currentBatch: Question[];
  answeredQuestions: Question[];
  warnings: string[];
  /** Effet de la dernière réponse modifiée (FR-007). */
  revision?: { removed: string[]; added: string[] };
  implementation: Implementation;
}

export interface VersionSummary {
  number: number;
  origin: 'GENERATED' | 'AI_EDIT' | 'JIRA_IMPORT' | 'CONFLICT_MERGE' | 'DISCUSSION';
  createdAt: string;
  pushedToJira: boolean;
  /** Discussion d'où vient une version `DISCUSSION`, tant qu'elle existe (011). */
  conversationId?: string | null;
}

export interface SourceRef {
  kind:
    | 'NEED_EXCERPT'
    | 'ANSWER'
    | 'ASSUMPTION'
    | 'PROJECT_CONTEXT'
    | 'JIRA_ISSUE'
    | 'DOC_PAGE'
    | 'ARCH_ELEMENT'
    | 'DISCUSSION';
  questionId: string | null;
  excerpt: string;
  /** Clé du ticket, page ou ADR, nom de l'élément d'architecture cité (research.md R24 de 002). */
  ref?: string | null;
}

export interface UseCase {
  code: string;
  title: string;
  actor: string;
  goal: string;
  preconditions: string[];
  nominalFlow: string[];
  alternatives: string[];
  source: SourceRef | null;
}

export interface Scenario {
  code: string;
  useCaseCode: string;
  title: string;
  kind: 'NOMINAL' | 'ERROR' | 'EDGE';
  asA: string;
  given: string[];
  when: string[];
  then: string[];
  assumption: boolean;
}

export interface Assumption {
  code: string;
  statement: string;
  origin: 'SKIPPED_QUESTION' | 'TOOL_INFERENCE';
  questionId: string | null;
  /** Élément concerné (021) ; nul pour une hypothèse non rattachée. */
  anchor?: SpecAnchor | null;
}

export interface Requirement {
  code: string;
  category: 'FUNCTIONAL' | 'TECHNICAL';
  statement: string;
}

export interface Specification {
  number: number;
  origin: string;
  /** Discussion d'où vient la version ou la proposition (011). */
  conversationId?: string | null;
  createdAt: string;
  title: string;
  /** Markdown (FR-012b). */
  summary: string;
  /** Markdown, vide si la version n'a pas de détails (FR-012b). */
  details: string;
  useCases: UseCase[];
  scenarios: Scenario[];
  assumptions: Assumption[];
  requirements: Requirement[];
  markdown: string;
  /** Date du contexte de projet utilisé, absente sans contexte (FR-013d de 002). */
  contextUpdatedAt?: string | null;
  /** Écarts aux ADR cités (FR-037 de 002). */
  adrDeviations?: AdrDeviation[];
  /** Faits d'architecture calculés par l'outil (FR-039 à FR-041 de 002). */
  architecture?: Architecture | null;
  /** Adresse de chaque référence citée : ticket, page ou ADR. */
  links?: Record<string, string>;
  /** Énoncé de story en tête (015, FR-023) ; absent des versions antérieures. */
  story?: UserStory | null;
  /** Stories proposées pour une story trop large (FR-024) ; vide sinon. */
  splitProposal?: string[];
  /** Écarts entre une source de référence et une interprétation (016, FR-021) ; vide sinon. */
  sourceDeviations?: SourceDeviation[];
  /** Questions sans réponse ni valeur par défaut (021) ; absentes des versions antérieures. */
  openQuestions?: OpenQuestion[];
}

/** Rattachement d'un point à un élément de la version (021) : use case et étape, scénario, exigence, résumé. */
export interface SpecAnchor {
  element: string;
  step: number | null;
  alternative: boolean;
}

/** Point ouvert (021) : une question, un responsable facultatif, son élément. */
export interface OpenQuestion {
  code: string;
  question: string;
  owner: string | null;
  due: string | null;
  anchor: SpecAnchor | null;
}

/** Extrait cité par un écart entre sources : référence, sorte de source, titre, date (016). */
export interface CitedExtract {
  ref: string;
  origin: SourceOrigin | null;
  title: string | null;
  updatedAt: string | null;
}

export interface SourceDeviation {
  /** Code de l'écart, « EC-01 »… (021). */
  code?: string;
  reference: CitedExtract;
  interpretation: CitedExtract;
  difference: string;
  anchor?: SpecAnchor | null;
}

export type StoryPart = 'ROLE' | 'CAPABILITY' | 'BENEFIT';

export interface UserStory {
  role: string;
  capability: string;
  benefit: string;
  /** Parties supposées, marquées « (hypothèse) ». */
  assumed: StoryPart[];
}

export interface AdrDeviation {
  adrRef: string;
  decision: string | null;
  deviation: string;
}

export interface Architecture {
  modules: { name: string; scope: 'PROJECT' | 'COMMON' }[];
  exchanges: { source: string; destination: string; kind: 'DIRECT' | 'INDIRECT' | 'MISSING'; via: string | null }[];
  deploymentFacts: {
    kind: 'NOT_DEPLOYED' | 'MULTIPLE_INSTANCES' | 'CROSS_INFRASTRUCTURE' | 'SHARED_INFRASTRUCTURE';
    element: string;
    other: string | null;
  }[];
}

export type RelatedDecision = 'LINK_NEW' | 'SAME' | 'DIFFERENT' | 'NONE';

/** Tickets proches du besoin, montrés avant les questions (FR-034 de 002). */
export interface RelatedIssues {
  status: 'PENDING' | 'READY' | 'PARTIAL' | 'UNAVAILABLE';
  issues: RelatedIssue[];
  decision: RelatedDecision | null;
}

export interface RelatedIssue {
  key: string;
  title: string;
  status: string;
  closed: boolean;
  /** Faux si Jira n'a pas pu être relu : statut indexé, daté de updatedAt. */
  statusFresh: boolean;
  updatedAt: string | null;
  reason: string | null;
  url: string | null;
  /** Ticket parent quand le ticket proche est une sous-tâche (research.md R29 de 002). */
  parentKey?: string | null;
}

export type SubtaskState = 'TO_REVIEW' | 'DELIVERED_TO_REWORK' | 'CONSISTENT' | 'UNMATCHED';

/** Sous-tâche de la story liée, face à une version (FR-046, FR-047 de 002). */
export interface Subtask {
  key: string;
  title: string;
  url: string | null;
  status: string | null;
  statusCategory: string | null;
  /** Faux si Jira n'a pas pu être relu : statut indexé, daté de statusDate. */
  statusFresh: boolean;
  statusDate: string | null;
  useCase: { code: string; title: string } | null;
  alternative: { code: string; title: string } | null;
  closeness: 'VERY_CLOSE' | 'CLOSE' | null;
  change: 'CHANGED' | 'REMOVED' | null;
  state: SubtaskState;
}

/** Rubrique « Sous-tâches de <clé> » d'une version, calculée à la demande. */
export interface SubtaskReport {
  storyKey: string;
  parentKey: string | null;
  matching: 'SEMANTIC' | 'UNAVAILABLE';
  matchingProblem: string | null;
  subtasks: Subtask[];
}

export interface IssueComparison {
  key: string;
  common: string[];
  different: string[];
  suggestion: 'SAME' | 'DIFFERENT';
}
