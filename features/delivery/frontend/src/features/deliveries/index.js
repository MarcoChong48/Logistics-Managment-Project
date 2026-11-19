import { badge, empty, escapeHtml as esc, icon, options, pageHeader, time } from '../../shared/html.js';
import { FAILURE_REASONS, label, prettyTime } from '../../shared/format.js';
import { selectOutcomeTab, uploadProof } from './outcome.js';

export { attachSignature, handleOutcomeField, renderOutcome } from './outcome.js';

const statuses = ['ALL', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERY_FAILED', 'AWAITING_WAREHOUSE'];

function pageLink(task, page, action, text, extra = '') {
  const ariaLabel = `${text} for order ${task.orderReference}`;
  return `<a href="#${page}/${encodeURIComponent(task.id)}" data-delivery="${action}" data-package="${esc(task.id)}"
    ${extra ? `class="${extra}"` : ''} aria-label="${esc(ariaLabel)}">${esc(text)}</a>`;
}

function action(task, id, title, cssClass, disabled = false) {
  return `<button type="button" class="${cssClass}" data-delivery="${id}" data-package="${esc(task.id)}"
    aria-label="${esc(`${title} for order ${task.orderReference}`)}" ${disabled ? 'disabled' : ''}>${title}</button>`;
}

function actions(task, small = true) {
  const style = small ? 'shared-button shared-button-small' : 'shared-button';
  const main = small ? style : `${style} shared-button-primary`;
  return [
    task.canReturn || task.returnPending ? action(task, 'return', 'Return handoff', style, task.returnPending) : '',
    task.canStart ? action(task, 'start', 'Start delivery', main) : '',
    task.canDeliver || task.canFail ? pageLink(task, 'record-outcome', 'outcome', 'Record outcome', main) : '',
  ].join('');
}

function displayStatus(task) {
  return task.returnPending ? 'AWAITING_WAREHOUSE' : task.status;
}

function contactSummary(task) {
  if (!task.recipientName) return '<span class="shared-cell-sub">Recipient details are unavailable.</span>';
  const phone = String(task.phone || '').replace(/[^+\d]/g, '');
  return `<span class="shared-cell-main">${esc(task.recipientName)}</span>
    <span class="shared-cell-sub">${esc(task.address)}</span>
    ${phone ? `<span class="shared-cell-sub"><a href="tel:${esc(phone)}">${esc(task.phone)}</a></span>` : ''}`;
}

function workerName(user, attempt) {
  return user?.username || attempt?.workerName || user?.id || '';
}

function savedFilters(worker) {
  try {
    const value = JSON.parse(localStorage.getItem(`delivery-filters:${worker}`) || '[]');
    return Array.isArray(value) ? value.filter(filter => filter && typeof filter.search === 'string' && statuses.includes(filter.status)).slice(0, 8) : [];
  } catch { return []; }
}

function row(task, user) {
  const buttons = actions(task);
  const packageLink = `<a class="shared-nowrap" href="#delivery-detail/${encodeURIComponent(task.id)}" data-delivery="details"
    data-package="${esc(task.id)}" aria-label="View order ${esc(task.orderReference)}">${esc(task.orderReference)}</a>`;
  return `<tr>
    <th scope="row" data-title="Order"><span class="shared-cell-main">${packageLink}</span>
      <span class="shared-cell-sub">${esc(task.id)}</span></th>
    <td data-title="Delivery details">${contactSummary(task)}</td>
    <td data-title="State">${badge(displayStatus(task))}
      ${task.completed ? `<span class="shared-cell-sub">Delivery confirmed by ${esc(workerName(user, task.completed))}, ${time(task.completed.endedAt)}.</span>` : ''}</td>
    <td class="shared-table-actions" data-title="Actions">
      ${buttons ? `<div class="shared-cell-buttons">${buttons}</div>` : '<span class="shared-muted">-</span>'}
      ${task.returnPending ? '<span class="shared-cell-sub">Warehouse must confirm receipt.</span>' : ''}
    </td>
  </tr>`;
}

export function renderDeliveries(ctx) {
  const { workspace, state } = ctx;
  const tasks = [...(workspace.tasks || [])];
  // Keep the completed row for feedback using only the returned attempt.
  const completed = workspace.attempts?.find(item => item.id === state.deliveredAttemptId && item.outcome === 'DELIVERED');
  if (completed && !tasks.some(task => task.id === completed.packageId)) {
    tasks.push({
      id: completed.packageId, orderReference: completed.orderReference, status: 'DELIVERED', completed,
    });
    tasks.sort((a, b) => a.id.localeCompare(b.id));
  }
  const search = (state.deliverySearch || '').trim().toLowerCase();
  const shown = tasks.filter(task => task.completed || (
    (state.deliveryStatus === 'ALL' || state.deliveryStatus === displayStatus(task)) &&
    [task.id, task.orderReference, task.recipientName, task.itemSummary, ...(task.products || []).map(product => product.name)]
      .join(' ').toLowerCase().includes(search)
  ));
  const saved = savedFilters(workspace.user.id);
  const filtersSet = search || state.deliveryStatus !== 'ALL';
  return `${pageHeader('My deliveries', `${shown.length} ${shown.length === 1 ? 'delivery' : 'deliveries'}`)}
    <section class="shared-card" aria-label="My deliveries">
      <div class="shared-filter-bar">
        <label class="shared-input-search">${icon('search')}<input type="search" placeholder="Search order, package or recipient"
          aria-label="Search deliveries" data-setting="deliverySearch" value="${esc(state.deliverySearch)}"></label>
        <span class="shared-select"><select aria-label="Delivery status" data-setting="deliveryStatus">
          ${options(statuses.map(status => [status, status === 'ALL' ? 'All statuses' : label(status)]), state.deliveryStatus)}
        </select>${icon('chevron')}</span>
        <span class="shared-filter-buttons">
          <button type="button" class="shared-button" data-delivery="save-filter">Save filter</button>
          ${filtersSet ? '<button type="button" class="shared-button shared-button-quiet" data-delivery="reset-filter">Clear filters</button>' : ''}
        </span>
      </div>
      ${saved.length ? `<div class="shared-filter-bar">
        <span class="shared-note">Saved on this device</span>
        ${saved.map((filter, index) => `<button type="button" class="shared-button shared-button-small" data-delivery="use-filter" data-index="${index}">${esc(filter.search || (filter.status === 'ALL' ? 'All statuses' : label(filter.status)))}</button>`).join('')}
        <button type="button" class="shared-button shared-button-quiet" data-delivery="clear-filters">Clear saved</button>
      </div>` : ''}
      ${shown.length ? `<div class="shared-table-scroll" role="region" aria-label="My deliveries table">
        <table class="shared-table shared-table-work">
          <thead><tr><th scope="col">Order</th><th scope="col">Delivery details</th><th scope="col">State</th>
            <th scope="col" class="shared-table-actions">Actions</th></tr></thead>
          <tbody>${shown.map(task => row(task, workspace.user)).join('')}</tbody>
        </table>
      </div>` : empty(tasks.length ? 'No deliveries match these filters.' : 'No collected packages yet.')}
    </section>`;
}

function fact(name, value) {
  return `<div class="shared-fact"><dt class="shared-fact-label">${name}</dt>
    <dd class="shared-fact-value">${value || '-'}</dd></div>`;
}

function attemptTimeline(attempts, summaries, user) {
  const events = attempts.flatMap(attempt => {
    const actor = workerName(user, attempt);
    const started = { date: attempt.startedAt, text: 'Started delivery', actor };
    if (attempt.outcome === 'IN_PROGRESS') return [started];
    const reason = FAILURE_REASONS.find(([id]) => id === attempt.reason)?.[1] || label(attempt.reason);
    const proofs = (attempt.proofs || []).map(proof => proof.url
      ? `<button type="button" class="shared-button shared-button-quiet shared-button-small" data-preview="${esc(proof.id)}" aria-label="Preview ${esc(label(proof.type).toLowerCase())} ${esc(proof.id)}">${esc(label(proof.type))}</button>`
      : esc(label(proof.type)));
    return [started, {
      date: attempt.endedAt,
      text: `${esc(label(attempt.outcome))}${reason ? ` · ${esc(reason)}` : ''}${attempt.note ? ` · “${esc(attempt.note)}”` : ''}${proofs.length ? ` · ${proofs.join(', ')}` : ''}`,
      actor,
    }];
  });
  for (const summary of summaries) {
    events.push({ date: summary.eventAt, text: `${esc(label(summary.outcome))} · Details expired`, actor: workerName(user) });
  }
  const days = new Map();
  for (const event of events.sort((a, b) => Date.parse(a.date) - Date.parse(b.date))) {
    if (!event.date || !Number.isFinite(Date.parse(event.date))) continue;
    const [day, clock] = prettyTime(event.date).split(' ');
    if (!days.has(day)) days.set(day, []);
    days.get(day).push(`<li class="shared-timeline-entry">
      <time class="shared-timeline-time" datetime="${esc(event.date)}">${esc(clock)}</time>
      <span class="shared-timeline-text">${event.text}${event.actor ? `<span class="shared-timeline-actor"> ${esc(event.actor)}</span>` : ''}</span>
    </li>`);
  }
  return days.size ? [...days].map(([day, entries]) => `<div class="shared-timeline-day">
    <h3 class="shared-timeline-date">${esc(day)}</h3><ol class="shared-timeline">${entries.join('')}</ol>
  </div>`).join('') : '<p class="shared-note">No delivery attempts yet.</p>';
}

function detailState(status) {
  return status === 'AWAITING_ADMIN_APPROVAL'
    ? badge(status, 'problem', 'Waiting for Admin approval')
    : badge(status);
}

export function renderDeliveryDetails(ctx) {
  const { workspace, state } = ctx;
  const back = '<a class="shared-page-back" href="#deliveries" data-delivery="back">Back to my deliveries</a>';
  const task = workspace.tasks?.find(item => item.id === state.selected);
  const attempts = (workspace.attempts || []).filter(item => item.packageId === state.selected);
  const receipts = (workspace.returns || []).filter(item => item.packageId === state.selected);
  const summaries = (workspace.retainedSummaries || []).filter(item => item.packageId === state.selected);
  const receipt = receipts[0];
  const attempt = attempts[0];
  const summary = summaries[0];
  const orderReference = task?.orderReference || attempt?.orderReference || receipt?.orderReference || summary?.orderReference;
  if (!orderReference) return `${pageHeader('Delivery details', '', '', back)}
    <section class="shared-card"><div class="shared-card-body"><p class="shared-note">This delivery is unavailable.</p></div></section>`;
  const followUp = receipt && Object.hasOwn(receipt, 'followUpStatus')
    ? receipt.followUpStatus
    : receipt?.awaitingInstruction ? 'AWAITING_CLIENT_INSTRUCTION' : null;
  const latestState = [
    [receipt?.status, receipt?.receivedAt || receipt?.createdAt],
    [attempt?.outcome, attempt?.endedAt || attempt?.startedAt],
    [summary?.outcome, summary?.eventAt],
  ].filter(([, date]) => date).sort((a, b) => Date.parse(b[1]) - Date.parse(a[1]))[0]?.[0];
  const stateLabel = task ? displayStatus(task) : followUp || latestState;
  const phone = String(task?.phone || '').replace(/[^+\d]/g, '');
  return `${pageHeader(`Order ${orderReference}`, '', task ? actions(task, false) : '', back)}
    <section class="shared-card">
      <div class="shared-card-header"><h2 class="shared-card-title">Delivery details</h2></div>
      <div class="shared-card-body"><dl class="shared-fact-list">
        ${fact('Package', esc(state.selected))}
        ${fact(task || followUp ? 'State' : 'Last recorded state', detailState(stateLabel))}
        ${task?.recipientName ? `${fact('Recipient', esc(task.recipientName))}
          ${fact('Phone', phone ? `<a href="tel:${esc(phone)}">${esc(task.phone)}</a>` : '')}
          ${fact('Address', esc(task.address))}` : ''}
        ${task?.products?.length ? fact('Items', task.products.map(item => `${esc(item.quantity)} × ${esc(item.name)}`).join('<br>')) : ''}
      </dl>
      ${!task?.recipientName ? '<p class="shared-note">Recipient details are unavailable.</p>' : ''}
      ${task?.returnPending ? '<p class="shared-note">Warehouse must confirm receipt.</p>' : ''}
      </div>
    </section>
    ${receipt ? `<section class="shared-card">
      <div class="shared-card-header"><h2 class="shared-card-title">Latest return handoff</h2></div>
      <div class="shared-card-body"><dl class="shared-fact-list">
        ${fact('Receipt', esc(receipt.id))}${fact('Receipt state', badge(receipt.status))}
        ${followUp ? fact('Follow-up', detailState(followUp)) : ''}
        ${fact('Reported', time(receipt.createdAt))}
        ${receipt.receivedAt ? fact('Received', time(receipt.receivedAt)) : ''}
        ${receipt.condition ? fact('Condition', esc(label(receipt.condition))) : ''}
        ${receipt.reason ? fact('Reason', `“${esc(receipt.reason)}”`) : ''}
      </dl></div>
    </section>` : ''}
    <section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Delivery attempts</h2></div>
      <div class="shared-card-body">${attemptTimeline(attempts, summaries, workspace.user)}</div>
    </section>`;
}

function reportReturn(ctx, task) {
  ctx.modal(`Report return handoff for ${task.orderReference}`, `<form>
    <div class="shared-field"><p class="shared-note">You keep recorded custody until Warehouse confirms receipt.</p></div>
    <div class="shared-field">
      <label class="shared-field-label" for="delivery-return-reason">Return reason</label>
      <textarea class="shared-textarea" id="delivery-return-reason" name="reason" maxlength="600" rows="3" required>${task.status === 'DELIVERY_FAILED' ? 'Returning package after failed delivery' : ''}</textarea>
    </div>
    <div class="shared-dialog-footer">
      <button class="shared-button" type="button" data-close>Cancel</button>
      <button class="shared-button shared-button-primary" type="submit">Report handoff</button>
    </div></form>`, dialog => {
    const form = dialog.querySelector('form');
    const field = form.elements.reason;
    const submit = form.querySelector('[type="submit"]');
    const update = () => { submit.disabled = !field.value.trim(); };
    field.addEventListener('input', update);
    update();
    form.addEventListener('submit', async event => {
      event.preventDefault();
      const reason = field.value.trim();
      if (ctx.busy || !reason) return;
      submit.disabled = true;
      if (await ctx.mutate(`/api/packages/${encodeURIComponent(task.id)}/return`, { reason })) dialog.close();
      else update();
    });
  });
}

function recordOutcome(ctx, task, payload) {
  const success = payload.outcome === 'DELIVERED';
  ctx.confirm(success ? 'Confirm successful delivery?' : 'Record failed attempt?',
    success ? `Mark ${task.orderReference} delivered with the selected proof. This removes your access to the private contact and proof files.`
      : `Record the failed delivery for ${task.orderReference}. You keep custody until Warehouse confirms the return.`,
    `/api/packages/${encodeURIComponent(task.id)}/outcome`, payload,
    success ? 'Confirm delivered' : 'Record failed attempt', () => {
      if (success) ctx.state.deliveredAttemptId = task.activeAttemptId;
      ctx.navigate('deliveries');
      const orderLink = [...document.querySelectorAll('a[data-delivery="details"]')].find(link => link.dataset.package === task.id);
      orderLink?.focus();
    });
}

function handleFilter(ctx, action, index) {
  const key = `delivery-filters:${ctx.workspace.user.id}`;
  const { state } = ctx;
  state.deliveredAttemptId = null;
  if (action === 'reset-filter') {
    state.deliverySearch = '';
    state.deliveryStatus = 'ALL';
    return ctx.render();
  }
  try {
    const filters = savedFilters(ctx.workspace.user.id);
    if (action === 'save-filter') {
      // Only save order and package codes so search terms stay private.
      const search = state.deliverySearch.trim();
      const safeSearch = /^(?:LMS|PKG|ORD)-[A-Z0-9-]+$/i.test(search) ? search : '';
      const filter = { search: safeSearch, status: state.deliveryStatus };
      localStorage.setItem(key, JSON.stringify([filter, ...filters.filter(item => item.search !== filter.search || item.status !== filter.status)].slice(0, 8)));
    } else if (action === 'clear-filters') {
      localStorage.removeItem(key);
    } else if (filters[index]) {
      state.deliverySearch = filters[index].search;
      state.deliveryStatus = filters[index].status;
    }
    ctx.render();
  } catch { ctx.flash('Saved filters are unavailable on this device.', 'error'); }
}

export function handleDelivery(node, ctx) {
  const { delivery, package: packageId, index } = node.dataset;
  const task = ctx.workspace.tasks?.find(item => item.id === packageId);
  if (ctx.busy) return;
  if (node.matches('[data-photo]') && task?.canDeliver) {
    uploadProof(ctx, task, 'PHOTO', node.files?.[0]);
    node.value = '';
    return;
  }
  if (node.matches('[data-failure-form]') && task?.canFail) {
    const { reason, note } = Object.fromEntries(new FormData(node));
    if (!FAILURE_REASONS.some(([id]) => id === reason)) return ctx.flash('Choose a failure reason.', 'error');
    if (reason === 'OTHER' && !note.trim()) return ctx.flash('Enter an internal note for Other.', 'error');
    return recordOutcome(ctx, task, { outcome: 'FAILED', reason, note: note.trim(), proofIds: [] });
  }
  if (['save-filter', 'clear-filters', 'use-filter', 'reset-filter'].includes(delivery)) return handleFilter(ctx, delivery, index);
  if (delivery === 'back') return ctx.navigate('deliveries');
  if (delivery === 'details') return ctx.navigate('delivery-detail', packageId);
  if (!task) return;
  if (delivery === 'tab') return selectOutcomeTab(node, ctx.state);
  if ((delivery === 'camera' || delivery === 'file') && task.canDeliver) {
    return document.getElementById(delivery === 'camera' ? 'camera-file' : 'photo-file')?.click();
  }
  if (delivery === 'return' && task.canReturn) return reportReturn(ctx, task);
  if (delivery === 'start' && task.canStart) return ctx.confirm('Start this delivery?',
    `Start the attempt for ${task.orderReference}. It will use the delivery details confirmed for this pickup.`,
    `/api/packages/${encodeURIComponent(task.id)}/start`, {}, 'Start delivery');
  if (delivery === 'outcome' && (task.canDeliver || task.canFail)) return ctx.navigate('record-outcome', packageId);
  if (delivery === 'complete' && task.canDeliver) {
    const proofIds = [...document.querySelectorAll('[data-proof-id]:checked')].map(item => item.dataset.proofId);
    if (!proofIds.length) return ctx.flash('Select a saved proof.', 'error');
    return recordOutcome(ctx, task, { outcome: 'DELIVERED', reason: null, note: null, proofIds });
  }
}
