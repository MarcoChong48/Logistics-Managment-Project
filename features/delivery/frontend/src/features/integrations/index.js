import { badge, empty, escapeHtml as esc, inlineMessage, pageHeader, time } from '../../shared/html.js';

function decisionRow(item) {
  const local = item.status === 'LOCAL_IMPLEMENTATION';
  return `<tr><th scope="row" data-title="Decision">${esc(item.id)} · Plan ${esc(item.plan)}</th>
    <td data-title="Choice"><strong>${esc(item.choice)}</strong><p class="shared-cell-sub">${esc(item.reason)}</p></td>
    <td data-title="Status">${local ? badge('ACTIVE', 'done', 'Local implementation') : badge('PENDING', 'waiting', 'Place holder')}</td>
  </tr>`;
}

export default function render({ workspace }) {
  const { integrations = [], decisions = [], storageHealth, settings } = workspace;
  return `<div class="delivery-stack">
    ${pageHeader('Decisions & integrations')}
    <section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Team integrations</h2></div>
      <div class="shared-notice-list">${integrations.map(item => `<article class="shared-notice">
        <div class="shared-notice-text"><h3 class="shared-notice-title"><strong>${esc(item.name)}</strong></h3>
        <p class="shared-notice-body">${esc(item.detail)}</p></div>
      </article>`).join('') || empty('No integrations listed.')}</div>
    </section>
    <section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Plan B decisions</h2></div>
      ${decisions.length ? `<table class="shared-table shared-table-work"><thead><tr><th scope="col">Decision</th><th scope="col">Choice</th><th scope="col">Status</th></tr></thead>
      <tbody>${decisions.map(decisionRow).join('')}</tbody></table>` : empty('No decisions listed.')}
    </section>
    ${storageHealth ? `<section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Private proof storage</h2></div>
      <div class="shared-card-body"><p>Last retention check (HKT) · ${time(storageHealth.lastCleanupAt)}</p>
      ${storageHealth.lastError ? inlineMessage(storageHealth.lastError, 'error') : ''}</div>
    </section>` : ''}
    ${settings ? `<section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Delivery settings</h2></div>
      <div class="shared-card-body">
        <p>Photo limit · ${esc(Math.round(settings.photoLimitBytes / 1048576))} MB</p>
        <p>Stale claim review · ${esc(settings.staleClaimMinutes)} min</p>
        <p>Failed-attempt review · ${esc(settings.reviewFailureCount)} attempts</p>
        <p>Return reminder and final review · ${esc(settings.reminderDays)} and ${esc(settings.finalReviewDays)} days</p>
      </div>
    </section>` : ''}
  </div>`;
}
