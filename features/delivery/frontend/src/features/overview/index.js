import { badge, empty, escapeHtml as esc, pageHeader } from '../../shared/html.js';

function metric(name, page, value) {
  const count = value ?? 0;
  return `<a class="shared-number-line" href="#${page}" data-nav="${page}">
    <span class="shared-number-line-name">${name}</span>
    <span class="shared-number-line-count${count === 0 ? ' shared-number-line-count-zero' : ''}">${esc(count)}</span>
  </a>`;
}

export default function render({ workspace }) {
  const { stats, tasks = [], reviewQueue = [] } = workspace;
  const nextPage = tasks.length ? 'deliveries' : 'pickup';
  const nextLabel = tasks.length ? 'Open my deliveries' : 'View my pickup lists';
  const rows = tasks.slice(0, 5).map(task => `<tr>
    <th scope="row" data-title="Order"><a href="#delivery-detail/${encodeURIComponent(task.id)}" data-delivery="details" data-package="${esc(task.id)}">${esc(task.orderReference)}</a></th>
    <td data-title="Package">${esc(task.id)}</td>
    <td data-title="Status">${task.returnPending ? badge('AWAITING_WAREHOUSE') : badge(task.status)}</td>
  </tr>`).join('');
  return `<div class="delivery-stack">
    ${pageHeader('Overview', '', `<button class="shared-button" type="button" data-action="refresh">Refresh</button><a class="shared-button shared-button-primary" href="#${nextPage}" data-nav="${nextPage}">${nextLabel}</a>`)}
    <div class="shared-grid-two">
      <section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Delivery workload</h2></div>
        <div class="shared-card-body">
          ${metric('Assigned lists to confirm', 'pickup', stats?.readyLists)}
          ${metric('Packages in transit', 'deliveries', stats?.inTransit)}
          ${metric('Delivered today', 'history', stats?.deliveredToday)}
          ${metric('Returns awaiting warehouse', 'returns', stats?.pendingReturns)}
        </div>
      </section>
      <section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Needs attention</h2></div>
        <div class="shared-card-body">
          ${metric('Review queue', 'notifications', reviewQueue.length)}
          ${reviewQueue.length ? `<ul class="shared-link-list">${reviewQueue.slice(0, 3).map(item => `<li>${esc(item.title)}${item.reference ? ` · ${esc(item.reference)}` : ''}</li>`).join('')}</ul>` : '<p class="shared-note">No items need your attention.</p>'}
        </div>
      </section>
    </div>
    <section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Current deliveries <span class="shared-card-count">${tasks.length}</span></h2>
      <div class="shared-card-actions"><a class="shared-button" href="#deliveries" data-nav="deliveries">View all deliveries</a></div>
    </div>
    ${rows ? `<table class="shared-table shared-table-work"><thead><tr><th scope="col">Order</th><th scope="col">Package</th><th scope="col">Status</th></tr></thead><tbody>${rows}</tbody></table>` : empty('No packages in your custody.')}</section>
  </div>`;
}
