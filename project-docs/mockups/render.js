const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const dir = process.argv[2];
(async () => {
  const b = await chromium.launch();
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.svg')).sort();
  const font = '<link href="https://fonts.googleapis.com/css2?family=Barlow:wght@400;500;600&family=Barlow+Condensed:wght@500;600;700&family=JetBrains+Mono:wght@400;500;600;700&display=block" rel="stylesheet"><style>body{margin:0;background:#04070A}</style>';
  for (const f of files) {
    const svg = fs.readFileSync(path.join(dir, f), 'utf8');
    const w = +svg.match(/width="(\d+)"/)[1], h = +svg.match(/height="(\d+)"/)[1];
    const p = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 2 });
    await p.setContent(font + svg); await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(500);
    await p.screenshot({ path: path.join(dir, f.replace('.svg', '.png')) }); await p.close();
  }
  // Übersicht der fünf Bildschirme
  const screens = files.filter(f => !f.startsWith('00'));
  const html = font + '<div style="display:flex;gap:28px;padding:28px;background:#04070A">' + screens.map(f => fs.readFileSync(path.join(dir, f), 'utf8')).join('') + '</div>';
  const p = await b.newPage({ viewport: { width: 28 + screens.length * (390 + 28), height: 844 + 56 }, deviceScaleFactor: 1.5 });
  await p.setContent(html); await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(500);
  await p.screenshot({ path: path.join(dir, 'overview.png') });
  await b.close();
})();
