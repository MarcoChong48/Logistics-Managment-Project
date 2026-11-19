import { badge, escapeHtml as esc, icon, options, pageHeader, time } from '../../shared/html.js';
import { label } from '../../shared/format.js';

const reasons = [
  ['', 'Select a reason'],
  ['VEHICLE_FULL', 'Vehicle full'],
  ['PACKAGING_DAMAGED', 'Packaging damaged'],
  ['GOODS_DAMAGED', 'Goods damaged'],
  ['OTHER', 'Other'],
];

let decoder;
function qrLibrary() {
  decoder ||= new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = '/assets/shared/vendor/zxing-browser.min.js';
    script.onload = () => resolve(window.ZXingBrowser);
    script.onerror = () => {
      decoder = undefined;
      script.remove();
      reject(new Error('Could not load the camera scanner.'));
    };
    document.head.append(script);
  });
  return decoder;
}

function codeForm(listId = '') {
  const collect = Boolean(listId);
  const action = collect ? 'Collect package' : 'Claim list';
  const cameraLabel = collect ? `Scan package for pickup list ${listId}` : 'Scan pickup list with camera';
  return `<form class="shared-scan-box" data-pickup-form data-list="${esc(listId)}">
    <input class="shared-input" type="text" name="code" aria-label="${collect ? 'Package QR code' : 'Pickup list QR code'}"
      placeholder="${collect ? 'Package QR code' : 'Pickup list QR code'}" autocomplete="off" required>
    <button class="shared-button" type="button" aria-label="${esc(cameraLabel)}"
      data-pickup="camera" data-list="${esc(listId)}">Scan with camera</button>
    <button class="shared-button${collect ? '' : ' shared-button-primary'}" type="submit"
      ${collect ? `aria-label="Collect package for pickup list ${esc(listId)}"` : ''}>${action}</button>
  </form>`;
}

function packageRow(item, list, mine) {
  const closed = list.status === 'CLOSED';
  const state = item.collectionOutcome || item.status;
  const canReport = mine && !closed && state === 'PENDING';
  const order = item.orderReference || item.id;
  const pickupState = state === 'PENDING' &&
    ['READY_FOR_PICKUP', 'READY_FOR_REDELIVERY'].includes(item.status) ? item.status : state;
  const note = item.cannotCollectReason ? label(item.cannotCollectReason) : item.holdReason;
  return `<tr data-pickup-package="${esc(item.id)}">
    <th scope="row" data-title="Order" tabindex="-1">
      <span class="shared-cell-main shared-nowrap">${esc(order)}</span>
      <span class="shared-cell-sub shared-fixed-text">${esc(item.id)}</span>
    </th>
    <td data-title="Contents">${esc(item.itemSummary || '-')}</td>
    <td data-title="State">
      <span class="shared-cell-main">${badge(pickupState)}</span>
      ${note ? `<span class="shared-cell-sub">${esc(note)}</span>` : ''}
    </td>
    <td class="shared-table-actions" data-title="Actions">
      ${canReport ? `<div class="shared-cell-buttons">
        <button class="shared-button shared-button-small" type="button"
          data-pickup="issue" data-list="${esc(list.id)}" data-package="${esc(item.id)}"
          aria-label="Cannot collect order ${esc(order)} on list ${esc(list.id)}">Cannot collect</button>
      </div>` : '<span class="shared-muted">-</span>'}
    </td>
  </tr>`;
}

function pickupList(list, userId) {
  const mine = Boolean(list.claimedBy && list.claimedBy === userId);
  const closed = list.status === 'CLOSED';
  const items = list.packages || [];
  const numberCollected = items.filter(item => item.collectionOutcome === 'COLLECTED').length;
  const claimNote = mine ? 'Claimed by you' : list.claimedBy ? `Claimed by ${list.claimedBy}` : '';
  return `<section class="shared-card" data-pickup-list="${esc(list.id)}">
    <div class="shared-card-header">
      <h2 class="shared-card-title" tabindex="-1">Pickup list <span class="shared-nowrap">${esc(list.id)}</span></h2>
      <div class="shared-card-actions">
        ${!list.claimedBy && !closed ? `<button class="shared-button shared-button-small" type="button"
          aria-label="Claim pickup list ${esc(list.id)}" data-pickup="claim" data-list="${esc(list.id)}"
          data-code="${esc(list.qrCode)}">Claim list</button>` : ''}
        ${mine && !closed ? `<button class="shared-button shared-button-small" type="button"
          aria-label="Close pickup list ${esc(list.id)}" data-pickup="close" data-list="${esc(list.id)}">Close list</button>` : ''}
      </div>
    </div>
    <div class="shared-card-strip shared-button-row">
      ${badge(list.status)}
      ${list.stale ? '<span class="shared-flag shared-flag-problem">Review</span>' : ''}
      ${claimNote ? `<span class="shared-muted">${esc(claimNote)}</span>` : ''}
      <span class="shared-muted">${numberCollected} of ${items.length} collected</span>
      ${list.createdAt ? `<span class="shared-muted">Created ${time(list.createdAt)}</span>` : ''}
    </div>
    ${mine && !closed ? `<div class="shared-card-body">${codeForm(list.id)}</div>` : ''}
    ${items.length ? `<div class="shared-table-scroll">
      <table class="shared-table shared-table-work" aria-label="Packages in pickup list ${esc(list.id)}">
        <thead><tr><th scope="col">Order</th><th scope="col">Contents</th><th scope="col">State</th>
          <th class="shared-table-actions" scope="col">Actions</th></tr></thead>
        <tbody>${items.map(item => packageRow(item, list, mine)).join('')}</tbody>
      </table>
    </div>` : '<p class="shared-table-empty">No packages in this list.</p>'}
  </section>`;
}

export function renderPickup({ workspace }) {
  const lists = workspace.pickupLists || [];
  return `${pageHeader('Pickup', `${lists.length} pickup ${lists.length === 1 ? 'list' : 'lists'}`)}
    <section class="shared-card">
      <div class="shared-card-header"><h2 class="shared-card-title">Claim a pickup list</h2></div>
      <div class="shared-card-body">
        ${codeForm()}
        <p class="shared-note">Scan each package when you collect it.</p>
      </div>
    </section>
    ${lists.map(list => pickupList(list, workspace.user?.id)).join('') ||
      '<section class="shared-card"><p class="shared-table-empty">No pickup lists available.</p></section>'}`;
}

function focusRecord(listId, packageId) {
  const list = [...document.querySelectorAll('[data-pickup-list]')]
    .find(section => section.dataset.pickupList === listId);
  if (!list) return;
  const row = [...list.querySelectorAll('[data-pickup-package]')]
    .find(item => item.dataset.pickupPackage === packageId);
  (row?.querySelector('th') || list.querySelector('h2'))?.focus();
}

async function submitCode(ctx, code, listId) {
  const value = code?.trim();
  if (!value) return;
  if (listId) {
    const list = ctx.workspace.pickupLists.find(item => item.id === listId);
    const item = list?.packages.find(pkg => pkg.qrCode?.toUpperCase() === value.toUpperCase());
    if (await ctx.mutate(`/api/pickup-lists/${encodeURIComponent(listId)}/collect`, { packageQrCode: value })) {
      focusRecord(listId, item?.id);
    }
    return;
  }
  const list = ctx.workspace.pickupLists.find(item => item.qrCode?.toUpperCase() === value.toUpperCase());
  if (await ctx.mutate('/api/pickup-lists/claim', { qrCode: value })) {
    focusRecord(list?.id);
  }
}

function camera(ctx, listId) {
  ctx.modal('Scan a QR code', `<p role="status" data-camera-message>Starting camera…</p>
    <div class="shared-capture-box" data-camera-view hidden>
      <video width="200" playsinline muted aria-label="QR camera preview"></video>
    </div>
    <div class="shared-dialog-footer"><button class="shared-button" type="button" data-close>Use manual entry</button></div>`, async dialog => {
    const video = dialog.querySelector('video');
    const message = dialog.querySelector('[data-camera-message]');
    const preview = dialog.querySelector('[data-camera-view]');
    let controls;
    let closed = false;
    // Release the camera when the scan is done or cancelled.
    dialog.addEventListener('close', () => {
      closed = true;
      controls?.stop();
      video.srcObject?.getTracks().forEach(track => track.stop());
    });
    try {
      if (!navigator.mediaDevices?.getUserMedia) throw new Error('Camera unavailable.');
      const lib = await qrLibrary();
      if (closed) return;
      preview.hidden = false;
      const reader = new lib.BrowserQRCodeReader();
      controls = await reader.decodeFromConstraints(
        { video: { facingMode: { ideal: 'environment' } } }, video,
        (result, _, scanner) => {
          if (!result || closed || !dialog.open) return;
          scanner.stop();
          dialog.close();
          submitCode(ctx, result.getText(), listId);
        });
      if (closed) controls.stop();
      else message.textContent = 'Point the camera at the QR code.';
    } catch {
      if (!closed) {
        preview.hidden = true;
        message.textContent = 'Camera unavailable. Enter the QR code manually.';
      }
    }
  });
}

function reportPickupIssue(ctx, listId, packageId) {
  const list = ctx.workspace.pickupLists.find(item => item.id === listId);
  const item = list?.packages.find(pkg => pkg.id === packageId);
  if (!item) return;
  const order = item.orderReference || item.id;
  return ctx.modal(`Cannot collect order ${order}`, `<form data-issue>
    <label class="shared-field"><span class="shared-field-label">Reason</span>
      <span class="shared-select shared-select-wide">
        <select name="reason" required>${options(reasons, '')}</select>${icon('chevron')}
      </span>
    </label>
    <label class="shared-field"><span class="shared-field-label">Note <span class="shared-field-optional">(required for Other)</span></span>
      <textarea class="shared-textarea" name="note" rows="3" maxlength="600"></textarea>
    </label>
    <div class="shared-dialog-footer">
      <button class="shared-button" type="button" data-close>Cancel</button>
      <button class="shared-button shared-button-primary" type="submit" disabled>Save pickup issue</button>
    </div>
  </form>`, dialog => {
    const form = dialog.querySelector('form');
    const reasonField = form.elements.reason;
    const noteField = form.elements.note;
    const submit = form.querySelector('[type="submit"]');
    const check = () => {
      noteField.required = reasonField.value === 'OTHER';
      submit.disabled = !reasonField.value || (noteField.required && !noteField.value.trim());
    };
    form.addEventListener('input', check);
    form.addEventListener('change', check);
    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (submit.disabled || ctx.busy) return;
      if (await ctx.mutate(`/api/pickup-lists/${encodeURIComponent(listId)}/cannot-collect`, {
        packageId, reason: reasonField.value, note: noteField.value.trim(),
      })) {
        dialog.addEventListener('close', () => focusRecord(listId, packageId), { once: true });
        dialog.close();
      }
    });
  });
}

export function handlePickup(node, ctx) {
  const { pickup, list, package: packageId, code } = node.dataset;
  if (node.matches('[data-pickup-form]')) return submitCode(ctx, new FormData(node).get('code'), list);
  if (!pickup || ctx.busy) return;
  if (pickup === 'camera') return camera(ctx, list);
  if (pickup === 'claim') return submitCode(ctx, code, '');
  if (pickup === 'close') return ctx.confirm(`Close pickup list ${list}?`,
    'Collected packages stay with you. Eligible uncollected packages return to the Warehouse queue.',
    `/api/pickup-lists/${encodeURIComponent(list)}/close`, {}, 'Close list', () => focusRecord(list));
  if (pickup === 'issue') return reportPickupIssue(ctx, list, packageId);
}
