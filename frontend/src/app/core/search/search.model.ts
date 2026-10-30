/** Recherche unifiée (contracts/openapi.yaml, /api/search). */
export type Provenance = 'SPEC_WRITER' | 'JIRA' | 'BOTH';
export type JiraStatus = 'OK' | 'NOT_CONFIGURED' | 'UNAVAILABLE' | 'TOKEN_REJECTED' | 'CONSENT_REQUIRED';

export interface SearchItem {
  /** Identifiant de session, ou clé Jira pour un ticket non lié. */
  id: string;
  title: string;
  provenance: Provenance;
  jiraKey: string | null;
  sessionId: string | null;
  /** Dernière version de la session, pour ouvrir sa spécification. */
  latestVersion: number | null;
  updatedAt: string;
  excerpt: string | null;
}

export interface SearchResult {
  items: SearchItem[];
  jiraStatus: JiraStatus;
  /** Tickets Jira trouvés ; 20 par page. */
  jiraTotal: number;
}

export const PAGE_SIZE = 20;
