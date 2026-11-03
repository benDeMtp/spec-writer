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
  /** Fiche de contexte en Markdown, vide sans contexte (FR-011). */
  context: string;
  contextOrigin: ContextOrigin;
  contextUpdatedAt: string | null;
  docSources: DocSource[];
}

export type DeletionMode = 'MOVE' | 'DELETE';

export type ContextOrigin = 'NONE' | 'GENERATED' | 'EDITED';
export type DocSourceKind = 'URL' | 'FOLDER';
export type UnreadReason = 'UNREACHABLE' | 'NOT_ANTORA' | 'HTTP_ERROR' | 'TOO_LARGE' | 'REDIRECT_OTHER_HOST' | 'LIMIT_REACHED';

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

/** Fiche proposée par l'IA, gardée en mémoire jusqu'à « Accepter » (FR-013a). */
export interface ContextProposal {
  proposalId: string;
  markdown: string;
  unifiedDiff: string;
  sources: string[];
  unread: UnreadSource[];
}

export type GenerationStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type GenerationStep = 'READING_DOCS' | 'READING_TICKETS' | 'SUMMARIZING' | 'BUILDING';

/** Erreur d'une génération, sans contenu de page ni de ticket (FR-013g). */
export interface GenerationError {
  kind: 'PAGE' | 'SOURCE' | 'JIRA' | 'CORRECTION' | 'FAILURE';
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
  finishedAt: string | null;
  errors: GenerationError[];
  proposal: ContextProposal | null;
}
