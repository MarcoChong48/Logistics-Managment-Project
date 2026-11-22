import { byId, button, list, orderPath, reportError, rows, statusBadge } from "./warehouse-ui.js";

function storedAction(key) {
  try {
    const action = JSON.parse(sessionStorage.getItem(key));
    if (action && ["scan", "reverse", "pack", "list"].includes(action.kind)) return action;
  } catch {
    // A blocked or unavailable tab store still allows a retry on the current page.
  }
  return null;
}

export function pickingTask(api, updateSummary, username) {
  const reference = (sharedReadPageParameter("order") || "").trim();
  const base = orderPath(reference);
  const taskPath = base + "/picking-task";
  const key = "lms-warehouse-pending:" + JSON.stringify([username, reference]);
  const scanForm = byId("item-scan-form");
  const sku = byId("scan-item-id");
  const feedback = byId("scan-feedback");
  const reverseDialog = byId("reverse-dialog");
  const reverseQuantity = byId("reverse-quantity");
  const reverseReason = byId("reverse-reason");
  const packDialog = byId("pack-dialog");
  const pickupButton = byId("create-pickup-list");
  const pickupQR = byId("pickup-list-qr");
  const retry = byId("retry-action");
  const actionLabels = { scan: "scan", reverse: "pick reversal", pack: "packing confirmation", list: "Pickup List creation" };
  let pending = storedAction(key);
  let working = false;
  let task;
  let canCreateList = false;
  let selectedLine;
  let refreshVersion = 0;

  function save(action) {
    pending = action;
    try {
      if (action) sessionStorage.setItem(key, JSON.stringify(action));
      else sessionStorage.removeItem(key);
    } catch {
      // The in-memory command still protects this page's retry.
    }
  }

  function controls() {
    const blocked = working || !!pending || !task;
    sku.disabled = blocked || !task.canScan;
    byId("record-scan").disabled = sku.disabled;
    byId("pack-task").disabled = blocked || !task.canConfirmPacking;
    pickupButton.disabled = blocked || !canCreateList;
    for (const control of byId("task-items").querySelectorAll("button")) control.disabled = blocked;
    retry.hidden = !pending;
    retry.disabled = working;
    if (pending) retry.textContent = "Retry " + actionLabels[pending.kind];
  }

  function openReversal(line) {
    selectedLine = line;
    byId("reverse-item").textContent = line.sku + " · " + line.productName
      + " · " + line.pickedQuantity + " currently picked";
    reverseQuantity.max = String(line.pickedQuantity - 1);
    reverseQuantity.value = String(line.pickedQuantity - 1);
    reverseReason.value = "";
    reverseReason.setCustomValidity("");
    sharedOpenDialog(reverseDialog);
  }

  function render() {
    const lines = list(task, "lineList");
    const ordered = lines.reduce((sum, line) => sum + line.orderedQuantity, 0);
    const picked = lines.reduce((sum, line) => sum + line.pickedQuantity, 0);
    byId("warehouse-title").textContent = "Picking task · " + task.orderReference;
    byId("task-order-status").replaceChildren(statusBadge(task.orderStatus));
    byId("task-assignee").textContent = task.assignedUsername || "Unassigned";
    byId("task-remaining").textContent = String(ordered - picked);
    byId("task-progress").textContent = picked + " of " + ordered + " picked";
    byId("task-progress-bar").max = ordered || 1;
    byId("task-progress-bar").value = picked;
    byId("task-notice").textContent = task.canScan || task.taskStatus === "PACKED" ? ""
      : "This picking task is currently read only.";
    byId("pack-reason").textContent = task.taskStatus === "PACKED"
      ? "Packing confirmed. The package label is ready."
      : task.hasOpenShortage ? "Packing is blocked by an open shortage."
      : task.canConfirmPacking ? "All ordered units have been picked."
      : "Packing requires the assigned worker and all ordered units picked.";
    byId("order-qr").href = "/api/warehouse" + base + "/qr-code";
    rows("task-items", lines, ["SKU", "Product", "Ordered", "Picked", "Actions"], "There are no items on this order.",
      line => [line.sku, line.productName, String(line.orderedQuantity), String(line.pickedQuantity),
        task.isMine && task.canScan && line.pickedQuantity > 0
          ? button("Reverse pick", () => openReversal(line), line.sku) : null]);
    controls();
  }

  async function labels(version) {
    canCreateList = false;
    pickupButton.hidden = true;
    pickupQR.hidden = true;
    const packed = task.taskStatus === "PACKED" && !!task.confirmPackingTime;
    byId("package-label").hidden = !packed;
    byId("pickup-list").hidden = !packed;
    if (!packed) return;

    const [packageResult, listResult] = await Promise.allSettled([api(base + "/package"), api(base + "/pickup-list")]);
    if (version !== refreshVersion) return;
    const packageQR = byId("package-qr");
    const code = packageResult.value?.packageQrCode;
    byId("package-code").textContent = code || "Could not load the package label. Refresh to try again.";
    packageQR.hidden = !code;
    if (code) packageQR.href = "/api/warehouse" + base + "/package/qr-code";

    const listStatus = byId("pickup-list-status");
    if (listResult.status === "rejected") {
      listStatus.textContent = listResult.reason?.status === 404
        ? "Pickup List creation is unavailable on this server."
        : "Could not load the Pickup List. Refresh to try again.";
      return;
    }
    const saved = listResult.value;
    if (saved?.pickupListId && saved.listQrCode) {
      listStatus.textContent = saved.listQrCode;
      pickupQR.href = "/api/warehouse" + base + "/pickup-list/" + encodeURIComponent(saved.pickupListId) + "/qr-code";
      pickupQR.hidden = false;
      return;
    }
    if (!saved || typeof saved !== "object" || Object.keys(saved).length > 0) {
      listStatus.textContent = "Could not read the Pickup List. Refresh to try again.";
      return;
    }
    canCreateList = task.orderStatus === "READY_FOR_PICKUP";
    pickupButton.hidden = !canCreateList;
    listStatus.textContent = canCreateList ? "The package is ready for a Pickup List." : "No active Pickup List.";
  }

  async function load() {
    if (!reference || reference.length > 60) throw new Error("Open a picking task from the order queue.");
    const version = ++refreshVersion;
    try {
      const data = await api(taskPath);
      if (version !== refreshVersion) return;
      list(data, "lineList");
      task = data;
      render();
      await labels(version);
    } catch (error) {
      if (version === refreshVersion) {
        task = null;
        throw error;
      }
    } finally {
      controls();
    }
  }

  async function run(action) {
    if (working) return;
    working = true;
    save(action);
    controls();
    feedback.replaceChildren();
    const path = {
      scan: taskPath + "/scans",
      reverse: taskPath + "/items/" + encodeURIComponent(action.orderItemID) + "/picked-quantity",
      pack: taskPath + "/confirm-packing",
      list: base + "/pickup-list"
    }[action.kind];
    try {
      const result = await api(path, "POST", action.body);
      const isList = action.kind === "list";
      if (isList ? !result?.pickupListId || !result.listQrCode : typeof result?.message !== "string") {
        throw new Error("The server response could not be confirmed.");
      }
      save(null);
      const rejected = result.result === "REJECTED";
      sharedShowInlineMessage(feedback, rejected ? "ERROR" : "SUCCESS",
        isList ? "Pickup List created." : result.message);
      if (action.kind === "scan") sku.value = "";
    } catch (error) {
      const definiteReply = error?.status >= 400 && error.status < 500
        && error.status !== 401 && error.code !== "SHARED_CSRF_INVALID" && error.status !== 403;
      if (definiteReply) save(null);
      reportError("scan-feedback", new Error(definiteReply ? error.message
        : (error?.message || "The result is unknown.") + " Retry the pending action to use the same request."));
    }
    try {
      await Promise.all([load(), updateSummary()]);
    } catch (error) {
      sharedShowMessage("ERROR", error?.message || "Refresh to load the latest task.");
    } finally {
      working = false;
      controls();
      if (pending) retry.focus();
      else if (action.kind === "scan" && !sku.disabled) sku.focus();
    }
  }

  scanForm.addEventListener("submit", event => {
    event.preventDefault();
    if (sku.disabled || !sku.value.trim()) return;
    run({ kind: "scan", body: { scannedIdentifier: sku.value, requestId: crypto.randomUUID() } });
  });
  reverseReason.addEventListener("input", () => reverseReason.setCustomValidity(""));
  byId("reverse-form").addEventListener("submit", event => {
    event.preventDefault();
    if (working || pending || !selectedLine) return;
    const reason = reverseReason.value.trim();
    if (!reason) {
      reverseReason.setCustomValidity("Enter a reason for this reversal.");
      reverseReason.reportValidity();
      return;
    }
    sharedCloseDialog(reverseDialog);
    run({ kind: "reverse", orderItemID: selectedLine.orderItemID,
      body: { pickedQuantity: reverseQuantity.valueAsNumber, reason, requestId: crypto.randomUUID() } });
  });
  byId("pack-task").addEventListener("click", () => sharedOpenDialog(packDialog));
  byId("confirm-pack").addEventListener("click", () => {
    sharedCloseDialog(packDialog);
    if (!working && !pending && task?.canConfirmPacking) run({ kind: "pack", body: null });
  });
  pickupButton.addEventListener("click", () => {
    if (!pickupButton.disabled) run({ kind: "list", body: { requestId: crypto.randomUUID() } });
  });
  retry.addEventListener("click", () => { if (pending) run(pending); });
  if (pending) {
    sharedShowInlineMessage(feedback, "INFORMATION", "A previous action is awaiting confirmation. Retry to check its result.");
  }
  return { load };
}
