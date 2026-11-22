import { badge, escapeHtml as esc, icon, options, pageHeader, time } from '../../shared/html.js';
import { FAILURE_REASONS, label } from '../../shared/format.js';

const imageTypes = ['image/jpeg', 'image/png'];
const outcomeTabs = [['DELIVERED', 'Delivered'], ['FAILED', 'Failed']];

function signature(task) {
  return `<div class="shared-capture-box">
    <p class="shared-strong">Recipient signature</p>
    <canvas class="delivery-signature" aria-label="Draw recipient signature"></canvas>
    <p class="shared-note" data-signature-hint>Sign with a finger or mouse.</p>
    <div class="shared-button-row">
      <button type="button" class="shared-button shared-button-small" data-clear-signature disabled>Clear signature</button>
      <button type="button" class="shared-button shared-button-small" data-save-signature data-package="${esc(task.id)}" disabled>Save signature proof</button>
    </div>
  </div>`;
}

function photo(task) {
  return `<div class="shared-capture-box">
    <p class="shared-strong">Delivery photo</p>
    <p class="shared-note">JPEG or PNG</p>
    <input id="photo-file" type="file" class="shared-visually-hidden" tabindex="-1" aria-label="Choose a delivery photo"
      data-photo data-package="${esc(task.id)}" accept="image/jpeg,image/png">
    <input id="camera-file" type="file" class="shared-visually-hidden" tabindex="-1" aria-label="Take a delivery photo"
      data-photo data-package="${esc(task.id)}" accept="image/jpeg,image/png" capture="environment">
    <div class="shared-button-row">
      <button type="button" class="shared-button shared-button-small" data-delivery="camera" data-package="${esc(task.id)}">Take photo</button>
      <button type="button" class="shared-button shared-button-small" data-delivery="file" data-package="${esc(task.id)}">Choose file</button>
    </div>
  </div>`;
}

function savedProofs(proofs) {
  if (!proofs.length) return '<p class="shared-note">No proof saved for this attempt.</p>';
  return `<div>
    <h3 class="shared-card-part-title">Saved proof</h3>
    ${proofs.map(proof => `<div class="shared-button-row">
      <label class="shared-checkbox">
        <input data-proof-id="${esc(proof.id)}" type="checkbox" checked>
        <span><span>${esc(label(proof.type))}</span> <span class="shared-muted">${esc(proof.id)}</span></span>
      </label>
      ${proof.url ? `<button type="button" class="shared-button shared-button-small"
        data-preview="${esc(proof.id)}" aria-label="Preview ${esc(label(proof.type).toLowerCase())} ${esc(proof.id)}">Preview</button>` : ''}
    </div>`).join('')}
  </div>`;
}

function successPanel(task, proofs) {
  if (!task.canDeliver) return '<p class="shared-note">This attempt cannot be marked as delivered.</p>';
  return `<p class="shared-note">Save and select at least one signature or photo before confirming delivery.</p>
    ${signature(task)}
    ${photo(task)}
    ${savedProofs(proofs)}
    <div class="shared-button-row">
      <button type="button" class="shared-button shared-button-primary" data-delivery="complete"
        data-package="${esc(task.id)}" ${proofs.length ? '' : 'disabled'}>Confirm delivered</button>
    </div>`;
}

function failurePanel(task) {
  if (!task.canFail) return '<p class="shared-note">This attempt cannot be recorded as failed.</p>';
  const reasons = [['', 'Choose a reason'], ...FAILURE_REASONS];
  return `<form data-failure-form data-package="${esc(task.id)}">
    <label class="shared-field">
      <span class="shared-field-label">Failure reason</span>
      <span class="shared-select shared-select-wide"><select name="reason" required>${options(reasons, '')}</select>${icon('chevron')}</span>
    </label>
    <label class="shared-field">
      <span class="shared-field-label">Internal note</span>
      <textarea class="shared-textarea" name="note" rows="3" maxlength="600" aria-describedby="failure-note-help"></textarea>
      <span class="shared-field-help" id="failure-note-help">Required when the failure reason is Other.</span>
    </label>
    <div class="shared-field"><div class="shared-button-row">
      <button type="submit" class="shared-button shared-button-primary" disabled>Record failed attempt</button>
    </div></div>
  </form>`;
}

export function renderOutcome(ctx) {
  const task = (ctx.workspace.tasks || []).find(item => item.id === ctx.state.selected);
  const active = task && (task.canDeliver || task.canFail);
  const attempt = active && (ctx.workspace.attempts || []).find(item => item.id === task.activeAttemptId);
  const selectedOutcome = ctx.state.outcome === 'FAILED' ? 'FAILED' : 'DELIVERED';
  const back = `<a class="shared-page-back" href="#${task ? `delivery-detail/${encodeURIComponent(task.id)}` : 'deliveries'}"
    data-delivery="${task ? 'details' : 'back'}" ${task ? `data-package="${esc(task.id)}"` : ''}>Back to ${task ? 'delivery details' : 'my deliveries'}</a>`;
  const renderTab = ([key, name]) => {
    const selected = key === selectedOutcome;
    return `<button type="button" class="shared-tab${selected ? ' shared-tab-active' : ''}" role="tab" id="outcome-tab-${key}"
      aria-selected="${selected}" aria-controls="outcome-panel-${key}" tabindex="${selected ? 0 : -1}"
      data-delivery="tab" data-package="${esc(task.id)}" data-outcome="${key}" data-tab="outcome-panel-${key}">${name}</button>`;
  };
  const renderPanel = ([key]) => `<div class="shared-tab-panel" id="outcome-panel-${key}" role="tabpanel"
    aria-labelledby="outcome-tab-${key}" ${key === selectedOutcome ? '' : 'hidden'}>
    <div class="shared-card-body">${key === 'DELIVERED' ? successPanel(task, attempt?.proofs || []) : failurePanel(task)}</div>
  </div>`;
  return `${pageHeader('Record outcome', '', '', back)}
  <section class="shared-card">${active ? `<div class="shared-card-strip">
      <p class="shared-note">${esc(task.orderReference)} ${attempt?.startedAt ? `· Attempt started ${time(attempt.startedAt)}.` : ''} ${badge(task.status)}</p>
    </div>
    <div class="shared-tab-list" role="tablist" aria-label="Delivery outcome">${outcomeTabs.map(renderTab).join('')}</div>
    ${outcomeTabs.map(renderPanel).join('')}`
    : '<p class="shared-table-empty">There is no active attempt to record.</p>'}</section>`;
}

export function selectOutcomeTab(tab, state) {
  const list = tab.closest('[role="tablist"]');
  if (!list) return;
  state.outcome = tab.dataset.outcome;
  for (const option of list.querySelectorAll('[role="tab"]')) {
    const selected = option === tab;
    option.classList.toggle('shared-tab-active', selected);
    option.setAttribute('aria-selected', String(selected));
    option.tabIndex = selected ? 0 : -1;
    document.getElementById(option.getAttribute('aria-controls')).hidden = !selected;
  }
  tab.focus();
}

export function handleOutcomeField(event, ctx) {
  if (event.type === 'keydown') {
    const tab = event.target.closest('[role="tab"][data-outcome]');
    if (!tab || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    const tabs = Array.from(tab.parentElement.querySelectorAll('[role="tab"]'));
    const current = tabs.indexOf(tab);
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1
      : (current + (event.key === 'ArrowLeft' ? -1 : 1) + tabs.length) % tabs.length;
    event.preventDefault();
    selectOutcomeTab(tabs[next], ctx.state);
    return;
  }
  const form = event.target.closest('[data-failure-form]');
  if (form) {
    const { reason, note } = form.elements;
    note.required = reason.value === 'OTHER';
    form.querySelector('[type="submit"]').disabled = !reason.value || (note.required && !note.value.trim());
  }
  if (event.target.hasAttribute('data-proof-id')) {
    const panel = event.target.closest('[role="tabpanel"]');
    panel.querySelector('[data-delivery="complete"]').disabled = !panel.querySelector('[data-proof-id]:checked');
  }
}

export function uploadProof(ctx, task, type, file) {
  if (!file || !task.canDeliver || ctx.busy) return;
  if (!imageTypes.includes(file.type)) return ctx.flash('Choose a JPEG or PNG image.', 'error');
  const maxSize = ctx.workspace.settings?.photoLimitBytes || 5242880;
  if (file.size > maxSize) return ctx.flash(`Choose an image no larger than ${maxSize / 1048576} MB.`, 'error');
  return ctx.upload(`/api/packages/${encodeURIComponent(task.id)}/proofs`, type, file);
}

export function attachSignature(root, ctx) {
  const canvas = root.querySelector('.delivery-signature');
  if (!canvas) return;
  const save = root.querySelector('[data-save-signature]');
  const clear = root.querySelector('[data-clear-signature]');
  const hint = root.querySelector('[data-signature-hint]');
  const task = ctx.workspace.tasks.find(item => item.id === save.dataset.package);
  const context = canvas.getContext('2d');
  if (!context) {
    hint.textContent = 'The signature pad is unavailable. Use a photo.';
    return;
  }
  const strokes = [];
  let stroke;
  let pointerId;
  function draw() {
    const rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const scale = window.devicePixelRatio || 1;
    canvas.width = Math.max(1, Math.round(rect.width * scale));
    canvas.height = Math.max(1, Math.round(rect.height * scale));
    context.lineWidth = 2.4 * scale;
    context.lineCap = 'round';
    context.lineJoin = 'round';
    context.strokeStyle = getComputedStyle(canvas).getPropertyValue('--colour-text').trim() || '#161616';
    for (const points of strokes) {
      if (points.length < 2) continue;
      context.beginPath();
      context.moveTo(points[0].x * canvas.width, points[0].y * canvas.height);
      for (const point of points.slice(1)) context.lineTo(point.x * canvas.width, point.y * canvas.height);
      context.stroke();
    }
    const hasInk = strokes.some(points => points.length > 1);
    hint.hidden = hasInk;
    save.disabled = !hasInk;
    clear.disabled = !hasInk;
  }
  function getPoint(event) {
    const rect = canvas.getBoundingClientRect();
    const clamp = value => Math.max(0, Math.min(1, value));
    return { x: clamp((event.clientX - rect.left) / rect.width), y: clamp((event.clientY - rect.top) / rect.height) };
  }
  canvas.addEventListener('pointerdown', event => {
    if (pointerId !== undefined || event.button !== 0 || ctx.busy || !task.canDeliver) return;
    event.preventDefault();
    canvas.setPointerCapture(event.pointerId);
    pointerId = event.pointerId;
    stroke = [getPoint(event)];
    strokes.push(stroke);
  });
  canvas.addEventListener('pointermove', event => {
    if (pointerId !== event.pointerId) return;
    stroke.push(getPoint(event));
    draw();
  });
  function endStroke(event) {
    if (pointerId === event.pointerId) pointerId = undefined;
  }
  canvas.addEventListener('pointerup', endStroke);
  canvas.addEventListener('pointercancel', endStroke);
  canvas.addEventListener('lostpointercapture', endStroke);
  clear.onclick = () => { strokes.length = 0; draw(); };
  save.onclick = () => canvas.toBlob(blob => {
    if (blob) uploadProof(ctx, task, 'SIGNATURE', new File([blob], 'recipient-signature.png', { type: 'image/png' }));
  }, 'image/png');
  const resize = new ResizeObserver(draw);
  resize.observe(canvas);
  draw();
  return () => resize.disconnect();
}
