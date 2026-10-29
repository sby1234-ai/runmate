/* 런메이트 웹 — 앱(app/src/main/java/.../logic)의 규칙을 그대로 옮긴 순수 함수입니다.
 * 걷기·뛰기 기준, 1km 구간 계산, GPS 끊김 처리 값이 앱과 같습니다. 브라우저(window.RM)와 Node 양쪽에서 씁니다.
 */
(function (root) {
  "use strict";
  var MOTION = { STILL: "still", WALK: "walk", RUN: "run" };
  var LABEL = { still: "멈춤", walk: "걷기", run: "뛰기" };

  // 앱의 MotionClassifier와 같은 규칙: 뛰기 기준 2.2 m/s(1km 7분 35초), 멈춤 0.5 m/s, 경계 여유 0.2 m/s, 최근 5개 속도의 중앙값
  function Classifier(opt) {
    opt = opt || {};
    this.runSpeed = opt.runSpeed != null ? opt.runSpeed : 2.2;
    this.stillSpeed = opt.stillSpeed != null ? opt.stillSpeed : 0.5;
    this.hys = 0.2;
    this.speeds = [];
    this.current = MOTION.STILL;
  }
  Classifier.prototype.onSpeed = function (v) {
    if (v != null && v >= 0 && v < 12) {           // 시속 43km가 넘는 튀는 값은 버림
      this.speeds.push(v);
      if (this.speeds.length > 5) this.speeds.shift();
    }
    var m = median(this.speeds);
    if (m == null) return this.current;
    var c = this.current, r = this.runSpeed, s = this.stillSpeed, h = this.hys;
    if (c === MOTION.RUN) this.current = m < s ? MOTION.STILL : (m < r - h ? MOTION.WALK : MOTION.RUN);
    else if (c === MOTION.WALK) this.current = m >= r + h ? MOTION.RUN : (m < s ? MOTION.STILL : MOTION.WALK);
    else this.current = m >= r + h ? MOTION.RUN : (m >= s + h ? MOTION.WALK : MOTION.STILL);
    return this.current;
  };
  function median(a) {
    if (!a.length) return null;
    var s = a.slice().sort(function (x, y) { return x - y; }), k = s.length >> 1;
    return s.length % 2 ? s[k] : (s[k - 1] + s[k]) / 2;
  }

  // 두 지점 사이 거리(m), 하버사인 공식
  function distance(lat1, lon1, lat2, lon2) {
    var R = 6371000, rad = Math.PI / 180;
    var dLat = (lat2 - lat1) * rad, dLon = (lon2 - lon1) * rad;
    var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 2 * R * Math.asin(Math.sqrt(Math.min(1, Math.max(0, a))));
  }

  // 앱의 RunStats와 같은 규칙: 30초 넘게 끊긴 구간은 빼고, 1km 지점을 지난 시각은 구간 안에서 나눠 계산
  var GAP_MS = 30000;
  function stats(points) {
    var out = { distance: 0, elapsed: 0, moving: 0, runDistance: 0, runTime: 0, walkDistance: 0, walkTime: 0, splits: [] };
    if (!points || points.length < 2) return finish(out);
    var nextKm = 1000, splitStart = points[0].time;
    for (var i = 1; i < points.length; i++) {
      var a = points[i - 1], b = points[i], dt = b.time - a.time;
      if (dt <= 0) continue;
      var d = distance(a.lat, a.lon, b.lat, b.lon);
      if (dt > GAP_MS) continue;
      out.distance += d;
      if (b.motion === MOTION.RUN) { out.runDistance += d; out.runTime += dt; out.moving += dt; }
      else if (b.motion === MOTION.WALK) { out.walkDistance += d; out.walkTime += dt; out.moving += dt; }
      while (out.distance >= nextKm) {
        var over = out.distance - nextKm, frac = d > 0 ? 1 - over / d : 1;
        var crossAt = a.time + Math.floor(dt * frac);
        out.splits.push(crossAt - splitStart);
        splitStart = crossAt; nextKm += 1000;
      }
    }
    out.elapsed = points[points.length - 1].time - points[0].time;
    return finish(out);
  }
  function finish(o) {
    o.pace = o.distance < 50 ? null : o.moving / 1000 / (o.distance / 1000);
    o.runPace = o.runDistance < 50 ? null : o.runTime / 1000 / (o.runDistance / 1000);
    return o;
  }

  // GPX 읽기. 런메이트 앱이 적어 둔 걷기·뛰기 표시(rm:motion)가 있으면 그대로 쓰고,
  // 없으면(다른 앱의 GPX) 앞 지점과의 거리·시간으로 속도를 구해 앱과 같은 규칙으로 나눕니다.
  function parseGpx(text, DOMParserImpl) {
    var P = DOMParserImpl || (typeof DOMParser !== "undefined" ? DOMParser : null);
    if (!P) throw new Error("GPX를 읽을 수 없는 환경입니다.");
    var doc = new P().parseFromString(String(text || ""), "application/xml");
    if (!doc || doc.getElementsByTagName("parsererror").length) throw new Error("GPX 형식이 아닙니다.");
    var nodes = doc.getElementsByTagName("trkpt");
    if (!nodes.length) nodes = doc.getElementsByTagName("rtept");
    var pts = [], hadMotion = false;
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      var lat = parseFloat(n.getAttribute("lat")), lon = parseFloat(n.getAttribute("lon"));
      var tn = n.getElementsByTagName("time")[0];
      var t = tn ? Date.parse(tn.textContent) : NaN;
      if (!isFinite(lat) || !isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180 || !isFinite(t)) continue;
      var mn = n.getElementsByTagName("rm:motion")[0] || n.getElementsByTagNameNS && n.getElementsByTagNameNS("*", "motion")[0];
      var m = mn ? String(mn.textContent).trim().toLowerCase() : "";
      if (m === "run" || m === "walk" || m === "still") hadMotion = true; else m = "";
      pts.push({ time: t, lat: lat, lon: lon, motion: m });
    }
    pts.sort(function (a, b) { return a.time - b.time; });
    if (!pts.length) throw new Error("시간이 적힌 GPS 지점이 없습니다.");
    if (!hadMotion) classify(pts);
    else pts.forEach(function (p) { if (!p.motion) p.motion = MOTION.STILL; });
    var nameNode = doc.getElementsByTagName("name")[0];
    return { points: pts, fromRunMate: hadMotion, name: nameNode ? nameNode.textContent : "" };
  }
  function classify(pts) {
    var c = new Classifier();
    pts[0].motion = MOTION.STILL;
    for (var i = 1; i < pts.length; i++) {
      var dt = (pts[i].time - pts[i - 1].time) / 1000;
      var v = dt > 0 && dt <= 30 ? distance(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon) / dt : null;
      pts[i].motion = c.onSpeed(v);
    }
  }

  // 표시 형식 (앱의 Format과 같음)
  function km(m) { return (m / 1000).toFixed(2); }
  function duration(ms) {
    var s = Math.max(0, Math.floor(ms / 1000)), h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
    var p = function (n) { return (n < 10 ? "0" : "") + n; };
    return h > 0 ? h + ":" + p(m) + ":" + p(x) : m + ":" + p(x);
  }
  function pace(sec) {
    if (sec == null || !isFinite(sec) || sec > 59 * 60) return "--'--\"";
    var t = Math.round(sec);
    return Math.floor(t / 60) + "'" + (t % 60 < 10 ? "0" : "") + (t % 60) + "\"";
  }

  var api = { MOTION: MOTION, LABEL: LABEL, Classifier: Classifier, distance: distance, stats: stats,
    parseGpx: parseGpx, classify: classify, km: km, duration: duration, pace: pace };
  if (typeof module !== "undefined" && module.exports) module.exports = api; else root.RM = api;
})(typeof window !== "undefined" ? window : this);
