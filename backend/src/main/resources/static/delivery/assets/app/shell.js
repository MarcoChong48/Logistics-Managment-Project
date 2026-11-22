import { escapeHtml as esc, icon } from '../shared/html.js';

const work = [
  ['overview', 'Overview', 'dashboard'],
  ['pickup', 'Pickup', 'warehouse'],
  ['deliveries', 'My deliveries', 'delivery'],
  ['returns', 'Return handoffs', 'refunds'],
];
const lookup = [
  ['history', 'Attempt history', 'logs'],
  ['notifications', 'Notifications', 'information'],
  ['integrations', 'Decisions & integrations', 'orders'],
];

function menuEntry([page, name, glyph], current, count) {
  const active = current === page;
  const countId = `delivery-menu-count-${page}`;
  return `<a href="#${page}" data-nav="${page}" class="shared-page-frame-menu-item${active ? ' shared-page-frame-menu-item-active' : ''}"
    aria-label="${esc(name)}"${count ? ` aria-describedby="${countId}"` : ''}${active ? ' aria-current="page"' : ''}>
    ${icon(glyph)}<span class="shared-page-frame-menu-text">${esc(name)}</span>
    ${count ? `<span class="shared-page-frame-menu-count" id="${countId}">${esc(count)}</span>` : ''}
  </a>`;
}

export function shell(ctx, screen) {
  const { state, workspace } = ctx;
  const user = esc(workspace.user.username || workspace.user.id);
  const current = ['delivery-detail', 'record-outcome'].includes(state.page) ? 'deliveries' : state.page;
  const inert = state.menuOpen ? ' inert' : '';
  const unread = (workspace.notifications || []).filter(item => !item.read).length;
  return `<a class="shared-skip-link" href="#delivery-main" data-action="skip"${inert}>Skip to main content</a>
  <div class="shared-page-frame shared-page-frame-adaptive${state.menuOpen ? ' shared-page-frame-menu-open' : ''}" data-testid="workspace-shell">
    <nav class="shared-page-frame-side-menu" id="delivery-menu" aria-label="Delivery pages">
      <div class="shared-page-frame-name">LMS Delivery
        <button type="button" class="shared-button shared-button-small shared-page-frame-menu-close" data-action="close-menu">Close menu</button>
      </div>
      ${menuEntry(work[0], current)}
      <div class="shared-page-frame-menu-label">Work to do</div>
      ${work.slice(1).map(item => menuEntry(item, current, item[0] === 'returns' ? workspace.stats?.pendingReturns : 0)).join('')}
      <div class="shared-page-frame-menu-label">Look up</div>
      ${lookup.map(item => menuEntry(item, current, item[0] === 'notifications' ? unread : 0)).join('')}
      <div class="shared-page-frame-menu-footer"><span class="shared-page-frame-user">${user}</span>
        <a href="/shared/shared-change-password.html">Change password</a>
      </div>
    </nav>
    ${state.menuOpen ? '<div class="delivery-menu-scrim" data-action="close-menu" aria-hidden="true"></div>' : ''}
    <header class="shared-page-frame-top-bar"${inert}>
      <button type="button" class="shared-button shared-button-small shared-page-frame-menu-button" data-action="open-menu" aria-controls="delivery-menu" aria-expanded="${state.menuOpen}">Menu</button>
      <span class="shared-page-frame-spacer"></span>
      <span class="shared-page-frame-countdown">${icon('clock')}<span class="shared-page-frame-countdown-words">Session ends in</span> <span id="sharedStartSessionCountdownText" role="timer" aria-label="Time until session expires">-</span></span>
      <span class="shared-page-frame-user">${user}</span>
      <a class="shared-page-frame-top-link" href="/shared/shared-change-password.html">Change password</a>
      <button type="button" class="shared-button shared-button-small" data-action="logout">Log out</button>
    </header>
    <main class="shared-page-frame-main" id="delivery-main" tabindex="-1"${inert}>${screen}</main>
  </div>
  <div class="shared-message-area" id="sharedShowMessagePanel" aria-live="polite" data-messages></div>`;
}
