import { expect, type APIRequestContext } from '@playwright/test';

/**
 * 011 : nouvelle version proposée d'après la discussion, par l'API. La discussion suivie de la
 * spécification reçoit `text`, puis l'agent propose ; renvoie la proposition à appliquer.
 */
export async function proposeThroughDiscussion(
  request: APIRequestContext,
  headers: Record<string, string>,
  sessionId: string,
  text: string,
): Promise<{ conversationId: string; proposalId: string }> {
  const opened = await request.post(`/api/sessions/${sessionId}/discussion`, { headers, data: {} });
  expect(opened.ok(), await opened.text()).toBe(true);
  const conversationId: string = (await opened.json()).conversationId;
  const sent = await request.post(`/api/conversations/${conversationId}/messages`, {
    headers,
    data: { text },
  });
  expect(sent.status(), await sent.text()).toBe(202);
  await events(request, conversationId, (await sent.json()).turnId);
  const proposing = await request.post(`/api/conversations/${conversationId}/proposals`, {
    headers,
    data: {},
  });
  expect(proposing.status(), await proposing.text()).toBe(202);
  const stream = await events(request, conversationId, (await proposing.json()).turnId);
  const found = /"proposalId":"([^"]+)"/.exec(stream);
  expect(found, stream).not.toBeNull();
  return { conversationId, proposalId: found![1] };
}

/** Flux d'un tour, lu jusqu'à sa fin. */
async function events(request: APIRequestContext, conversationId: string, turnId: string): Promise<string> {
  const r = await request.get(`/api/conversations/${conversationId}/turns/${turnId}/events`, {
    headers: { Accept: 'text/event-stream' },
  });
  expect(r.ok()).toBe(true);
  return r.text();
}
