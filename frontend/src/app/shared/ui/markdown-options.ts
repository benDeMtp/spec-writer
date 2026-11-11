import { MarkedOptions, MarkedRenderer } from 'ngx-markdown';
import type { Tokens } from 'marked';

const SAFE_LINK = /^(?:https?:|mailto:)/i;
/**
 * Liens de l'outil écrits par la conversation (010) : version d'une spécification citée, note
 * d'une source sous la réponse, ou ligne d'un module dans l'analyse du périmètre (013). Ils
 * restent dans l'onglet.
 */
const TOOL_LINK =
  /^(?:\/sessions\/[0-9a-f-]{36}\/versions\/\d+(?:#[A-Za-z0-9-]{1,80})?|#source-[0-9a-f-]{36}-\d+|#perimeter-[0-9a-f-]{36}-\d+-\d+)$/i;

function attribute(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/**
 * Rendu Markdown du contenu tiers (descriptions Jira, FR-043a) : seuls les liens http, https et
 * mailto sont cliquables, et ils s'ouvrent dans un nouvel onglet sans accès à la page d'origine ;
 * plus les seuls liens de l'outil que pose la conversation ({@link TOOL_LINK}).
 * Les autres liens restent du texte. Le HTML produit est ensuite nettoyé par Angular (SANITIZE).
 */
export function markedOptions(): MarkedOptions {
  const renderer = new MarkedRenderer();
  renderer.link = function (this: MarkedRenderer, { href, title, tokens }: Tokens.Link): string {
    const text = this.parser.parseInline(tokens);
    const tip = title ? ` title="${attribute(title)}"` : '';
    if (TOOL_LINK.test(href)) {
      return `<a href="${attribute(href)}"${tip}>${text}</a>`;
    }
    if (!SAFE_LINK.test(href)) {
      return text;
    }
    return `<a href="${attribute(href)}"${tip} target="_blank" rel="noopener noreferrer">${text}</a>`;
  };
  // Un tableau large défile dans son propre cadre, atteignable au clavier, sans élargir la page.
  const table = MarkedRenderer.prototype.table;
  renderer.table = function (this: MarkedRenderer, token: Tokens.Table): string {
    return `<div class="sw-table" role="region" tabindex="0" aria-label="Tableau">${table.call(this, token)}</div>`;
  };
  return { renderer, gfm: true };
}
