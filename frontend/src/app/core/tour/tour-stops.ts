/**
 * Arrêts de la visite guidée, dans l'ordre d'usage (018, FR-013, contracts/tour.md). Chaque cible
 * est un attribut `data-tour` posé dans le gabarit de l'écran ; sans cible (écran encore vide),
 * la bulle se centre sur l'écran avec son texte de remplacement (FR-015).
 */
export interface TourStop {
  id: string;
  /** Adresse fixe, ou `SPECIFICATION` : la spécification la plus récente, sinon la liste. */
  route: string;
  target: string;
  title: string;
  text: string;
  /** Texte quand la cible n'existe pas encore. */
  fallback: string;
}

export const SPECIFICATION = 'SPECIFICATION';

export const TOUR_STOPS: TourStop[] = [
  {
    id: 'conversation',
    route: '/',
    target: 'composer',
    title: 'Converser avec l’agent',
    text: 'Posez ici une question sur le projet. L’agent cherche dans la connaissance du projet et cite ses sources.',
    fallback: 'Posez une question sur le projet : l’agent cherche dans sa connaissance et cite ses sources.',
  },
  {
    id: 'draft',
    route: '/',
    target: 'draft-spec',
    title: 'Rédiger depuis la discussion',
    text: 'Quand la discussion décrit un besoin, ce bouton en fait une spécification.',
    fallback: 'Quand une discussion décrit un besoin, un bouton « Rédiger une spécification » apparaît en tête de la conversation.',
  },
  {
    id: 'sessions',
    route: '/sessions',
    target: 'sessions-list',
    title: 'Vos spécifications',
    text: 'Retrouvez, cherchez et rouvrez vos spécifications.',
    fallback: 'Vos spécifications apparaîtront ici, avec la recherche.',
  },
  {
    id: 'specification',
    route: SPECIFICATION,
    target: 'spec-document',
    title: 'Une spécification',
    text: 'Le document, ses versions et le ticket Jira lié.',
    fallback: 'Une spécification s’ouvre depuis cette liste : document, versions et ticket Jira lié.',
  },
  {
    id: 'discussion',
    route: SPECIFICATION,
    target: 'discuss',
    title: 'Discuter avec l’IA',
    text: 'Demandez une modification : l’agent propose une nouvelle version, que vous relisez par différences.',
    fallback: 'Sur une spécification, « Discuter avec l’IA » propose une nouvelle version relue par différences.',
  },
  {
    id: 'interview',
    route: SPECIFICATION,
    target: 'interview',
    title: 'Faire le point avec les sachants',
    text: 'Une séance sur écran partagé : hypothèses, écarts et points ouverts tranchés un à un, puis un compte rendu.',
    fallback: 'Sur une spécification, « Faire le point » ouvre une séance de relecture avec les sachants.',
  },
  {
    id: 'tasks',
    route: SPECIFICATION,
    target: 'tasks-panel',
    title: 'Tâches et suivi par les agents',
    text: 'Les tâches proposées deviennent des sous-tâches Jira, que les agents de code prennent et terminent.',
    fallback: 'Une spécification se découpe en tâches, créées en sous-tâches Jira et suivies par les agents de code.',
  },
  {
    id: 'jira',
    route: '/jira',
    target: 'jira-table',
    title: 'Tickets Jira',
    text: 'Les tickets du projet, triés par date, statut ou état de synchronisation avec leur spécification.',
    fallback: 'Les tickets du projet Jira s’afficheront ici une fois Jira connecté et synchronisé.',
  },
  {
    id: 'knowledge',
    route: '/projet/connaissance',
    target: 'knowledge-state',
    title: 'Connaissance du projet',
    text: 'Ce que l’agent sait du projet. Resynchronisez, ou réindexez une seule source.',
    fallback: 'Ce que l’agent sait du projet, et les commandes pour le resynchroniser.',
  },
  {
    id: 'project-settings',
    route: '/projet/reglages',
    target: 'doc-sources',
    title: 'Réglages du projet',
    text: 'Sources de documentation, fiabilité, synchronisation automatique et sources du code.',
    fallback: 'Sources de documentation, fiabilité, synchronisation automatique et sources du code.',
  },
  {
    id: 'advanced',
    route: '/configuration',
    target: 'advanced',
    title: 'Réglages avancés',
    text: 'Modèle de recherche, navigateur, notifications des sources mises à jour.',
    fallback: 'Modèle de recherche, navigateur, notifications des sources mises à jour.',
  },
  {
    id: 'agents',
    route: '/configuration',
    target: 'agent-access',
    title: 'Agents de code (MCP)',
    text: 'Recopiez cette commande dans votre agent de code : il lira les spécifications et suivra les tâches.',
    fallback: 'La passerelle MCP relie vos agents de code aux spécifications et aux tâches.',
  },
];
