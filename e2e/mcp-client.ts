import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process';
import { createInterface } from 'node:readline';

/** Réponse JSON-RPC d'un appel ; `text` est le contenu d'un tools/call. */
export interface RpcResponse {
  id: number;
  result?: {
    content?: { type: string; text: string }[];
    isError?: boolean;
    [key: string]: unknown;
  };
  error?: { code: number; message: string };
}

/**
 * Client MCP stdio minimal pour l'E2E (T052 de 002) : lance la commande affichée dans
 * Configuration, telle qu'un agent de code la lancerait, et échange des messages JSON-RPC ligne
 * par ligne. stdout ne doit contenir que du JSON-RPC : toute autre ligne fait échouer le test.
 */
export class McpClient {
  private readonly process: ChildProcessWithoutNullStreams;
  private readonly pending = new Map<number, (r: RpcResponse) => void>();
  private nextId = 1;
  readonly stderr: string[] = [];
  readonly invalidLines: string[] = [];

  constructor(command: string, args: string[]) {
    const env = { ...process.env };
    delete env.JAVA_TOOL_OPTIONS;
    this.process = spawn(command, args, { env });
    createInterface({ input: this.process.stdout }).on('line', (line) => {
      try {
        const message = JSON.parse(line) as RpcResponse;
        this.pending.get(message.id)?.(message);
        this.pending.delete(message.id);
      } catch {
        this.invalidLines.push(line);
      }
    });
    createInterface({ input: this.process.stderr }).on('line', (line) => this.stderr.push(line));
  }

  request(method: string, params: object = {}): Promise<RpcResponse> {
    const id = this.nextId++;
    const response = new Promise<RpcResponse>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`Pas de réponse à ${method}`)), 15_000);
      this.pending.set(id, (r) => {
        clearTimeout(timer);
        resolve(r);
      });
    });
    this.process.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', id, method, params })}\n`);
    return response;
  }

  notify(method: string): void {
    this.process.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', method })}\n`);
  }

  /** tools/call : le texte de la réponse, et l'indicateur d'erreur métier. */
  async call(name: string, args: object = {}): Promise<{ text: string; isError: boolean }> {
    const r = await this.request('tools/call', { name, arguments: args });
    return { text: r.result?.content?.[0]?.text ?? '', isError: r.result?.isError === true };
  }

  close(): void {
    this.process.stdin.end();
    this.process.kill();
  }
}
