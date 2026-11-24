(() => {
  function isTextLikeInput(element) {
    if (!(element instanceof HTMLElement)) return false;
    if (element instanceof HTMLTextAreaElement) return true;
    if (element instanceof HTMLInputElement) {
      const type = (element.type || "text").toLowerCase();
      return !["button", "checkbox", "color", "file", "hidden", "image", "radio", "range", "reset", "submit"].includes(type);
    }
    return false;
  }

  function isNavigableField(element) {
    if (!(element instanceof HTMLElement)) return false;
    if (element.tabIndex === -1 || element.hasAttribute("disabled") || element.getAttribute("aria-hidden") === "true") return false;
    if (element.offsetParent === null) return false;
    return isTextLikeInput(element);
  }

  function getCellContainer(element) {
    return element.closest("td, th, .today-entry-field, .today-entry-card, .table-field, .field-row, .field-cell");
  }

  function getCell(element) {
    return getCellContainer(element);
  }

  function getTable(element) {
    return element.closest("table");
  }

  function caretAtStart(element) {
    if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
      return element.selectionStart === element.selectionEnd && element.selectionStart === 0;
    }
    return false;
  }

  function caretAtEnd(element) {
    if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
      const length = element.value.length;
      return element.selectionStart === element.selectionEnd && element.selectionEnd === length;
    }
    return false;
  }

  function getCandidates(current) {
    const table = getTable(current);
    const container = table
      || current.closest(".today-entry-grid")
      || current.closest(".landing-canvas")
      || current.closest("form")
      || document;
    const scope = container;
    return Array.from(scope.querySelectorAll("input, textarea"))
      .filter((element) => element !== current && isNavigableField(element) && getCell(element));
  }

  function centerOf(element) {
    const rect = element.getBoundingClientRect();
    return {
      x: rect.left + rect.width / 2,
      y: rect.top + rect.height / 2,
      rect,
    };
  }

  function pickNextField(current, direction) {
    const origin = centerOf(current);
    const candidates = getCandidates(current)
      .map((element) => ({ element, pos: centerOf(element) }))
      .filter(({ pos }) => {
        if (direction === "left") return pos.x < origin.x - 2;
        if (direction === "right") return pos.x > origin.x + 2;
        if (direction === "up") return pos.y < origin.y - 2;
        if (direction === "down") return pos.y > origin.y + 2;
        return false;
      });

    if (!candidates.length) return null;

    candidates.sort((a, b) => {
      const ax = Math.abs(a.pos.x - origin.x);
      const ay = Math.abs(a.pos.y - origin.y);
      const bx = Math.abs(b.pos.x - origin.x);
      const by = Math.abs(b.pos.y - origin.y);

      const aScore = direction === "left" || direction === "right"
        ? ax + ay * 4
        : ay + ax * 4;
      const bScore = direction === "left" || direction === "right"
        ? bx + by * 4
        : by + bx * 4;
      return aScore - bScore;
    });

    return candidates[0]?.element || null;
  }

  function focusField(element, direction) {
    if (!(element instanceof HTMLElement)) return;
    element.focus();
    if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
      if (direction === "left") {
        const end = element.value.length;
        element.setSelectionRange(end, end);
      } else if (direction === "right") {
        element.setSelectionRange(0, 0);
      } else {
        element.select();
      }
    }
  }

  document.addEventListener("keydown", (event) => {
    const target = event.target;
    if (!isTextLikeInput(target) || !getCell(target)) return;
    if (event.altKey || event.ctrlKey || event.metaKey) return;

    let direction = "";
    if (event.key === "ArrowLeft") {
      if (!caretAtStart(target)) return;
      direction = "left";
    } else if (event.key === "ArrowRight") {
      if (!caretAtEnd(target)) return;
      direction = "right";
    } else if (event.key === "ArrowUp") {
      direction = "up";
    } else if (event.key === "ArrowDown") {
      direction = "down";
    } else {
      return;
    }

    const next = pickNextField(target, direction);
    if (!next) return;
    event.preventDefault();
    focusField(next, direction);
  }, true);
})();
