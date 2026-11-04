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
  /** Fiche générale, rédigée à la main, vide sans fiche (FR-008). */
  context: string;
  contextUpdatedAt: string | null;
  docSources: DocSource[];
  /** Workspace Structurizr du projet (chemin absolu) et propriété ownership des éléments propres (R21). */
  architectureWorkspace?: string | null;
  architectureOwnership?: string | null;
  knowledge?: KnowledgeStatus;
}

export type DeletionMode = 'MOVE' | 'DELETE';

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

export type ModelState = 'MISSING' | 'DOWNLOADING' | 'READY' | 'ERROR' | 'TERMS_ONLY';

/** État de la connaissance indexée d'un projet (FR-032, FR-043). */
export interface KnowledgeStatus {
  tickets: number;
  docSections: number;
  adrs: number;
  architectureElements: number;
  syncedAt: string | null;
  /** Indexation en cours, source en erreur ou modèle de recherche absent. */
  partial: boolean;
  model: ModelState;
}

export type IndexationMode = 'FULL' | 'INCREMENTAL';
export type IndexationStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type IndexationStep = 'MODEL' | 'JIRA' | 'DOCUMENTATION' | 'ARCHITECTURE' | 'EMBEDDING';

/** Erreur d'une indexation, sans contenu de page ni de ticket (FR-013g). */
export interface IndexationError {
  kind: 'PAGE' | 'SOURCE' | 'JIRA' | 'ARCHITECTURE' | 'MODEL' | 'FAILURE';
  location: string | null;
  reason: string;
  httpStatus: number | null;
  message: string | null;
}

/** Indexation en arrière-plan, suivie par interrogation (research.md R14, R20). */
export interface Indexation {
  status: IndexationStatus;
  mode: IndexationMode;
  step: IndexationStep | null;
  current: string | null;
  done: number;
  total: number;
  startedAt: string;
  stepStartedAt: string | null;
  finishedAt: string | null;
  errors: IndexationError[];
}

export type KnowledgeKind = 'TICKET' | 'DOC_SECTION' | 'ADR' | 'ARCH_ELEMENT';

/** Élément retrouvé par la recherche d'essai, en extrait (FR-032). */
export interface KnowledgeHit {
  kind: KnowledgeKind;
  ref: string;
  title: string;
  status: string | null;
  scope: 'PROJECT' | 'COMMON' | null;
  excerpt: string;
  url: string | null;
}
