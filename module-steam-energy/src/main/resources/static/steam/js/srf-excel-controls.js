/* 스팀에너지관리 — SRF 보일러 인보이스 통합본 엑셀 다운로드/업로드 버튼 (SRF 인보이스 화면 공용).
 *
 * 통합본(KleanNara SRF Boiler AOT invoice(N월).xls) 하나에 Summary(VK) · Client Invoice · 운영내역 · 월별 운영내역 ·
 * Accumulated · BSC · Adjustment Factor · 사고이력이 다 들어 있어서, 어느 화면에서 올리거나 내려받아도 같은 파일이다.
 *
 *   다운로드  GET  /steam-energy-api/srf-invoice-excel/export-excel?year=&month=
 *   업로드    POST /steam-energy-api/srf-invoice-excel/import-excel  (file) — 기간은 엑셀의 Invoice for the period 로 정해진다
 *
 * 업로드가 끝나면 그 기간으로 공유 기간을 맞추고 화면을 다시 불러온 뒤, 반영 결과와 확인할 점(경고)을 보여 준다.
 * 연도만 고르는 화면(월별 · Accumulated · BSC · Adjustment · 사고이력)에는 내려받을 월을 고르는 칸을 함께 붙인다.
 *
 * 필요: platform-iframe-compat.js (토큰), excel-upload-errors.js (오류 글자)
 */
document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (!page.startsWith("srf-boiler-invoice")) return;
  if (document.getElementById("srfExcelDownload")) return;

  const API = "/steam-energy-api/srf-invoice-excel";
  const PERIOD_KEY = "steamlog:shared-period";
  const RESULT_KEY = "steamlog:srf-excel-result";
  // 회계연도(6월 시작)로 보는 화면 — 연도 칸이 "6월이 속한 해"다
  const FISCAL_PAGES = ["srf-boiler-invoice-monthly.html", "srf-boiler-invoice-accumulated.html"];
  const SHEET_NAMES = {
    summary: "Summary(VK)", clientInvoice: "Client Invoice", operation: "운영내역", monthly: "월별 운영내역",
    accumulated: "Accumulated", bsc: "BSC", adjustment: "Adjustment Factor", incident: "사고이력",
  };

  const toolbar =
    document.querySelector(".invoice-toolbar") ||
    document.querySelector(".cli-toolbar") ||
    document.querySelector(".mon-toolbar") ||
    // 사고이력: accident-top-actions 는 화면이 그릴 때마다 비우므로 그 옆(바깥 묶음)에 붙인다
    document.querySelector(".accident-left-actions");
  const yearSelect = document.getElementById("invoiceYear") || document.getElementById("filterYear");
  if (!toolbar || !yearSelect) return;
  const monthSelectInPage = document.getElementById("invoiceMonth");
  const isFiscalPage = FISCAL_PAGES.includes(page);

  function readPeriod() {
    try {
      return JSON.parse(localStorage.getItem(PERIOD_KEY) || "{}");
    } catch (_) {
      return {};
    }
  }

  // 연도만 고르는 화면: 엑셀 기준 월
  let excelMonthSelect = null;
  if (!monthSelectInPage) {
    excelMonthSelect = document.createElement("select");
    excelMonthSelect.id = "srfExcelMonth";
    excelMonthSelect.title = "엑셀로 내려받을 인보이스 월";
    excelMonthSelect.setAttribute("aria-label", "엑셀 기준 월");
    for (let month = 1; month <= 12; month += 1) {
      const option = document.createElement("option");
      option.value = String(month);
      option.textContent = `엑셀 ${month}월`;
      excelMonthSelect.appendChild(option);
    }
    const savedMonth = parseInt(readPeriod().month, 10);
    excelMonthSelect.value = String(savedMonth >= 1 && savedMonth <= 12 ? savedMonth : new Date().getMonth() + 1);
  }

  /** 내려받을 인보이스 기간 — 회계연도 화면에서 1~5월을 고르면 다음 해다. */
  function period() {
    const month = parseInt((monthSelectInPage || excelMonthSelect).value, 10) || new Date().getMonth() + 1;
    let year = parseInt(yearSelect.value, 10) || new Date().getFullYear();
    if (isFiscalPage && month <= 5) year += 1;
    return { year, month };
  }

  async function responseMessage(res, fallback) {
    if (window.ExcelUploadErrors?.message) {
      const message = await window.ExcelUploadErrors.message(res, fallback);
      if (message) return message;
    }
    return fallback;
  }

  async function downloadExcel() {
    const { year, month } = period();
    const res = await fetch(`${API}/export-excel?year=${encodeURIComponent(year)}&month=${encodeURIComponent(month)}`);
    if (!res.ok) throw new Error(await responseMessage(res, "엑셀 다운로드에 실패했습니다."));
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `KleanNara SRF Boiler AOT invoice(${year}년 ${month}월).xls`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  async function uploadExcel(file, button) {
    if (!file) return;
    const original = button.textContent;
    button.disabled = true;
    button.textContent = "업로드 중";
    try {
      const fd = new FormData();
      fd.append("file", file);
      // 기간은 보내지 않는다 — 엑셀의 Invoice for the period 가 기준이다 (다른 달 화면을 보고 있어도 그 달로 들어간다)
      const res = await fetch(`${API}/import-excel`, { method: "POST", body: fd });
      if (!res.ok) throw new Error(await responseMessage(res, "엑셀 업로드에 실패했습니다."));
      const result = ((await res.json().catch(() => ({}))) || {}).data || {};
      if (!result.year || !result.month) throw new Error("업로드 결과를 읽지 못했습니다.");

      // 올린 기간으로 공유 기간을 맞춘다 — 다시 불러온 화면이 그 달(회계연도 화면은 그 회계연도)을 보도록
      try {
        localStorage.setItem(PERIOD_KEY, JSON.stringify({
          year: isFiscalPage ? (result.fiscalYear || result.year) : result.year,
          month: String(result.month).padStart(2, "0"),
        }));
        sessionStorage.setItem(RESULT_KEY, JSON.stringify({ file: file.name, result }));
      } catch (_) {}
      window.location.reload();
    } finally {
      button.disabled = false;
      button.textContent = original || "엑셀 업로드";
    }
  }

  function makeButton(id, label) {
    const button = document.createElement("button");
    button.type = "button";
    button.id = id;
    button.className = "btn-secondary";
    button.textContent = label;
    if (toolbar.classList.contains("accident-left-actions")) button.classList.add("accident-button");
    return button;
  }

  const downloadButton = makeButton("srfExcelDownload", "엑셀 다운로드");
  const uploadButton = makeButton("srfExcelUpload", "엑셀 업로드");
  uploadButton.title = "KleanNara SRF Boiler AOT invoice(N월).xls 통합본을 올리면 SRF 인보이스 화면 전체에 반영됩니다";
  const fileInput = document.createElement("input");
  fileInput.type = "file";
  fileInput.accept = ".xls,.xlsx,.xlsm";
  fileInput.hidden = true;
  fileInput.id = "srfExcelFileInput";

  if (excelMonthSelect) toolbar.appendChild(excelMonthSelect);
  toolbar.appendChild(downloadButton);
  toolbar.appendChild(uploadButton);
  toolbar.appendChild(fileInput);

  downloadButton.addEventListener("click", async () => {
    try {
      await downloadExcel();
    } catch (e) {
      alert(e instanceof Error ? e.message : "엑셀 다운로드 중 오류가 발생했습니다.");
    }
  });
  uploadButton.addEventListener("click", () => fileInput.click());
  fileInput.addEventListener("change", async () => {
    const file = fileInput.files?.[0];
    try {
      await uploadExcel(file, uploadButton);
    } catch (e) {
      alert(e instanceof Error ? e.message : "엑셀 업로드 중 오류가 발생했습니다.");
    } finally {
      fileInput.value = "";
    }
  });

  showLastResult();

  /** 업로드 직후 다시 불러온 화면에서 반영 결과와 경고를 한 번 보여 준다. */
  function showLastResult() {
    let saved = null;
    try {
      saved = JSON.parse(sessionStorage.getItem(RESULT_KEY) || "null");
      sessionStorage.removeItem(RESULT_KEY);
    } catch (_) {}
    if (!saved || !saved.result) return;
    const result = saved.result;
    const warnings = Array.isArray(result.warnings) ? result.warnings : [];
    const sheets = result.sheets || {};

    const panel = document.createElement("div");
    panel.id = "srfExcelResult";
    panel.setAttribute("role", "status");
    panel.style.cssText = "position:fixed;right:20px;bottom:20px;z-index:9999;width:min(560px,calc(100vw - 40px));max-height:70vh;"
      + "overflow:auto;background:#fff;border:1px solid #c9d6e6;border-radius:12px;box-shadow:0 12px 32px rgba(15,35,65,.22);"
      + "padding:16px 18px;font:500 12px/1.6 Inter,'Apple SD Gothic Neo',sans-serif;color:#1f2a44;";

    const title = document.createElement("div");
    title.style.cssText = "font-weight:700;font-size:13px;margin-bottom:6px;";
    title.textContent = `엑셀 업로드 완료 — ${result.year}년 ${result.month}월 (${Number(result.imported) || 0}칸 반영)`;
    panel.appendChild(title);

    const counts = document.createElement("div");
    counts.style.cssText = "color:#4a5a75;margin-bottom:8px;";
    counts.textContent = Object.keys(SHEET_NAMES)
      .filter((key) => key in sheets)
      .map((key) => `${SHEET_NAMES[key]} ${sheets[key]}`)
      .join(" · ");
    panel.appendChild(counts);

    if (warnings.length) {
      const heading = document.createElement("div");
      heading.style.cssText = "font-weight:700;color:#a15c00;margin-bottom:4px;";
      heading.textContent = `확인할 점 ${warnings.length}건`;
      panel.appendChild(heading);
      const list = document.createElement("ul");
      list.style.cssText = "margin:0 0 8px;padding-left:18px;";
      warnings.forEach((warning) => {
        const item = document.createElement("li");
        item.textContent = warning;
        list.appendChild(item);
      });
      panel.appendChild(list);
    }

    const close = document.createElement("button");
    close.type = "button";
    close.textContent = "닫기";
    close.style.cssText = "height:30px;padding:0 14px;border:1px solid #1f4f82;border-radius:8px;background:#1f4f82;color:#fff;"
      + "font:600 12px Inter,'Apple SD Gothic Neo',sans-serif;cursor:pointer;";
    close.addEventListener("click", () => panel.remove());
    panel.appendChild(close);
    document.body.appendChild(panel);
  }
});
