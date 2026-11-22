import { badge, escapeHtml as esc, icon, options, pageHeader, time } from '../../shared/html.js';
import { FAILURE_REASONS, label } from '../../shared/format.js';

const outcomes = [
  ['ALL', 'All outcomes'], ['IN_PROGRESS', 'In progress'], ['DELIVERED', 'Delivered'], ['FAILED', 'Failed'],
];
const proofTypes = [
  ['ALL', 'All proof types'], ['PHOTO', 'Photo'], ['SIGNATURE', 'Signature'], ['NONE', 'No proof recorded'],
];
const reasons = new Map(FAILURE_REASONS);

function attemptRow(attempt, canPreview) {
  const order = attempt.orderReference || attempt.packageId;
  const proofs = (attempt.proofs || []).map((proof, index) => `<div>
    <span class="shared-cell-main">${esc(label(proof.type))} · ${time(proof.createdAt)}</span>
    <span class="shared-cell-sub shared-fixed-text">${esc(proof.id)}</span>
    ${canPreview && proof.url
      ? `<button class="shared-button shared-button-small" type="button" data-preview="${esc(proof.id)}"
        aria-label="Preview ${esc(label(proof.type).toLowerCase())} ${index + 1} for order ${esc(order)}, attempt ${esc(attempt.id)}">Preview</button>`
      : '<span class="shared-cell-sub">Metadata only</span>'}
  </div>`).join('');
  return `<tr>
    <th scope="row" data-title="Order">
      <span class="shared-cell-main shared-nowrap">${esc(order)}</span>
      <span class="shared-cell-sub shared-fixed-text">${esc(attempt.packageId)}</span>
      <span class="shared-cell-sub shared-fixed-text">${esc(attempt.id)}</span>
      <span class="shared-cell-sub">${esc(attempt.workerName || '')}</span>
    </th>
    <td data-title="Outcome">
      <span class="shared-cell-main">${badge(attempt.outcome)}</span>
      ${attempt.reason ? `<span class="shared-cell-sub">${esc(reasons.get(attempt.reason) || label(attempt.reason))}</span>` : ''}
      ${attempt.note ? `<span class="shared-cell-sub">“${esc(attempt.note)}”</span>` : ''}
    </td>
    <td class="shared-table-time" data-title="Attempt time">
      <span class="shared-cell-main">Started ${time(attempt.startedAt)}</span>
      ${attempt.endedAt ? `<span class="shared-cell-sub">Finished ${time(attempt.endedAt)}</span>` : ''}
    </td>
    <td data-title="Proof">${proofs || '<span class="shared-muted">-</span>'}</td>
  </tr>`;
}

function retainedHistory(summaries) {
  if (!summaries.length) return '';
  return `<section class="shared-card">
    <div class="shared-card-header"><h2 class="shared-card-title">Retained order summaries
      <span class="shared-card-count">${summaries.length}</span></h2></div>
    <div class="shared-card-strip"><p class="shared-note">Detailed attempts and proof have reached their retention limit.</p></div>
    <div class="shared-table-scroll">
      <table class="shared-table shared-table-work" aria-label="Retained order summaries">
        <thead><tr><th scope="col">Order</th><th scope="col">Outcome</th><th class="shared-table-time" scope="col">Time</th></tr></thead>
        <tbody>${summaries.map(summary => `<tr>
          <th scope="row" data-title="Order"><span class="shared-cell-main shared-nowrap">${esc(summary.orderReference || summary.packageId)}</span>
            <span class="shared-cell-sub shared-fixed-text">${esc(summary.packageId)}</span></th>
          <td data-title="Outcome">${badge(summary.outcome)}</td>
          <td class="shared-table-time" data-title="Time">${time(summary.eventAt)}</td>
        </tr>`).join('')}</tbody>
      </table>
    </div>
  </section>`;
}

function matchesProof(attempt, filter) {
  const proofs = attempt.proofs || [];
  if (filter === 'ALL') return true;
  if (filter === 'NONE' || filter === 'NO_PROOF') return !proofs.length;
  if (filter === 'WITH_PROOF') return proofs.length > 0;
  return proofs.some(proof => proof.type === filter);
}

export default function render({ workspace = {}, state = {} }) {
  const search = String(state.historySearch ?? '');
  const outcome = state.historyOutcome || 'ALL';
  const proofFilter = state.historyProof || 'ALL';
  const tasks = workspace.tasks || [];
  const attempts = (workspace.attempts || [])
    .filter(attempt => (outcome === 'ALL' || attempt.outcome === outcome)
      && matchesProof(attempt, proofFilter)
      && `${attempt.orderReference} ${attempt.packageId} ${attempt.id}`.toLowerCase().includes(search.toLowerCase()))
    .sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
  // The current worker can preview proof only while the backend keeps the attempt active.
  const rows = attempts.map(attempt => attemptRow(attempt, tasks.some(task =>
    task.id === attempt.packageId && task.canDeliver && task.activeAttemptId === attempt.id))).join('');
  return `${pageHeader('Attempt history', `${attempts.length} ${attempts.length === 1 ? 'attempt' : 'attempts'}`)}
    <section class="shared-card" aria-label="Delivery attempts">
      <div class="shared-filter-bar">
        <label class="shared-input-search">${icon('search')}<input type="search" aria-label="Search attempt history"
          placeholder="Search order, package or attempt ID" data-setting="historySearch" value="${esc(search)}"></label>
        <span class="shared-select"><select aria-label="Attempt outcome" data-setting="historyOutcome">
          ${options(outcomes, outcome)}</select>${icon('chevron')}</span>
        <span class="shared-select"><select aria-label="Proof type" data-setting="historyProof">
          ${options(proofTypes, proofFilter)}</select>${icon('chevron')}</span>
      </div>
      ${rows ? `<div class="shared-table-scroll">
        <table class="shared-table shared-table-work" aria-label="Delivery attempts">
          <thead><tr><th scope="col">Order</th><th scope="col">Outcome</th>
            <th class="shared-table-time" scope="col">Attempt time</th><th scope="col">Proof</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>` : '<p class="shared-table-empty">No attempts to show.</p>'}
    </section>
    ${retainedHistory(workspace.retainedSummaries || [])}`;
}
