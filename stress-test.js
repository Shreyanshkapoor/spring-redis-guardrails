const POST_ID       = process.argv[2] || '1';
const TARGET_USER   = process.argv[3] || '1';
const BASE_URL      = process.env.BASE_URL || 'http://localhost:8080';
const TOTAL_BOTS    = 200;

async function sendBotComment(botId) {
  const url  = `${BASE_URL}/api/posts/${POST_ID}/comments`;
  const body = JSON.stringify({
    authorId:     botId,
    authorType:   'BOT',
    content:      `Stress-test comment from bot ${botId}`,
    depthLevel:   0,
    targetUserId: parseInt(TARGET_USER)
  });

  try {
    const res = await fetch(url, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body
    });
    return res.status;
  } catch (err) {
    return 500;
  }
}

async function main() {
  console.log(`\n🚀 Firing ${TOTAL_BOTS} concurrent bot-comment requests to post ${POST_ID}...\n`);

  const promises = Array.from({ length: TOTAL_BOTS }, (_, i) => sendBotComment(i + 1));
  const statuses = await Promise.all(promises);

  const counts = statuses.reduce((acc, s) => {
    acc[s] = (acc[s] || 0) + 1;
    return acc;
  }, {});

  console.log('── Results ─────────────────────────────────────────');
  Object.entries(counts).sort().forEach(([status, count]) => {
    const label = status === '201' ? '✅ Accepted' : status === '429' ? '🚫 Rejected (cap)' : '❌ Other';
    console.log(`  HTTP ${status}  ${label.padEnd(25)} → ${count} requests`);
  });

  const accepted = counts['201'] || 0;
  const rejected = counts['429'] || 0;

  console.log('\n── Verdict ─────────────────────────────────────────');
  if (accepted === 100 && rejected === 100) {
    console.log('  ✅ PASS  — Horizontal cap held perfectly at 100.');
  } else {
    console.log(`  ❌ FAIL  — Expected 100 accepted + 100 rejected.`);
    console.log(`             Got ${accepted} accepted + ${rejected} rejected.`);
  }
  console.log('────────────────────────────────────────────────────\n');
}

main();
