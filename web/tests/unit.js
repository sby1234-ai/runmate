// node web/tests/unit.js — 앱의 단위 테스트(MotionClassifierTest, RunStatsTest)와 같은 경우를 웹 로직에 돌립니다
const RM = require('../logic.js');
let fail = 0;
const eq = (n, a, b, tol = 0) => { const ok = typeof a === 'number' ? Math.abs(a - b) <= tol : a === b; console.log((ok ? 'PASS ' : 'FAIL ') + n + (ok ? '' : `  → ${a} ≠ ${b}`)); if (!ok) fail++; };
const rep = (n, f) => { for (let i = 0; i < n; i++) f(); };
{ const c = new RM.Classifier(); rep(5, () => c.onSpeed(1.4)); eq('1.4m/s → 걷기', c.current, 'walk');
  rep(5, () => c.onSpeed(3.0)); eq('3.0m/s → 뛰기', c.current, 'run'); rep(5, () => c.onSpeed(0.1)); eq('0.1m/s → 멈춤', c.current, 'still'); }
{ const c = new RM.Classifier(); rep(5, () => c.onSpeed(3.0)); rep(5, () => c.onSpeed(2.1)); eq('경계 여유 안 → 뛰기 유지', c.current, 'run');
  rep(5, () => c.onSpeed(1.8)); eq('1.8m/s → 걷기', c.current, 'walk'); }
{ const c = new RM.Classifier(); rep(5, () => c.onSpeed(1.3)); c.onSpeed(40); eq('튀는 속도 무시', c.current, 'walk'); }
const line = (meters, seconds, motion, startT = 0, startLat = 35.85) => {
  const step = meters / 111195 / seconds; const out = [];
  for (let i = 0; i <= seconds; i++) out.push({ time: startT + i * 1000, lat: startLat + step * i, lon: 128.55, motion });
  return out;
};
{ const s = RM.stats(line(2010, 603, 'run')); eq('거리 2.01km', s.distance, 2010, 5); eq('움직인 시간', s.moving, 603000);
  eq('1km 구간 2개', s.splits.length, 2); eq('첫 구간 300초', s.splits[0], 300000, 1500); eq('페이스 5분', s.pace, 300, 2); }
{ const run = line(1000, 300, 'run'); const walk = line(500, 300, 'walk', 301000, run[run.length - 1].lat).slice(1);
  const s = RM.stats(run.concat(walk)); eq('뛴 거리', s.runDistance, 1000, 5); eq('걸은 거리', s.walkDistance, 505, 15); eq('뛴 시간', s.runTime, 300000); }
{ const a = line(500, 150, 'run'); const L = a[a.length - 1];
  const still = { time: L.time + 60000, lat: L.lat, lon: L.lon, motion: 'still' };
  const b = line(500, 150, 'run', still.time + 1000, still.lat);
  eq('끊긴 시간 제외', RM.stats(a.concat([still], b)).moving, 301000); }
{ const s = RM.stats(line(0, 0, 'still')); eq('점 하나 → 0', s.distance, 0); eq('점 하나 → 페이스 없음', s.pace, null); }
eq('형식: 시간', RM.duration(3725000), '1:02:05'); eq('형식: 페이스', RM.pace(342), "5'42\""); eq('형식: km', RM.km(2012), '2.01');
console.log(fail ? `\n실패 ${fail}건` : '\n모두 통과'); process.exit(fail ? 1 : 0);
