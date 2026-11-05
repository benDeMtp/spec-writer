import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ModelState } from '../projects/projects.model';

export type Pooling = 'MEAN' | 'CLS';

/** Modèle de recherche par le sens (research.md R19 de 002) : état et réglages. */
export interface EmbeddingSettings {
  state: ModelState;
  modelId: string;
  problem: string | null;
  defaultModel: boolean;
  folder: string | null;
  pooling: Pooling;
  queryPrefix: string | null;
  passagePrefix: string | null;
}

export interface EmbeddingInput {
  folder: string;
  pooling: Pooling;
  queryPrefix: string;
  passagePrefix: string;
}

@Injectable({ providedIn: 'root' })
export class EmbeddingApi {
  private readonly http = inject(HttpClient);

  get(): Promise<EmbeddingSettings> {
    return firstValueFrom(this.http.get<EmbeddingSettings>('/api/settings/embedding'));
  }

  /** Un dossier vide revient au modèle par défaut. */
  save(input: EmbeddingInput): Promise<EmbeddingSettings> {
    return firstValueFrom(this.http.put<EmbeddingSettings>('/api/settings/embedding', input));
  }
}
