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
    console.error('[subscribe] invalid JSON:', e.message);
    return new Response('Invalid JSON', { status: 400 });
  }

  const { subscription, matchId, enabled } = body || {};
  if (!subscription || !subscription.endpoint || !matchId) {
    console.error('[subscribe] missing fields — subscription:', !!subscription, 'matchId:', matchId);
    return new Response('Missing subscription or matchId', { status: 400 });
  }

  const key = crypto.createHash('sha256').update(subscription.endpoint).digest('hex');
  console.log(`[subscribe] matchId=${matchId} enabled=${enabled} key=${key.slice(0,12)}…`);

  try {
    const store = getStore('push-subs');
    const existing = (await store.get(key, { type: 'json' })) || { subscription, matches: [] };
    existing.subscription = subscription;
    const matches = new Set(existing.matches || []);
    if (enabled) matches.add(String(matchId));
    else matches.delete(String(matchId));
    existing.matches = [...matches];
    await store.setJSON(key, existing);
    console.log(`[subscribe] saved — matches now: ${existing.matches}`);
    return new Response('OK');
  } catch (e) {
    console.error('[subscribe] blob error:', e.message);
    return new Response('Server error', { status: 500 });
  }
};
