import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { AgentAccessLog } from '../../core/settings/agent-access.api';
import { AgentAccessCardComponent } from './agent-access-card.component';

const MCP = '{\n  "mcpServers" : { "spec-writer" : { "command" : "/Applications/spec-writer.app/Contents/MacOS/spec-writer-mcp" } }\n}';

function log(startAt: number, count: number, total: number): AgentAccessLog {
  return {
    startAt,
    total,
    entries: Array.from({ length: count }, (_, i) => ({
      at: '2026-11-04T10:00:00Z',
      agentName: i === 0 ? 'Claude Code' : 'Cursor',
      operation: i === 0 ? 'SET_STATUS' : 'GET_FEATURE',
      projectName: 'Facturation',
      featureTitle: 'Saisir un avoir',
      oldStatus: i === 0 ? 'READY' : null,
      newStatus: i === 0 ? 'IN_PROGRESS' : null,
    })),
  };
}

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('AgentAccessCardComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<AgentAccessCardComponent>;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AgentAccessCardComponent);
    el = fixture.nativeElement;
  });

  function button(text: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!;
  }

  async function load(enabled: boolean, entries: AgentAccessLog): Promise<void> {
    fixture.detectChanges();
    ctrl.expectOne('/api/settings/agent-access').flush({ enabled, command: '/x/spec-writer-mcp', mcpJson: MCP });
    await settle(fixture);
    ctrl.expectOne((r) => r.url === '/api/settings/agent-access/log').flush(entries);
    await settle(fixture);
  }

  it('désactivé par défaut ; l’activation recharge l’état ; l’extrait est affiché sans secret', async () => {
    await load(false, log(0, 0, 0));
    expect(el.querySelector('[role="status"]')?.textContent).toContain('Désactivé');
    expect(el.querySelector('pre')?.textContent).toContain('spec-writer-mcp');
    expect(el.textContent).toContain("Aucun accès d'agent pour l'instant.");
    expect(button('Régénérer')).toBeUndefined();

    button("Activer l'accès des agents").click();
    const put = ctrl.expectOne({ method: 'PUT', url: '/api/settings/agent-access' });
    expect(put.request.body).toEqual({ enabled: true });
    put.flush(null);
    await settle(fixture);
    ctrl.expectOne('/api/settings/agent-access').flush({ enabled: true, command: '/x/spec-writer-mcp', mcpJson: MCP });
    await settle(fixture);
    ctrl.expectOne((r) => r.url === '/api/settings/agent-access/log').flush(log(0, 0, 0));
    await settle(fixture);
    expect(button("Désactiver l'accès des agents")).toBeDefined();
    expect(button('Régénérer le secret')).toBeDefined();
    expect(el.textContent).toContain('Accès des agents activé.');
  });

  it('journal paginé par 25, avec les changements de statut', async () => {
    await load(true, log(0, 25, 30));
    const rows = el.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(25);
    expect(rows[0].textContent).toContain('Claude Code');
    expect(rows[0].textContent).toContain('Saisir un avoir : Prête à implémenter → En cours');
    expect(rows[1].textContent).toContain('Lecture d’une feature');
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('1 à 25 sur 30');
    button('Page suivante').click();
    const req = ctrl.expectOne((r) => r.url === '/api/settings/agent-access/log');
    expect(req.request.params.get('startAt')).toBe('25');
    req.flush(log(25, 5, 30));
    await settle(fixture);
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('26 à 30 sur 30');
  });

  it('régénérer demande une confirmation', async () => {
    await load(true, log(0, 0, 0));
    button('Régénérer le secret').click();
    await settle(fixture);
    const dialog = document.querySelector('mat-dialog-container')!;
    expect(dialog.textContent).toContain('Régénérer le secret des agents ?');
    (Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent?.includes('Régénérer')) as HTMLButtonElement).click();
    await settle(fixture);
    ctrl.expectOne({ method: 'POST', url: '/api/settings/agent-access/secret' }).flush(null);
    await settle(fixture);
    expect(el.textContent).toContain('Secret régénéré.');
  });
});
