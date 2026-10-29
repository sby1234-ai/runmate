// node web/tests/e2e.js <기준 주소> [스크린샷 폴더] — 실제 브라우저로 웹 화면을 점검합니다
const { chromium } = require('playwright');
const BASE = process.argv[2] || 'http://127.0.0.1:8766/'; const SHOT = process.argv[3] || '.';
let fail = 0; const check = (n, c, x) => { console.log((c ? 'PASS ' : 'FAIL ') + n + (x ? '  → ' + x : '')); if (!c) fail++; };
const gpx = (pts, ext) => `<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1" xmlns:rm="https://github.com/sby1234-ai/runmate"><trk><name>t</name><trkseg>` +
  pts.map(p => `<trkpt lat="${p[0]}" lon="${p[1]}"><time>${new Date(p[2]).toISOString()}</time>${ext ? `<extensions><rm:motion>${p[3]}</rm:motion></extensions>` : ''}</trkpt>`).join('') + `</trkseg></trk></gpx>`;
const line = (n, v, motion) => Array.from({ length: n }, (_, i) => [35.85 + v * i * 2 / 111195, 128.55, Date.UTC(2026, 9, 1) + i * 2000, motion]);
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 390, height: 844 } }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e))); p.on('console', m => m.type() === 'error' && errs.push(m.text()));
  const external = []; p.on('request', r => { if (!r.url().startsWith(BASE)) external.push(r.url()); });
  await p.goto(BASE); await p.waitForSelector('#result:not([hidden])');
  check('처음 열면 예시 기록이 보임', (await p.textContent('#rec-name')).includes('만든 데이터'));
  check('예시: 거리 5km 안팎', Math.abs(parseFloat(await p.textContent('#dist')) - 5.0) < 0.3, await p.textContent('#dist'));
  check('예시: 뛴 거리와 걸은 거리 둘 다 있음', parseFloat(await p.textContent('#run-d')) > 3 && parseFloat(await p.textContent('#walk-d')) > 0.5);
  check('경로 선이 색별로 그려짐', (await p.$$('#route polyline')).length >= 3);
  check('시간 흐름 띠', (await p.$$('#timeline span')).length >= 3);
  check('1km 구간 5개', (await p.$$('#splits li')).length === 5);
  await p.screenshot({ path: SHOT + '/rm_web_top.png' });
  // 다른 앱 GPX(걷기·뛰기 표시 없음) → 속도로 나눔
  await p.setInputFiles('#file', { name: 'other.gpx', mimeType: 'application/gpx+xml', buffer: Buffer.from(gpx(line(120, 1.4).concat(line(150, 3.0).map((q, i) => [35.85 + (1.4 * 238 + 3.0 * i * 2) / 111195, 128.55, Date.UTC(2026, 9, 1) + (120 + i) * 2000]))))});
  await p.waitForFunction(() => document.getElementById('rec-name').textContent === 'other.gpx');
  check('다른 앱 GPX → 앱 규칙으로 나눔', (await p.textContent('#status')).includes('같은 규칙') && parseFloat(await p.textContent('#run-d')) > 0.7 && parseFloat(await p.textContent('#walk-d')) > 0.2,
    (await p.textContent('#run-d')) + '/' + (await p.textContent('#walk-d')));
  // 런메이트 GPX(표시 있음) → 그대로 사용
  await p.setInputFiles('#file', { name: 'runmate.gpx', mimeType: 'application/gpx+xml', buffer: Buffer.from(gpx(line(100, 1.4, 'run'), true)) });
  await p.waitForFunction(() => document.getElementById('rec-name').textContent === 'runmate.gpx');
  check('런메이트 GPX → 앱의 구분 그대로', (await p.textContent('#status')).includes('그대로') && parseFloat(await p.textContent('#walk-d')) === 0);
  // 잘못된 파일
  for (const [n, body] of [['bad.gpx', 'hello'], ['empty.gpx', ''], ['notime.gpx', '<gpx><trk><trkseg><trkpt lat="1" lon="2"/></trkseg></trk></gpx>'], ['x.gpx', '<gpx><trkpt lat="999" lon="2"><time>x</time></trkpt><script>alert(1)</script></gpx>']]) {
    await p.setInputFiles('#file', { name: n, mimeType: 'text/xml', buffer: Buffer.from(body) });
    await p.waitForTimeout(150);
    check('잘못된 파일에도 멈추지 않음: ' + n, (await p.getAttribute('#status', 'class')).includes('err'));
  }
  const ov = await p.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  check('390px 가로 넘침 없음', ov <= 0, String(ov));
  check('외부 서버 요청 없음', external.length === 0, external.join(' '));
  check('콘솔 오류 없음', errs.length === 0, errs.join(' | '));
  await b.close(); console.log(fail ? `\n실패 ${fail}건` : '\n모두 통과'); process.exit(fail ? 1 : 0);
})();
