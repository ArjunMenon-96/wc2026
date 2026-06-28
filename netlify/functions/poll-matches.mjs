import { getStore } from '@netlify/blobs';
import webpush from 'web-push';

const ESPN_TODAY = 'https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/scoreboard?dates=20260611-20260719';
const ST = { in: 'LIVE', post: 'FT', pre: 'SCHEDULED' };

export default async () => {
  webpush.setVapidDetails(
    process.env.VAPID_SUBJECT,
    process.env.VAPID_PUBLIC_KEY,
    process.env.VAPID_PRIVATE_KEY
  );

  const res = await fetch(ESPN_TODAY + '&_=' + Date.now());
  const data = await res.json();
  const events = data.events || [];
  if (!events.length) return new Response('no matches today');

  const stateStore = getStore('match-state');
  const subsStore = getStore('push-subs');

  const { blobs } = await subsStore.list();
  const subsList = [];
  for (const { key } of blobs) {
    const doc = await subsStore.get(key, { type: 'json' });
    if (doc) subsList.push({ key, ...doc });
  }

  const now = Date.now();

  for (const e of events) {
    const c = e.competitions[0];
    const h = c.competitors.find(x => x.homeAway === 'home') || c.competitors[0];
    const a = c.competitors.find(x => x.homeAway === 'away') || c.competitors[1];
    const id = String(e.id);
    const home = h.team.displayName, away = a.team.displayName;
    const homeScore = h.score == null ? 0 : +h.score;
    const awayScore = a.score == null ? 0 : +a.score;
    const status = ST[c.status.type.state] || 'SCHEDULED';
    const koMs = new Date(c.date).getTime();
    const goals = (c.details || []).filter(x => x.scoringPlay);

    const prev = await stateStore.get(id, { type: 'json' });
    const notifications = [];

    if (status === 'SCHEDULED' && koMs - now > 0 && koMs - now <= 30 * 60 * 1000 && !(prev && prev.preKickoffSent)) {
      const mins = Math.round((koMs - now) / 60000);
      notifications.push({ tag: `prekickoff-${id}`, title: '⏰ Starting Soon', body: `${home} vs ${away} kicks off in ${mins} min` });
    }

    if (prev) {
      if (prev.status !== 'LIVE' && status === 'LIVE') {
        notifications.push({ tag: `kickoff-${id}`, title: '⚽ Kickoff!', body: `${home} vs ${away} has started` });
      }
      if (homeScore > prev.homeScore || awayScore > prev.awayScore) {
        const scoringTeamId = homeScore > prev.homeScore ? h.team.id : a.team.id;
        const latestGoal = goals
          .filter(g => String(g.team && g.team.id) === String(scoringTeamId))
          .sort((x, y) => (y.clock?.value || 0) - (x.clock?.value || 0))[0];
        const scorer = latestGoal?.athletesInvolved?.[0]?.displayName;
        const scorerTxt = scorer ? `${scorer}${latestGoal.ownGoal ? ' (OG)' : latestGoal.penaltyKick ? ' (pen)' : ''} scores! ` : '';
        notifications.push({ tag: `goal-${id}-${homeScore}-${awayScore}`, title: '⚽ GOAL!', body: `${scorerTxt}${home} ${homeScore} - ${awayScore} ${away}` });
      }
      if (prev.status !== 'FT' && status === 'FT') {
        notifications.push({ tag: `ft-${id}`, title: '🏁 Full Time', body: `${home} ${homeScore} - ${awayScore} ${away}` });
      }
    }

    await stateStore.setJSON(id, {
      status, homeScore, awayScore,
      preKickoffSent: (prev && prev.preKickoffSent) || notifications.some(n => n.tag.startsWith('prekickoff-'))
    });

    if (!notifications.length) continue;

    const subscribers = subsList.filter(s => (s.matches || []).includes(id));
    for (const sub of subscribers) {
      for (const note of notifications) {
        try {
          await webpush.sendNotification(sub.subscription, JSON.stringify({ ...note, matchId: id }));
        } catch (err) {
          if (err.statusCode === 404 || err.statusCode === 410) {
            await subsStore.delete(sub.key);
          }
        }
      }
    }
  }

  return new Response('OK');
};

export const config = { schedule: '*/2 * * * *' };
