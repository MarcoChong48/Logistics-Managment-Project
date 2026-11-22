import { badge, empty, escapeHtml as esc, pageHeader, time } from '../../shared/html.js';
import { label } from '../../shared/format.js';

function notice(title, body, date, unread = false) {
  return `<article class="shared-notice${unread ? ' shared-notice-unread' : ''}">
    <div class="shared-notice-text"><h3 class="shared-notice-title">${esc(title)}${unread ? badge('UNREAD', 'warehouse', 'Unread') : ''}</h3>
      <p class="shared-notice-body">${esc(body)}</p>
      <span class="shared-notice-time">${time(date)}</span>
    </div>
  </article>`;
}

export default function render({ workspace, state }) {
  const unread = state.notificationFilter === 'UNREAD';
  const events = (workspace.notifications || []).filter(item => !unread || !item.read);
  const reviews = workspace.reviewQueue || workspace.reviews || [];
  return `<div class="delivery-stack">
    ${pageHeader('Notifications', '', '<button class="shared-button" type="button" data-action="refresh">Refresh</button>')}
    <section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Event notifications <span class="shared-card-count">${events.length}</span></h2>
      <div class="shared-card-actions"><label class="shared-checkbox"><input type="checkbox" data-setting="notificationFilter" value="UNREAD"${unread ? ' checked' : ''}>Unread only</label></div>
    </div>
    <div class="shared-notice-list">${events.map(item => notice(label(item.type), item.message, item.createdAt, !item.read)).join('') || empty('No matching notifications.')}</div></section>
    <section class="shared-card"><div class="shared-card-header"><h2 class="shared-card-title">Review queue <span class="shared-card-count">${reviews.length}</span></h2></div>
      <div class="shared-notice-list">${reviews.map(item => notice([item.title, item.reference].filter(Boolean).join(' · '), item.message, item.createdAt)).join('') || empty('No items need review.')}</div>
    </section>
  </div>`;
}
