import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { AgentAccessApi, AgentAccessEntry, AgentAccessLog, AgentAccessState, AgentOperation } from '../../core/settings/agent-access.api';
import { IMPLEMENTATION_LABELS } from '../../core/sessions/sessions.model';
import { ConfirmDialogComponent, ConfirmData } from '../../shared/ui/confirm-dialog.component';
import { PagerComponent } from '../../shared/ui/pager.component';

const OPERATIONS: Record<AgentOperation, string> = {
  LIST_PROJECTS: 'Liste des projets',
  LIST_FEATURES: 'Liste des features',
  GET_FEATURE: 'Lecture d’une feature',
  SET_STATUS: 'Changement de statut',
  SEARCH_KNOWLEDGE: 'Recherche dans la connaissance',
};

/**
 * Accès des agents de code (FR-024, FR-028 de 002) : activation, commande et extrait MCP à copier
 * (sans secret), régénération du secret, et journal des accès par 25.
 */
@Component({
  selector: 'sw-agent-access-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatButtonModule, PagerComponent],
  template: `
    <section class="sheet" aria-labelledby="agents-title">
      <h3 id="agents-title">Accès des agents</h3>
      <p class="scope">Les agents de code accèdent à tous les projets.</p>
      @if (state(); as s) {
        <div class="status" [class.ok]="s.enabled" role="status">
          <p>
            {{
              s.enabled
                ? 'Activé : les agents de code qui lancent la commande ci-dessous lisent les features à implémenter.'
                : 'Désactivé : aucun agent ne peut joindre spec-writer.'
            }}
          </p>
        </div>
        <div class="actions">
          <button mat-flat-button type="button" [disabled]="busy()" (click)="toggle(!s.enabled)">
            {{ s.enabled ? "Désactiver l'accès des agents" : "Activer l'accès des agents" }}
          </button>
          @if (s.enabled) {
            <button mat-stroked-button type="button" [disabled]="busy()" (click)="regenerate()">Régénérer le secret</button>
          }
        </div>
        <h4 id="mcp-json-title">Configuration à déclarer dans l'agent</h4>
        <p class="hint">
          Claude Code, Copilot ou Cursor : ajoutez cet extrait à leur configuration MCP. Il ne contient aucun secret.
        </p>
        <pre tabindex="0" aria-labelledby="mcp-json-title"><code>{{ s.mcpJson }}</code></pre>
        <div class="actions">
          <button mat-stroked-button type="button" (click)="copy(s.mcpJson, 'Extrait copié.')">Copier l'extrait</button>
          <button mat-button type="button" (click)="copy(s.command, 'Commande copiée.')">Copier la commande</button>
        </div>
        <p class="announce" role="status" aria-live="polite">{{ announce() }}</p>

        <h4 id="agent-log-title">Journal des accès</h4>
        @if (log(); as l) {
          @if (l.entries.length) {
            <table aria-labelledby="agent-log-title">
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col">Agent</th>
                  <th scope="col">Opération</th>
                  <th scope="col">Concerne</th>
                </tr>
              </thead>
              <tbody>
                @for (e of l.entries; track $index) {
                  <tr>
                    <td>{{ e.at | date: 'd MMM y, HH:mm' }}</td>
                    <td>{{ e.agentName }}</td>
                    <td>{{ operation(e) }}</td>
                    <td>{{ subject(e) }}</td>
                  </tr>
                }
              </tbody>
            </table>
            <sw-pager
              label="Pages du journal des accès"
              [position]="position()"
              [hasPrevious]="l.startAt > 0"
              [hasNext]="l.startAt + l.entries.length < l.total"
              (previous)="loadLog(l.startAt - 25)"
              (next)="loadLog(l.startAt + 25)"
            />
          } @else {
            <p>Aucun accès d'agent pour l'instant.</p>
          }
        }
      } @else {
        <p role="status" aria-live="polite">Chargement de l'accès des agents…</p>
      }
    </section>
  `,
  styleUrl: './settings-card.scss',
  styles: `
    pre {
      overflow-x: auto;
      padding: 0.75rem;
      background: var(--mat-sys-surface-container);
      border-radius: var(--sw-radius-field);
    }
    h4 {
      margin: 1.25rem 0 0.5rem;
      font-size: var(--sw-step-1);
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: left;
      padding: 0.4rem;
      border-bottom: 1px solid var(--sw-rule);
    }
    .announce:empty {
      display: none;
    }
  `,
})
export class AgentAccessCardComponent {
  private readonly api = inject(AgentAccessApi);
  private readonly dialog = inject(MatDialog);

  protected readonly state = signal<AgentAccessState | null>(null);
  protected readonly log = signal<AgentAccessLog | null>(null);
  protected readonly busy = signal(false);
  protected readonly announce = signal('');
  protected readonly position = computed(() => {
    const l = this.log();
    return l && l.entries.length ? `${l.startAt + 1} à ${l.startAt + l.entries.length} sur ${l.total}` : '';
  });

  constructor() {
    void this.refresh();
  }

  protected operation(e: AgentAccessEntry): string {
    return OPERATIONS[e.operation] ?? e.operation;
  }

  /** Feature, ou projet, et pour un statut : « Prête à implémenter → En cours ». */
  protected subject(e: AgentAccessEntry): string {
    const what = e.featureTitle ?? e.projectName ?? '';
    if (e.oldStatus && e.newStatus) {
      return `${what} : ${IMPLEMENTATION_LABELS[e.oldStatus]} → ${IMPLEMENTATION_LABELS[e.newStatus]}`;
    }
    return what;
  }

  protected async toggle(enabled: boolean): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.setEnabled(enabled);
      this.announce.set(enabled ? 'Accès des agents activé.' : 'Accès des agents désactivé.');
      await this.refresh();
    } finally {
      this.busy.set(false);
    }
  }

  protected async regenerate(): Promise<void> {
    const data: ConfirmData = {
      title: 'Régénérer le secret des agents ?',
      message:
        'Les agents connectés perdront l’accès jusqu’à leur prochain appel : la passerelle relit alors le nouveau secret.',
      confirm: 'Régénérer',
    };
    const confirmed = await firstValueFrom(this.dialog.open(ConfirmDialogComponent, { data }).afterClosed());
    if (confirmed !== true) {
      return;
    }
    await this.api.regenerate();
    this.announce.set('Secret régénéré.');
  }

  protected async copy(text: string, done: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.announce.set(done);
    } catch {
      this.announce.set('La copie a échoué : sélectionnez le texte et copiez-le.');
    }
  }

  protected async loadLog(startAt: number): Promise<void> {
    this.log.set(await this.api.log(Math.max(0, startAt)));
  }

  private async refresh(): Promise<void> {
    try {
      this.state.set(await this.api.state());
      await this.loadLog(this.log()?.startAt ?? 0);
    } catch {
      // le bandeau d'erreur affiche le motif
    }
  }
}
