window.ExcelUploadErrors = (() => {
  async function message(response, fallback = "엑셀 업로드에 실패했습니다.") {
    try {
      const text = await response.text();
      if (!text) return fallback;
      try {
        const json = JSON.parse(text);
        return json.detail || json.message || json.error || text;
      } catch (_) {
        return text;
      }
    } catch (_) {
      return fallback;
    }
  }

  return { message };
})();
