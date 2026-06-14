import { getStore } from '@netlify/blobs';
import crypto from 'node:crypto';

export default async (req) => {
  if (req.method !== 'POST') {
    return new Response('Method not allowed', { status: 405 });
  }

  let body;
  try {
    body = await req.json();
  } catch (e) {
    return new Response('Invalid JSON', { status: 400 });
  }

  const { subscription, matchId, enabled } = body || {};
  if (!subscription || !subscription.endpoint || !matchId) {
    return new Response('Missing subscription or matchId', { status: 400 });
  }

  const key = crypto.createHash('sha256').update(subscription.endpoint).digest('hex');
  const store = getStore('push-subs');

  const existing = (await store.get(key, { type: 'json' })) || { subscription, matches: [] };
  existing.subscription = subscription;
  const matches = new Set(existing.matches || []);
  if (enabled) matches.add(String(matchId));
  else matches.delete(String(matchId));
  existing.matches = [...matches];

  await store.setJSON(key, existing);

  return new Response('OK');
};
