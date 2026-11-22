export const byId = id => document.getElementById(id);

export const orderPath = reference => "/orders/" + encodeURIComponent(reference);

export function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text != null) element.textContent = text;
  if (className) element.className = className;
  return element;
}

export function button(text, action, target) {
  const control = node("button", text, "shared-button shared-button-small");
  control.type = "button";
  if (target) control.setAttribute("aria-label", text + " " + target);
  control.addEventListener("click", () => action(control));
  return control;
}

export function link(text, href, target) {
  const anchor = node("a", text, "shared-button shared-button-small");
  anchor.href = href;
  if (target) anchor.setAttribute("aria-label", text + " for " + target);
  return anchor;
}

export function taskLink(text, reference) {
  return link(text, "warehouse-picking-task.html?order=" + encodeURIComponent(reference), reference);
}

export function statusWord(value) {
  if (!value) return "—";
  const words = value.toLowerCase().replaceAll("_", " ");
  return words[0].toUpperCase() + words.slice(1);
}

export function statusBadge(value) {
  const styles = {
    AWAITING_PAYMENT: "waiting",
    READY_FOR_PICKING: "warehouse",
    PICKING: "warehouse",
    READY_FOR_PICKUP: "warehouse",
    READY_FOR_REDELIVERY: "warehouse",
    SHIPPED: "road",
    OUT_FOR_DELIVERY: "road",
    DELIVERED: "done",
    ACCEPTED: "done",
    PACKED: "done",
    REJECTED: "problem",
    DELIVERY_FAILED: "problem"
  };
  return node("span", statusWord(value), "shared-status-badge shared-status-badge-" + (styles[value] || "closed"));
}

export function rows(target, records, labels, empty, cells) {
  const body = byId(target);
  const fragment = document.createDocumentFragment();
  if (!records.length) {
    const row = fragment.appendChild(node("tr"));
    const cell = row.appendChild(node("td", empty));
    cell.colSpan = labels.length;
    cell.className = "warehouse-empty";
  }
  for (const record of records) {
    const row = fragment.appendChild(node("tr"));
    cells(record).forEach((content, index) => {
      const cell = row.appendChild(node(index === 0 ? "th" : "td"));
      if (index === 0) cell.scope = "row";
      cell.dataset.title = labels[index];
      if (["Units ordered", "Units picked", "Ordered", "Picked", "Units"].includes(labels[index])) {
        cell.className = "shared-table-number";
      }
      cell.append(content ?? "—");
    });
  }
  body.replaceChildren(fragment);
}

export function reportError(id, error) {
  sharedShowInlineMessage(byId(id), "ERROR", error?.message || "The request could not be completed.");
}

export function list(answer, name) {
  if (!Array.isArray(answer?.[name])) {
    throw new Error("Could not read the Warehouse response. Refresh to try again.");
  }
  return answer[name];
}
