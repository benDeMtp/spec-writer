/**
 * Provenance d'un extrait transmis à l'IA (016) : sorte de source, date de mise à jour à la source
 * et niveau de fiabilité. Nuls pour un message antérieur à 016.
 */
export type SourceOrigin =
  | 'JIRA'
  | 'ANTORA'
  | 'DOCUMENTS'
  | 'CONFLUENCE'
  | 'ARCHITECTURE'
  | 'SPECIFICATIONS'
  | 'SHEET'
  | 'ATTACHMENT';

export type Reliability = 'REFERENCE' | 'INTERPRETATION' | 'TO_CONFIRM';

export const ORIGIN_LABELS: Record<SourceOrigin, string> = {
  JIRA: 'Jira',
  ANTORA: 'Antora',
  DOCUMENTS: 'SharePoint',
  CONFLUENCE: 'Confluence',
  ARCHITECTURE: 'Modèle d’architecture',
  SPECIFICATIONS: 'Spécifications',
  SHEET: 'Fiche générale',
  ATTACHMENT: 'Document joint',
};

export const RELIABILITY_LABELS: Record<Reliability, string> = {
  REFERENCE: 'Référence',
  INTERPRETATION: 'Interprétation',
  TO_CONFIRM: 'À confirmer',
};

const DATE = new Intl.DateTimeFormat('fr-FR', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'Europe/Paris',
});

/** « 3 mars 2026 », ou « date inconnue » : la date d'indexation n'est jamais montrée. */
export function updatedLabel(updatedAt: string | null | undefined): string {
  if (!updatedAt) {
    return 'date inconnue';
  }
  const date = new Date(updatedAt);
  return Number.isNaN(date.getTime()) ? 'date inconnue' : DATE.format(date);
}

/** « Confluence · 3 mars 2026 », vide sans provenance (message antérieur à 016). */
export function provenanceLabel(p: {
  origin?: SourceOrigin | null;
  updatedAt?: string | null;
}): string {
  return p.origin ? `${ORIGIN_LABELS[p.origin]} · ${updatedLabel(p.updatedAt)}` : '';
}
