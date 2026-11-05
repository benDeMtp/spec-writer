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

/** Une page de 25 éléments au plus, des deux sources fusionnées par date (research.md R9). */
export interface SearchResult {
  items: SearchItem[];
  /** « 26 à 50 sur 120 », ou « sur environ 120 ». */
  position: string;
  approximateTotal: boolean;
  /** Curseur opaque de la page suivante ; absent en fin de liste. */
  nextCursor: string | null;
  jiraStatus: JiraStatus;
  /** Présent si Jira n'a pas répondu : la page ne porte que sur spec-writer. */
  jiraError: string | null;
}
