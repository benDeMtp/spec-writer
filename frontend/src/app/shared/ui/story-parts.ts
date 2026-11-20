import { UserStory } from '../../core/sessions/sessions.model';

/** Parties de l'énoncé de story (015, FR-023), la partie supposée marquée « (hypothèse) ». */
export function storyParts(
  story: UserStory,
): { lead: string; text: string; assumed: boolean; end: string }[] {
  const assumed = new Set(story.assumed ?? []);
  return [
    { lead: 'En tant que', text: story.role, assumed: assumed.has('ROLE'), end: ',' },
    { lead: 'je veux', text: story.capability, assumed: assumed.has('CAPABILITY'), end: ',' },
    { lead: 'afin de', text: story.benefit, assumed: assumed.has('BENEFIT'), end: '.' },
  ];
}
