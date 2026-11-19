import { badge, empty, escapeHtml as esc, inlineMessage, pageHeader } from '../../shared/html.js';
import { label } from '../../shared/format.js';

const actions = {
  intact: ['Receive intact', 'confirm-return', { condition: 'INTACT' }, 'Warehouse receipt'],
  damaged: ['Receive damaged', 'confirm-return', { condition: 'DAMAGED' }, 'Warehouse receipt'],
  redeliver: ['Request redelivery', 'client-instruction', { instruction: 'REDELIVER' }, 'Client instruction'],
  cancel: ['Request cancellation', 'client-instruction', { instruction: 'CANCEL' }, 'Client instruction'],
  approve: ['Approve redelivery', 'admin-decision', { decision: 'APPROVE_REDELIVERY' }, 'Admin approval'],
  confirmCancel: ['Approve cancellation', 'admin-decision', { decision: 'APPROVE_CANCELLATION' }, 'Admin approval'],
};

function button(key, pkg) {
  return `<button type="button" class="shared-button" data-sandbox="${key}" data-package="${esc(pkg)}">${actions[key][0]}</button>`;
}

function packageCard(pkg) {
  return `<section class="shared-card" data-test-package="${esc(pkg.id)}">
    <div class="shared-card-header"><h3 class="shared-card-title">${esc(pkg.orderReference)} <span class="shared-card-count">${esc(pkg.id)}</span></h3>${badge(pkg.status)}</div>
    <div class="shared-card-body">
      <p class="shared-note">Custody · ${esc(label(pkg.custody))}<br>Return · ${esc(label(pkg.returnStatus))}<br>Instruction · ${esc(label(pkg.instruction))}</p>
      <div><h4>Warehouse</h4><div class="shared-button-row">${button('intact', pkg.id)}${button('damaged', pkg.id)}</div></div>
      <div><h4>Client</h4><div class="shared-button-row">${button('redeliver', pkg.id)}${button('cancel', pkg.id)}</div></div>
      <div><h4>Admin</h4><div class="shared-button-row">${button('approve', pkg.id)}${button('confirmCancel', pkg.id)}</div></div>
    </div>
  </section>`;
}

export function renderSandbox({ workspace }) {
  if (workspace.mode !== 'demo') return empty('Test tools are available in demo mode.');
  const lists = (workspace.pickupLists || []).filter(list => list.claimedBy && list.status !== 'CLOSED');
  return `<div class="delivery-stack">
    ${pageHeader('Test tools')}
    ${inlineMessage('Place holder · Simulate Warehouse, Client and Admin actions on demo records.')}
    <div class="shared-grid-two">${(workspace.demoPackages || []).map(packageCard).join('') || empty('No demo packages.')}</div>
    <section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Stale pickup claims</h2></div>
      <div class="shared-card-body">${lists.map(list => `<div class="shared-button-row">
        <span>${esc(list.id)} · Collector ${esc(list.claimedBy)}</span>
        <button type="button" class="shared-button" data-sandbox="release" data-list="${esc(list.id)}">Simulate release</button>
      </div>`).join('') || '<p class="shared-note">No active claimed lists to review.</p>'}</div>
    </section>
  </div>`;
}

export function handleSandbox(node, ctx) {
  const { sandbox, list, package: packageId } = node.dataset;
  if (!sandbox || ctx.workspace?.mode !== 'demo' || ctx.busy) return;
  if (sandbox === 'release') return ctx.confirm('Simulate claim release?', 'Already collected packages keep their recorded holder.',
    `/api/demo/pickup-lists/${encodeURIComponent(list)}/release`, {}, 'Simulate release');
  if (!Object.hasOwn(actions, sandbox)) return;
  const [text, route, payload, actor] = actions[sandbox];
  ctx.confirm(`Simulate ${actor}?`, 'The backend checks the receipt, instruction and custody before saving.',
    `/api/demo/packages/${encodeURIComponent(packageId)}/${route}`, payload, text);
}
