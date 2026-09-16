/**
 * 에너지 회계비용 페이지용 엑셀 업/다운로드 컨트롤.
 *
 * 페이지마다 다음 element 들을 기대:
 *   - 연도 selector: #invoiceYear (또는 #efrYear / #emcYear / #efsYear / #efpYear)
 *   - 다운로드 버튼: #{prefix}ExcelDownload
 *   - 업로드 버튼  : #{prefix}ExcelUpload
 *   - 파일 input   : #{prefix}ExcelFileInput
 *
 * 페이지 prefix 와 엔드포인트는 PAGE_CONFIG 에서 결정한다.
 */
(function () {
  "use strict";

  const PAGE_CONFIG = {
    "energy-accounting-plan.html": {
      prefix: "eap",
      endpoint: "energy-accounting-plan",
      yearSelectId: "invoiceYear",
      filename: (year) => `6. ${year}년 에너지 회계비용 실적.xlsx`,
    },
    "energy-fuel-ratio.html": {
      prefix: "efr",
      endpoint: "energy-fuel-ratio",
      yearSelectId: "invoiceYear",
      filename: (year) => `6. ${year}년 에너지 회계비용 실적.xlsx`,
    },
    "energy-month-compare.html": {
      prefix: "emc",
      endpoint: "energy-month-compare",
      yearSelectId: "emcYear",
      filename: (year) => `6. ${year}년 에너지 회계비용 실적.xlsx`,
    },
    "energy-fuel-summary.html": {
      prefix: "efs",
      endpoint: "energy-fuel-summary",
      yearSelectId: "invoiceYear",
      filename: (year) => `6. ${year}년 에너지 회계비용 실적.xlsx`,
    },
    "energy-fuel-plan.html": {
      prefix: "efp",
      endpoint: "energy-fuel-plan",
      yearSelectId: "invoiceYear",
      filename: (year) => `6. ${year}년 에너지 회계비용 실적.xlsx`,
    },
  };

  function pageKey() {
    const path = window.location.pathname.split("/").pop();
    return path;
  }

  function init() {
    const cfg = PAGE_CONFIG[pageKey()];
    if (!cfg) return;
    const yearSel = document.getElementById(cfg.yearSelectId);
    const downloadBtn = document.getElementById(`${cfg.prefix}ExcelDownload`);
    const uploadBtn = document.getElementById(`${cfg.prefix}ExcelUpload`);
    const fileInput = document.getElementById(`${cfg.prefix}ExcelFileInput`);
    if (!yearSel || !downloadBtn || !uploadBtn || !fileInput) return;

    async function doDownload() {
      const year = yearSel.value;
      const url = `/tables/${cfg.endpoint}/export-excel?year=${encodeURIComponent(year)}`;
      const response = await fetch(url);
      if (!response.ok) {
        const msg = (await window.ExcelUploadErrors?.message(response)) || "엑셀 다운로드에 실패했습니다.";
        throw new Error(msg);
      }
      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = objectUrl;
      a.download = cfg.filename(year);
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(objectUrl);
    }

    async function doUpload(file) {
      if (!file) return;
      const originalLabel = uploadBtn.textContent;
      uploadBtn.disabled = true;
      uploadBtn.textContent = "업로드 중";
      try {
        const formData = new FormData();
        formData.append("file", file);
        formData.append("year", yearSel.value);
        const response = await fetch(`/tables/${cfg.endpoint}/import-excel`, {
          method: "POST",
          body: formData,
        });
        if (!response.ok) {
          const msg = (await window.ExcelUploadErrors?.message(response)) || "엑셀 업로드에 실패했습니다.";
          throw new Error(msg);
        }
        window.location.reload();
      } finally {
        uploadBtn.disabled = false;
        uploadBtn.textContent = originalLabel;
      }
    }

    downloadBtn.addEventListener("click", async () => {
      try {
        await doDownload();
      } catch (e) {
        alert(e instanceof Error ? e.message : "엑셀 다운로드 중 오류가 발생했습니다.");
      }
    });
    uploadBtn.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", async () => {
      const file = fileInput.files?.[0];
      try {
        await doUpload(file);
      } catch (e) {
        alert(e instanceof Error ? e.message : "엑셀 업로드 중 오류가 발생했습니다.");
      } finally {
        fileInput.value = "";
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
