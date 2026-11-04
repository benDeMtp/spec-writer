/** Projets, tels que renvoyés par /api/projects (contracts/openapi.yaml de 002). */
export interface ProjectInput {
  name: string;
  description?: string | null;
  jiraProjectKey?: string | null;
}

export interface Project {
  id: string;
  name: string;
  description: string | null;
  jiraProjectKey: string | null;
  /** Clé du projet, sinon le projet Jira par défaut de la configuration. */
  effectiveJiraProjectKey: string | null;
  /** Clé introuvable dans Jira (FR-007) : signalée, sans bloquer l'enregistrement. */
  jiraKeyWarning: string | null;
  sessionCount: number;
  /** Fiches fonctionnelle et technique, vides sans contexte (FR-008a). */
  functionalContext: ContextSheetState;
  technicalContext: ContextSheetState;
  docSources: DocSource[];
}

export type DeletionMode = 'MOVE' | 'DELETE';

export type ContextOrigin = 'NONE' | 'GENERATED' | 'EDITED';
export type SheetKind = 'FUNCTIONAL' | 'TECHNICAL';

/** Une fiche du contexte d'un projet (FR-008a). */
export interface ContextSheetState {
  markdown: string;
  origin: ContextOrigin;
  updatedAt: string | null;
}
export type DocSourceKind = 'URL' | 'FOLDER';
export type UnreadReason =
  | 'UNREACHABLE'
  | 'NOT_ANTORA'
  | 'HTTP_ERROR'
  | 'TOO_LARGE'
  | 'REDIRECT_OTHER_HOST'
  | 'LIMIT_REACHED'
  | 'FOLDER_NOT_FOUND'
  | 'NOT_A_FOLDER'
  | 'ACCESS_DENIED'
  | 'UNREADABLE'
  | 'NO_HTML'
  | 'ADOC_SOURCES';

/** Source de documentation Antora d'un projet (FR-013) et résultat de sa dernière lecture. */
export interface DocSource {
  id: string;
  kind: DocSourceKind;
  location: string;
  lastReadAt: string | null;
  pagesRead: number | null;
  pagesInError: number | null;
}

export interface UnreadSource {
  location: string;
  reason: UnreadReason;
  httpStatus?: number | null;
}

/** Ce qui sera envoyé à Copilot, compté sans lire les pages (FR-013b). */
export interface ContextPreview {
  pages: number;
  tickets: number;
  unreadable: UnreadSource[];
}

/** Fiche proposée ; {@code changed} est faux si elle est identique à la fiche actuelle. */
export interface ProposedSheet {
  markdown: string;
  unifiedDiff: string;
  changed: boolean;
  accepted: boolean;
}

/** Question de clarification de l'assistant (FR-013h) ; {@code number} une fois répondue ou passée. */
export interface ProposalQuestion {
  id: string;
  sheet: SheetKind;
  label: string;
  rationale: string;
  suggestions: string[];
  state: 'OPEN' | 'ANSWERED' | 'SKIPPED';
  number: number | null;
  answer: string | null;
}

/** Question répondue ou passée, citable sous Q-n (FR-013i). */
export interface ContextQuestion {
  number: number;
  sheet: SheetKind;
  label: string;
  rationale: string;
  answer: string;
  state: 'ANSWERED' | 'SKIPPED';
  askedAt: string;
  answeredAt: string;
}

/** Fiches proposées par l'IA, gardées en mémoire jusqu'à « Accepter » (FR-013a). */
export interface ContextProposal {
  proposalId: string;
  functional: ProposedSheet;
  technical: ProposedSheet;
  sources: string[];
  unread: UnreadSource[];
  questions: ProposalQuestion[];
}

export type GenerationStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type GenerationStep = 'READING_DOCS' | 'READING_TICKETS' | 'SUMMARIZING' | 'BUILDING';

/** Erreur d'une génération, sans contenu de page ni de ticket (FR-013g). */
export interface GenerationError {
  kind: 'PAGE' | 'SOURCE' | 'JIRA' | 'CORRECTION' | 'REPAIRED' | 'FAILURE';
  location: string | null;
  reason: string;
  httpStatus: number | null;
  message: string | null;
}

/** Génération du contexte en arrière-plan, suivie par interrogation (FR-013f, research.md R14). */
export interface ContextGeneration {
  status: GenerationStatus;
  step: GenerationStep | null;
  current: string | null;
  done: number;
  total: number;
  startedAt: string;
  /** Début de l'étape en cours, pour le minuteur. */
  stepStartedAt: string | null;
  finishedAt: string | null;
  errors: GenerationError[];
  proposal: ContextProposal | null;
}
