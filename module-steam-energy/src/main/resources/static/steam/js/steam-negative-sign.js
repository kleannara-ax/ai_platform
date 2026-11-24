/* 스팀에너지관리 화면 공통 — 음수 표기를 "-1,234" 대신 "△1,234" 로.
 *
 * 화면에 그려진 글자(텍스트 노드)만 바꾼다. 입력칸(input/textarea)의 값은 그대로 두어 타이핑·저장·파싱에 영향이 없고,
 * 엑셀 내려받기(서버가 숫자로 씀)도 영향이 없다.
 *
 * 규칙: 숫자 바로 앞의 "-"(또는 유니코드 마이너스 "−")만 바꾼다. 앞 글자가 글자(한글 포함)·숫자·점·%·) 이면 건드리지 않아
 *       날짜 "2026-10-30", 설비명 "PM-2", 범위 "1월-2월" 은 그대로다. 빈칸 표시 "—"(em dash) 는 다른 글자라 무관하다.
 *
 * 화면 글자를 다시 숫자로 읽는 곳(계산칸 textContent 파싱)은 SteamNegativeSign.normalize() 로 "△" 를 "-" 로 되돌려 읽는다.
 */
(function (global) {
  "use strict";

  const MARK = "△";                                  // △
  const NEG_RE = /(^|[^\p{L}\p{N}.%)△])[-−](?=\d)/gu; // 숫자 앞의 - (앞이 글자/숫자/점/%/) 가 아닐 때 — 날짜·설비명·"1월-2월" 제외)
  const SKIP = new Set(["INPUT", "TEXTAREA", "SCRIPT", "STYLE", "SELECT", "OPTION", "NOSCRIPT"]);

  function toDisplay(text) {
    return text.replace(NEG_RE, "$1" + MARK);
  }
  /** "△1,234" → "-1,234" — 화면 글자를 숫자로 되읽을 때 쓴다. */
  function normalize(text) {
    return String(text ?? "").replace(/[△▲]/g, "-");
  }

  function skip(el) {
    for (let e = el; e && e.nodeType === 1; e = e.parentElement) {
      if (SKIP.has(e.tagName) || e.isContentEditable) return true;
      if (e.dataset && e.dataset.keepMinus !== undefined) return true;   // data-keep-minus 로 제외 가능
    }
    return false;
  }

  function fixTextNode(node) {
    const text = node.nodeValue;
    if (!text || text.indexOf("-") < 0 && text.indexOf("−") < 0) return;
    if (skip(node.parentElement)) return;
    const next = toDisplay(text);
    if (next !== text) node.nodeValue = next;
  }

  function walk(root) {
    if (!root) return;
    if (root.nodeType === 3) { fixTextNode(root); return; }
    if (root.nodeType !== 1 && root.nodeType !== 9 && root.nodeType !== 11) return;
    if (root.nodeType === 1 && skip(root)) return;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    for (let n = walker.nextNode(); n; n = walker.nextNode()) nodes.push(n);
    nodes.forEach(fixTextNode);
  }

  let observer = null;
  function start() {
    walk(document.body);
    if (observer) return;
    observer = new MutationObserver((records) => {
      for (const r of records) {
        if (r.type === "characterData") fixTextNode(r.target);
        else r.addedNodes.forEach(walk);
      }
    });
    observer.observe(document.body, { childList: true, characterData: true, subtree: true });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();

  global.SteamNegativeSign = { MARK, toDisplay, normalize };
})(window);
