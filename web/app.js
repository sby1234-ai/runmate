/* 런메이트 웹 — GPX 기록 파일을 열어 앱과 같은 화면(요약·경로·시간 흐름·1km 구간)으로 보여 줍니다.
 * 파일은 브라우저 안에서만 읽고, 외부로 보내거나 지도 서버를 부르지 않습니다.
 */
(function () {
  "use strict";
  var $ = function (id) { return document.getElementById(id); };
  var MAX_BYTES = 30 * 1024 * 1024;
  var COLOR = { run: "var(--run)", walk: "var(--walk)", still: "var(--still)" };

  function setStatus(t, err) { var s = $("status"); s.textContent = t || ""; s.className = "status" + (err ? " err" : ""); }

  function show(rec, label, auto) {
    var pts = rec.points;
    if (pts.length < 2) throw new Error("GPS 지점이 2개 이상 있어야 기록을 계산할 수 있습니다.");
    var s = RM.stats(pts);
    $("rec-name").textContent = label;
    $("time").textContent = RM.duration(s.elapsed);
    $("dist").textContent = RM.km(s.distance);
    $("pace").textContent = RM.pace(s.pace);
    $("run-d").textContent = RM.km(s.runDistance);
    $("walk-d").textContent = RM.km(s.walkDistance);
    drawRoute(pts);
    drawTimeline(pts);
    drawSplits(s.splits);
    $("result").hidden = false;
    setStatus("지점 " + pts.length.toLocaleString("ko-KR") + "개를 읽었습니다. " +
      (rec.fromRunMate ? "런메이트 앱이 나눈 걷기·뛰기를 그대로 보여 줍니다." : "앱과 같은 규칙(1km 7분 35초 기준)으로 걷기·뛰기를 나눴습니다."));
    if (!auto) $("result").scrollIntoView({ behavior: "smooth", block: "start" });
  }

  // 위도·경도를 화면 좌표로 바꿔, 같은 상태가 이어지는 구간마다 색을 달리해 그립니다
  function drawRoute(pts) {
    var NS = "http://www.w3.org/2000/svg", box = $("route");
    box.textContent = "";
    var W = 600, H = 380, pad = 22;
    var lat0 = pts[0].lat * Math.PI / 180, k = Math.cos(lat0);
    var xs = pts.map(function (p) { return p.lon * k; }), ys = pts.map(function (p) { return p.lat; });
    var minX = Math.min.apply(null, xs), maxX = Math.max.apply(null, xs), minY = Math.min.apply(null, ys), maxY = Math.max.apply(null, ys);
    var sc = Math.min((W - 2 * pad) / ((maxX - minX) || 1e-9), (H - 2 * pad) / ((maxY - minY) || 1e-9));
    var ox = (W - (maxX - minX) * sc) / 2, oy = (H - (maxY - minY) * sc) / 2;
    var X = function (i) { return (ox + (xs[i] - minX) * sc).toFixed(1); };
    var Y = function (i) { return (H - oy - (ys[i] - minY) * sc).toFixed(1); };
    var svg = document.createElementNS(NS, "svg");
    svg.setAttribute("viewBox", "0 0 " + W + " " + H);
    var seg = [0], cur = pts[1].motion;
    function flush(end) {
      if (seg.length < 2) return;
      var pl = document.createElementNS(NS, "polyline");
      pl.setAttribute("points", seg.map(function (i) { return X(i) + "," + Y(i); }).join(" "));
      pl.setAttribute("fill", "none"); pl.setAttribute("stroke", COLOR[cur] || COLOR.still);
      pl.setAttribute("stroke-width", cur === "still" ? "3" : "4.5");
      pl.setAttribute("stroke-linecap", "round"); pl.setAttribute("stroke-linejoin", "round");
      svg.appendChild(pl);
    }
    for (var i = 1; i < pts.length; i++) {
      if (pts[i].motion !== cur) { flush(); seg = [i - 1]; cur = pts[i].motion; }
      seg.push(i);
    }
    flush();
    [[0, "출발"], [pts.length - 1, "도착"]].forEach(function (e) {
      var c = document.createElementNS(NS, "circle");
      c.setAttribute("cx", X(e[0])); c.setAttribute("cy", Y(e[0])); c.setAttribute("r", "7");
      c.setAttribute("fill", e[1] === "출발" ? "var(--bg)" : "var(--ink)"); c.setAttribute("stroke", "var(--ink)"); c.setAttribute("stroke-width", "3");
      svg.appendChild(c);
    });
    box.appendChild(svg);
  }

  // 시간 순서대로 걷기·뛰기·멈춤이 차지한 비율을 띠로 보여 줍니다(30초 넘게 끊긴 구간은 멈춤으로 칠합니다)
  function drawTimeline(pts) {
    var bar = $("timeline"), total = pts[pts.length - 1].time - pts[0].time;
    bar.textContent = "";
    if (total <= 0) return;
    var runs = [], cur = null;
    for (var i = 1; i < pts.length; i++) {
      var dt = pts[i].time - pts[i - 1].time; if (dt <= 0) continue;
      var m = dt > 30000 ? "still" : pts[i].motion;
      if (cur && cur.m === m) cur.t += dt; else { cur = { m: m, t: dt }; runs.push(cur); }
    }
    runs.forEach(function (r) {
      var s = document.createElement("span");
      s.className = r.m; s.style.width = (r.t / total * 100).toFixed(3) + "%";
      s.title = RM.LABEL[r.m] + " " + RM.duration(r.t);
      bar.appendChild(s);
    });
    var t0 = new Date(pts[0].time), t1 = new Date(pts[pts.length - 1].time);
    var hm = function (d) { return d.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" }); };
    $("tl-start").textContent = hm(t0) + " 출발";
    $("tl-end").textContent = hm(t1) + " 도착";
  }

  function drawSplits(splits) {
    var ol = $("splits");
    ol.textContent = "";
    if (!splits.length) {
      var li = document.createElement("li"); li.className = "empty";
      li.textContent = "1km를 넘지 않아 구간 기록이 없습니다."; ol.appendChild(li); return;
    }
    var max = Math.max.apply(null, splits), min = Math.min.apply(null, splits);
    splits.forEach(function (ms, i) {
      var li = document.createElement("li");
      var n = document.createElement("span"); n.textContent = (i + 1) + "km";
      var b = document.createElement("span"); b.className = "bar" + (ms === min ? " fast" : "");
      b.style.width = Math.max(12, ms / max * 100).toFixed(1) + "%";
      var t = document.createElement("span"); t.className = "t"; t.textContent = RM.pace(ms / 1000);
      li.appendChild(n); li.appendChild(b); li.appendChild(t);
      ol.appendChild(li);
    });
  }

  function load(text, label, auto) {
    try { show(RM.parseGpx(text), label, auto); }
    catch (e) { setStatus("이 파일은 열 수 없습니다. " + (e && e.message ? e.message : "") + " 러닝 앱에서 GPX로 내보낸 파일을 골라 주세요.", true); }
  }
  function readFile(f) {
    if (!f) return;
    if (f.size > MAX_BYTES) { setStatus("파일이 너무 큽니다(30MB까지). 기록 하나씩 내보내 주세요.", true); return; }
    var r = new FileReader();
    r.onload = function () { load(String(r.result), f.name); };
    r.onerror = function () { setStatus("파일을 읽지 못했습니다. 다시 골라 주세요.", true); };
    r.readAsText(f);
  }

  $("file").addEventListener("change", function (e) { readFile(e.target.files && e.target.files[0]); e.target.value = ""; });
  var drop = $("drop");
  ["dragenter", "dragover"].forEach(function (ev) { drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.add("over"); }); });
  ["dragleave", "drop"].forEach(function (ev) { drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.remove("over"); }); });
  drop.addEventListener("drop", function (e) { readFile(e.dataTransfer && e.dataTransfer.files[0]); });

  function sample(auto) {
    fetch("sample.gpx").then(function (r) { if (!r.ok) throw new Error(); return r.text(); })
      .then(function (t) { load(t, "예시 기록: 화면 설명용으로 만든 데이터입니다(실제 사람의 기록 아님).", auto === true); })
      .catch(function () { setStatus("예시 기록을 불러오지 못했습니다. 인터넷 주소(https)로 열었는지 확인해 주세요.", true); });
  }
  $("btn-sample").addEventListener("click", function () { sample(false); });
  sample(true);   // 처음 열면 예시 기록을 바로 보여 줍니다
})();
