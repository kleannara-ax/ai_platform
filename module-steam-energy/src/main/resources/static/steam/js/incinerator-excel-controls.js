document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (!page.startsWith("incinerator-")) return;
  if (document.getElementById("incineratorExcelDownload")) return;

  const yearSelect =
    document.getElementById("invoiceYear") ||
    document.getElementById("filterYear");
  if (!yearSelect) return;

  const monthSelectInPage =
    document.getElementById("invoiceMonth") ||
    document.getElementById("filterMonth");

  function yearValue() {
    return String(yearSelect.value || new Date().getFullYear());
  }

  function monthValue() {
    if (monthSelectInPage?.value) {
      return String(parseInt(monthSelectInPage.value, 10) || 1);
    }
    try {
      const saved = JSON.parse(localStorage.getItem("steamlog:shared-period") || "{}");
      if (/^(0[1-9]|1[0-2])$/.test(String(saved.month || ""))) {
        return String(parseInt(saved.month, 10));
      }
    } catch (_) {}
    return String(new Date().getMonth() + 1);
  }

  async function responseMessage(res, fallback) {
    if (window.ExcelUploadErrors?.message) {
      const message = await window.ExcelUploadErrors.message(res);
      if (message) return message;
    }
    const text = await res.text().catch(() => "");
    if (!text) return fallback;
    try {
      const json = JSON.parse(text);
      return json.message || json.error || fallback;
    } catch (_) {
      return text || fallback;
    }
  }

  async function downloadExcel() {
    const year = yearValue();
    const month = monthValue();
    const res = await fetch(`/tables/incinerator/export-excel?year=${encodeURIComponent(year)}&month=${encodeURIComponent(month)}`);
    if (!res.ok) {
      throw new Error(await responseMessage(res, "엑셀 다운로드에 실패했습니다."));
    }

    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `소각로_위탁운영_${year}_${String(month).padStart(2, "0")}.xlsx`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  async function uploadExcel(file, button) {
    if (!file) return;
    const original = button?.textContent;
    if (button) {
      button.disabled = true;
      button.textContent = "업로드 중";
    }

    try {
      const fd = new FormData();
      const uploadYear = yearValue();
      const uploadMonth = monthValue();
      fd.append("file", file);
      fd.append("year", uploadYear);
      fd.append("month", uploadMonth);

      const res = await fetch("/tables/incinerator/import-excel", { method: "POST", body: fd });
      if (!res.ok) {
        throw new Error(await responseMessage(res, "엑셀 업로드에 실패했습니다."));
      }

      const result = await res.json().catch(() => ({}));
      const importedRows = Object.entries(result)
        .filter(([key]) => key.endsWith("_rows"))
        .reduce((sum, [, value]) => sum + (Number(value) || 0), 0);
      if (importedRows <= 0) {
        throw new Error("업로드한 엑셀에서 반영할 데이터를 찾지 못했습니다. 다운로드한 양식의 시트와 셀 위치를 확인해주세요.");
      }

      // 업로드한 year/month 를 공유 기간(localStorage)에 반영 — reload 후 페이지가 같은 월을 보도록.
      try {
        const prev = JSON.parse(localStorage.getItem("steamlog:shared-period") || "{}");
        localStorage.setItem("steamlog:shared-period", JSON.stringify({
          year: Number(uploadYear) || prev.year,
          month: String(uploadMonth).padStart(2, "0"),
        }));
      } catch (_) {}

      alert(`엑셀 업로드 완료: ${importedRows}건 반영`);
      window.location.reload();
    } finally {
      if (button) {
        button.disabled = false;
        button.textContent = original || "업로드";
      }
    }
  }

  const toolbar =
    document.querySelector(".inc-toolbar") ||
    document.querySelector(".accident-top-actions") ||
    document.querySelector(".landing-filter-box");
  if (!toolbar) return;

  const downloadButton = document.createElement("button");
  downloadButton.type = "button";
  downloadButton.id = "incineratorExcelDownload";
  downloadButton.className = "btn-secondary";
  downloadButton.textContent = "엑셀 다운로드";

  const uploadButton = document.createElement("button");
  uploadButton.type = "button";
  uploadButton.id = "incineratorExcelUpload";
  uploadButton.className = "btn-secondary";
  uploadButton.textContent = "엑셀 업로드";

  const fileInput = document.createElement("input");
  fileInput.type = "file";
  fileInput.accept = ".xls,.xlsx,.xlsm";
  fileInput.hidden = true;
  fileInput.id = "incineratorExcelFileInput";

  if (toolbar.classList.contains("accident-top-actions")) {
    downloadButton.classList.add("accident-button");
    uploadButton.classList.add("accident-button");
  }

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
});
