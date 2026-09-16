document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (!page.startsWith("unit-fluidized-") || page.includes("frame")) return;
  if (document.getElementById("summaryExcelDownload")) return;

  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  if (!yearSelect) return;

  function monthValue() {
    const excelMonthSelect = document.getElementById("fluidizedExcelMonth");
    if (excelMonthSelect?.value) return excelMonthSelect.value;
    if (monthSelect?.value) return monthSelect.value;
    try {
      const saved = JSON.parse(localStorage.getItem("steamlog:shared-period") || "{}");
      if (/^(0[1-9]|1[0-2])$/.test(String(saved.month || ""))) return String(saved.month);
    } catch (_) {}
    return String(new Date().getMonth() + 1).padStart(2, "0");
  }

  async function downloadFluidizedExcel() {
    const year = yearSelect.value;
    const month = monthValue();
    const response = await fetch(`/tables/fluidized-summary/export-excel?year=${encodeURIComponent(year)}&month=${encodeURIComponent(month)}`);
    if (!response.ok) throw new Error(await window.ExcelUploadErrors?.message(response) || "엑셀 업로드에 실패했습니다.");
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `유동상_운영내역_${year}_${month}.xlsx`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  async function uploadFluidizedExcel(file, uploadButton) {
    if (!file) return;
    const originalLabel = uploadButton?.textContent;
    if (uploadButton) {
      uploadButton.disabled = true;
      uploadButton.textContent = "업로드 중";
    }
    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("year", yearSelect.value);
      formData.append("month", monthValue());
      const response = await fetch("/tables/fluidized-summary/import-excel", {
        method: "POST",
        body: formData,
      });
      if (!response.ok) throw new Error(await window.ExcelUploadErrors?.message(response) || "엑셀 업로드에 실패했습니다.");
      window.location.reload();
    } finally {
      if (uploadButton) {
        uploadButton.disabled = false;
        uploadButton.textContent = originalLabel || "업로드";
      }
    }
  }

  const existingSrfUpload = document.getElementById("srfExcelUpload");
  if (existingSrfUpload) {
    if (!document.getElementById("fluidizedExcelDownload")) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "landing-filter-action";
      button.id = "fluidizedExcelDownload";
      button.textContent = "다운로드";
      existingSrfUpload.parentElement?.insertBefore(button, existingSrfUpload);
      button.addEventListener("click", async () => {
        try {
          await downloadFluidizedExcel();
        } catch (error) {
          alert(error instanceof Error ? error.message : "엑셀 다운로드 중 오류가 발생했습니다.");
        }
      });
    }
    return;
  }

  const actions =
    document.querySelector("#monthlyExcelActions") ||
    document.querySelector("#improvementTopActions") ||
    document.querySelector("#accidentTopActions") ||
    document.querySelector(".landing-filter-stack") ||
    document.querySelector(".srf-header-actions") ||
    document.querySelector(".monthly-header-actions") ||
    document.querySelector(".improvement-header-actions") ||
    document.querySelector(".accident-header-actions") ||
    document.querySelector(".landing-page-header");
  if (!actions || document.getElementById("fluidizedExcelDownload")) return;

  const box = document.createElement("div");
  box.className = "landing-filter-box landing-upload-box";
  box.setAttribute("aria-label", "엑셀 작업");
  if (actions.id === "improvementTopActions" || actions.id === "accidentTopActions") {
    box.classList.add("fluidized-inline-excel-box");
  }
  const monthControl = monthSelect ? "" : `<select id="fluidizedExcelMonth" aria-label="엑셀 기준 월"></select>`;
  box.innerHTML = `
    <span class="landing-filter-title">엑셀</span>
    ${monthControl}
    <button type="button" class="landing-filter-action" id="fluidizedExcelDownload">다운로드</button>
    <button type="button" class="landing-filter-action" id="fluidizedExcelUpload">업로드</button>
    <input type="file" id="fluidizedExcelFileInput" accept=".xls,.xlsx,.xlsm" hidden />
  `;
  actions.appendChild(box);

  const excelMonthSelect = box.querySelector("#fluidizedExcelMonth");
  if (excelMonthSelect) {
    const selectedMonth = monthValue();
    for (let month = 1; month <= 12; month += 1) {
      const option = document.createElement("option");
      option.value = String(month).padStart(2, "0");
      option.textContent = `${month}월`;
      option.selected = option.value === selectedMonth;
      excelMonthSelect.appendChild(option);
    }
  }

  const downloadButton = box.querySelector("#fluidizedExcelDownload");
  const uploadButton = box.querySelector("#fluidizedExcelUpload");
  const fileInput = box.querySelector("#fluidizedExcelFileInput");

  downloadButton?.addEventListener("click", async () => {
    try {
      await downloadFluidizedExcel();
    } catch (error) {
      alert(error instanceof Error ? error.message : "엑셀 다운로드 중 오류가 발생했습니다.");
    }
  });
  uploadButton?.addEventListener("click", () => fileInput?.click());
  fileInput?.addEventListener("change", async () => {
    const file = fileInput.files?.[0];
    try {
      await uploadFluidizedExcel(file, uploadButton);
    } catch (error) {
      alert(error instanceof Error ? error.message : "엑셀 업로드 중 오류가 발생했습니다.");
    } finally {
      fileInput.value = "";
    }
  });
});
