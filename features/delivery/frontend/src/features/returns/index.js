import { badge, escapeHtml as esc, pageHeader, time } from '../../shared/html.js';

const followUpLabels = {
  AWAITING_CLIENT_INSTRUCTION: ['waiting', 'Waiting for client'],
  AWAITING_ADMIN_APPROVAL: ['problem', 'Waiting for Admin approval'],
  READY_FOR_REDELIVERY: ['warehouse', 'Ready for redelivery'],
  ON_HOLD: ['problem', 'On hold'],
  CANCELLED: ['closed', 'Cancelled'],
};

function returnState(record) {
  if (record.status === 'AWAITING_WAREHOUSE') {
    return badge(record.status, 'waiting', 'Waiting for Warehouse receipt');
  }
  // Older responses only report whether the client still needs to reply.
  const followUp = Object.hasOwn(record, 'followUpStatus')
    ? record.followUpStatus
    : record.awaitingInstruction ? 'AWAITING_CLIENT_INSTRUCTION' : null;
  const display = followUpLabels[followUp];
  return display ? badge(followUp, ...display) : badge(record.status);
}

function returnActionRow(task) {
  const order = task.orderReference || task.id;
  const contents = task.products?.map(item => `${item.quantity} × ${item.name}`).join(', ');
  return `<tr>
    <th scope="row" data-title="Order">
      <span class="shared-cell-main shared-nowrap">${esc(order)}</span>
      <span class="shared-cell-sub shared-fixed-text">${esc(task.id)}</span>
    </th>
    <td data-title="Contents">${esc(contents || '-')}</td>
    <td data-title="State">${badge(task.status)}</td>
    <td class="shared-table-actions" data-title="Actions"><div class="shared-cell-buttons">
      <button class="shared-button shared-button-small" type="button"
        data-delivery="return" data-package="${esc(task.id)}"
        aria-label="Report return for order ${esc(order)}">Report return</button>
    </div></td>
  </tr>`;
}

function returnRecordRow(record) {
  return `<tr>
    <th scope="row" data-title="Order">
      <span class="shared-cell-main shared-nowrap">${esc(record.orderReference || record.packageId)}</span>
      <span class="shared-cell-sub shared-fixed-text">${esc(record.packageId)}</span>
    </th>
    <td data-title="State">
      <span class="shared-cell-main">${returnState(record)}</span>
      ${record.condition ? `<span class="shared-cell-sub">Receipt condition: ${badge(record.condition)}</span>` : ''}
    </td>
    <td class="shared-table-time" data-title="Handoff">
      <span class="shared-cell-main">Reported ${time(record.createdAt)}</span>
      ${record.receivedAt ? `<span class="shared-cell-sub">Received ${time(record.receivedAt)}</span>` : ''}
    </td>
    <td data-title="Reason">${record.reason ? `“${esc(record.reason)}”` : '-'}</td>
  </tr>`;
}

export default function renderReturns({ workspace }) {
  const available = (workspace.tasks || []).filter(task => task.canReturn);
  const records = workspace.returns || [];
  return `${pageHeader('Return handoffs', '', '<button class="shared-button" type="button" data-action="refresh">Refresh status</button>')}
    <section class="shared-card">
      <div class="shared-card-header">
        <h2 class="shared-card-title">Packages you can return <span class="shared-card-count">${available.length}</span></h2>
      </div>
      <div class="shared-card-strip"><p class="shared-note">Warehouse confirms receipt. Until then, the package stays in your custody.</p></div>
      ${available.length ? `<div class="shared-table-scroll">
        <table class="shared-table shared-table-work" aria-label="Packages you can return">
          <thead><tr><th scope="col">Order</th><th scope="col">Contents</th><th scope="col">State</th>
            <th class="shared-table-actions" scope="col">Actions</th></tr></thead>
          <tbody>${available.map(returnActionRow).join('')}</tbody>
        </table>
      </div>` : '<p class="shared-table-empty">No packages are ready to return.</p>'}
    </section>
    <section class="shared-card">
      <div class="shared-card-header">
        <h2 class="shared-card-title">Handoff records <span class="shared-card-count">${records.length}</span></h2>
      </div>
      ${records.length ? `<div class="shared-table-scroll">
        <table class="shared-table shared-table-work" aria-label="Return handoff records">
          <thead><tr><th scope="col">Order</th><th scope="col">State</th>
            <th class="shared-table-time" scope="col">Handoff</th><th scope="col">Reason</th></tr></thead>
          <tbody>${records.map(returnRecordRow).join('')}</tbody>
        </table>
      </div>` : '<p class="shared-table-empty">No handoffs recorded yet.</p>'}
    </section>`;
}
