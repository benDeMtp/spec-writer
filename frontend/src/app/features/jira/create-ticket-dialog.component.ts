import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { JiraApi } from '../../core/jira/jira.api';
import { PushPreview, RequiredField } from '../../core/jira/jira.model';
import { LinkedTicket } from '../../core/sessions/sessions.model';

export interface CreateTicketDialogData {
  sessionId: string;
  versionNumber: number;
}

/**
 * Création d'un ticket depuis une version (FR-036 à FR-038) : type (Story par défaut), titre, aperçu de
 * la description et champs obligatoires du projet. Rien n'est créé avant « Créer le ticket ».
 * Se ferme avec le ticket créé, ou sans valeur si l'utilisateur annule.
 */
@Component({
  selector: 'sw-create-ticket-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>{{ created() ? 'Ticket créé' : 'Créer un ticket Jira depuis la version ' + data.versionNumber }}</h2>
    <mat-dialog-content>
      @if (created(); as t) {
        <p role="status">
          Le ticket
          <a [href]="t.issueUrl" target="_blank" rel="noopener noreferrer">{{ t.issueKey }}<span class="sw-visually-hidden"> (nouvel onglet)</span></a>
          a été créé et la session lui est liée.
        </p>
        @if (t.relatesTo) {
          @if (t.relatesToLink === 'FAILED') {
            <p class="unsupported" role="alert">
              Le lien « relates to » vers {{ t.relatesTo }} n'a pas pu être créé.
              <button mat-button type="button" [disabled]="busy()" (click)="retryRelation()">Réessayer le lien</button>
            </p>
          } @else if (t.relatesToLink === 'LINKED') {
            <p>La story est liée à {{ t.relatesTo }} par « relates to ».</p>
          }
        }
      } @else if (preview(); as p) {
        <dl class="facts">
          <dt>Projet</dt>
          <dd>{{ p.projectKey }}</dd>
          <dt>Titre</dt>
          <dd>{{ p.summary }}</dd>
          @if (p.relatesTo) {
            <dt>Lien</dt>
            <dd>La story sera liée à {{ p.relatesTo }} par « relates to ».</dd>
          }
        </dl>
        <mat-form-field appearance="outline" class="type">
          <mat-label>Type de ticket</mat-label>
          <mat-select [value]="p.issueTypeId" (selectionChange)="changeType($event.value)" [disabled]="busy()">
            @for (t of p.issueTypes ?? []; track t.id) {
              <mat-option [value]="t.id">{{ t.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (fields().length) {
          <fieldset [formGroup]="form">
            <legend>Champs obligatoires du projet</legend>
            @for (f of fields(); track f.id) {
              @if (f.kind === 'UNSUPPORTED') {
                <p class="unsupported">
                  Le champ « {{ f.name }} » ne peut pas être rempli ici. Choisissez un autre type, ou créez le ticket
                  dans Jira puis liez-le en l'important.
                </p>
              } @else if (f.options.length) {
                <mat-form-field appearance="outline">
                  <mat-label>{{ f.name }}</mat-label>
                  <mat-select [formControlName]="f.id" required>
                    @for (o of f.options; track o.id) {
                      <mat-option [value]="o.id">{{ o.label }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              } @else {
                <mat-form-field appearance="outline">
                  <mat-label>{{ f.name }}</mat-label>
                  <input matInput [formControlName]="f.id" [type]="inputType(f)" required />
                </mat-form-field>
              }
            }
          </fieldset>
        }

        <h3 id="description-label">Description envoyée à Jira</h3>
        <pre class="wiki" tabindex="0" aria-labelledby="description-label">{{ p.descriptionWiki }}</pre>
      } @else {
        <p role="status" aria-live="polite">Lecture des types de ticket du projet…</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (created(); as t) {
        <button mat-flat-button type="button" (click)="close(t)">Fermer</button>
      } @else {
        <button mat-button type="button" mat-dialog-close [disabled]="busy()">Annuler</button>
        <button mat-flat-button type="button" [disabled]="!canCreate()" (click)="create()">
          {{ busy() && preview() ? 'Création…' : 'Créer le ticket' }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .facts {
      display: grid;
      grid-template-columns: 6rem minmax(0, 1fr);
      gap: 0.25rem 1rem;
      margin: 0 0 1rem;
    }
    .facts dt {
      color: var(--sw-ink-soft);
    }
    .facts dd {
      margin: 0;
    }
    .type {
      width: 16rem;
      max-width: 100%;
    }
    fieldset {
      display: grid;
      gap: 0.25rem;
      margin: 0 0 1rem;
      padding: 0.75rem 1rem 0;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
    }
    legend {
      padding: 0 0.25rem;
    }
    .unsupported {
      margin: 0 0 1rem;
      padding-left: 0.5rem;
      border-left: 3px solid var(--mat-sys-error);
    }
    h3 {
      margin: 0 0 0.5rem;
      font-size: var(--sw-step-0);
      font-weight: 600;
    }
    .wiki {
      max-height: 18rem;
      overflow: auto;
      margin: 0;
      padding: 0.75rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      white-space: pre-wrap;
      overflow-wrap: anywhere;
      font-size: var(--sw-step--1);
    }
    .wiki:focus-visible {
      outline: 2px solid var(--sw-pencil);
      outline-offset: 2px;
    }
  `,
})
export class CreateTicketDialogComponent {
  protected readonly data = inject<CreateTicketDialogData>(MAT_DIALOG_DATA);
  private readonly api = inject(JiraApi);
  private readonly ref = inject<MatDialogRef<CreateTicketDialogComponent, LinkedTicket>>(MatDialogRef);

  protected readonly preview = signal<PushPreview | null>(null);
  protected readonly created = signal<LinkedTicket | null>(null);
  protected readonly busy = signal(true);
  protected readonly fields = computed(() => this.preview()?.requiredFields ?? []);
  protected form = new FormGroup<Record<string, FormControl<string>>>({});
  private readonly valid = signal(true);

  protected readonly canCreate = computed(
    () => !!this.preview() && !this.busy() && this.valid() && this.fields().every((f) => f.kind !== 'UNSUPPORTED'),
  );

  constructor() {
    this.ref.disableClose = true;
    void this.load();
  }

  protected inputType(field: RequiredField): string {
    return field.kind === 'NUMBER' ? 'number' : field.kind === 'DATE' ? 'date' : 'text';
  }

  protected changeType(issueTypeId: string): void {
    void this.load(issueTypeId);
  }

  protected async create(): Promise<void> {
    const p = this.preview();
    if (!p?.issueTypeId) {
      return;
    }
    this.busy.set(true);
    try {
      this.created.set(await this.api.create(this.data.sessionId, this.data.versionNumber, p.issueTypeId, this.values()));
    } catch {
      // le bandeau d'erreur affiche la cause renvoyée par Jira ; le formulaire reste modifiable
    } finally {
      this.busy.set(false);
    }
  }

  /** Le ticket est créé : seul le lien « relates to » est réessayé. */
  protected async retryRelation(): Promise<void> {
    this.busy.set(true);
    try {
      this.created.set(await this.api.retryRelation(this.data.sessionId));
    } catch {
      // le bandeau d'erreur affiche la cause ; le bouton reste disponible
    } finally {
      this.busy.set(false);
    }
  }

  protected close(ticket: LinkedTicket): void {
    this.ref.close(ticket);
  }

  private async load(issueTypeId?: string): Promise<void> {
    this.busy.set(true);
    try {
      const preview = await this.api.preview(this.data.sessionId, this.data.versionNumber, issueTypeId);
      this.form = formFor(preview.requiredFields ?? []);
      this.valid.set(this.form.valid);
      this.form.statusChanges.subscribe(() => this.valid.set(this.form.valid));
      this.preview.set(preview);
    } catch {
      if (!this.preview()) {
        this.ref.close();
      }
    } finally {
      this.busy.set(false);
    }
  }

  private values(): Record<string, string> {
    return Object.fromEntries(Object.entries(this.form.getRawValue()).map(([id, v]) => [id, String(v).trim()]));
  }
}

function formFor(fields: RequiredField[]): FormGroup<Record<string, FormControl<string>>> {
  const controls: Record<string, FormControl<string>> = {};
  for (const f of fields.filter((x) => x.kind !== 'UNSUPPORTED')) {
    controls[f.id] = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  }
  return new FormGroup(controls);
}
