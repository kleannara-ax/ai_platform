document.addEventListener("DOMContentLoaded", async () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "unit-fluidized-monthly-frame.html") return;

  const root = document.getElementById("monthlyFrameRoot");
  if (!root || typeof window.renderFluidizedMonthlyView !== "function") return;

  const params = new URLSearchParams(window.location.search);
  await window.renderFluidizedMonthlyView({
    root,
    year: params.get("year") || "2025",
    month: params.get("month") || "",
    section: params.get("section") || "operating",
    sub: params.get("sub") || "incinerator",
  });

  function notifyHeight() {
    const height = Math.max(
      document.body.scrollHeight,
      document.documentElement.scrollHeight,
      root.scrollHeight
    );
    window.parent.postMessage({
      type: "fluidized-monthly-frame-size",
      height,
    }, window.location.origin);
  }

  requestAnimationFrame(notifyHeight);
  window.addEventListener("load", notifyHeight);
  window.addEventListener("resize", notifyHeight);
});
