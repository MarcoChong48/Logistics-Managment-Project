import { label, prettyTime } from './format.js';

export function escapeHtml(value) {
  const entities = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
  return String(value ?? '').replace(/[&<>"']/g, char => entities[char]);
}

const iconNames = new Set([
  'dashboard', 'users', 'products', 'orders', 'warehouse', 'delivery', 'refunds', 'logs',
  'test-tools', 'search', 'plus', 'chevron', 'clock', 'download', 'check', 'alert', 'information', 'tick',
]);
const aliases = {
  box: 'products', grid: 'dashboard', scan: 'warehouse', truck: 'delivery', return: 'refunds',
  history: 'logs', bell: 'information', layers: 'orders', arrow: 'chevron', close: 'chevron',
  camera: 'information', pen: 'orders', upload: 'plus', info: 'information', user: 'users',
  lock: 'information', refresh: 'clock', menu: 'dashboard', logout: 'chevron',
  pin: 'information', phone: 'users', save: 'check', file: 'orders',
};

export function icon(name, _size, className = '') {
  const symbol = aliases[name] || name;
  return `<svg class="shared-icon ${escapeHtml(className)}" aria-hidden="true"><use href="#icon-${iconNames.has(symbol) ? symbol : 'information'}"></use></svg>`;
}

const families = {
  waiting: ['AWAITING_PAYMENT', 'AWAITING_WAREHOUSE', 'AWAITING_CLIENT_INSTRUCTION', 'PENDING', 'WAITING_RESTOCK', 'LOCKED', 'CLAIMED'],
  warehouse: ['READY_FOR_PICKING', 'PICKING', 'READY_FOR_PICKUP', 'READY_FOR_REDELIVERY', 'IN_PROGRESS', 'WAREHOUSE'],
  road: ['SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERY', 'COLLECTED'],
  done: ['DELIVERED', 'RECEIVED', 'RETURNED', 'INTACT', 'SUCCESS', 'ACTIVE', 'APPROVED', 'ACCEPTED', 'PACKED', 'RESOLVED', 'REFUNDED'],
  problem: ['FAILED', 'DELIVERY_FAILED', 'DAMAGED', 'CANNOT_COLLECT', 'STALE', 'OPEN', 'ON_HOLD', 'AWAITING_ADMIN_APPROVAL', 'REJECTED', 'BANNED'],
  closed: ['CANCELLED', 'CLOSED', 'SUPERSEDED', 'WITHDRAWN', 'REVERSED', 'INACTIVE'],
};
const tones = { green: 'done', red: 'problem', amber: 'waiting', blue: 'warehouse', navy: 'road', gray: 'closed' };

export function badge(value, tone = '', text = label(value)) {
  const family = tones[tone] || (Object.hasOwn(families, tone) && tone) ||
    Object.keys(families).find(key => families[key].includes(value)) || 'closed';
  return `<span class="shared-status-badge shared-status-badge-${family}">${escapeHtml(text || '-')}</span>`;
}

export function empty(title, message = '') {
  return `<p class="shared-table-empty">${escapeHtml(title)}${message ? ` ${escapeHtml(message)}` : ''}</p>`;
}

export function options(choices, selected) {
  return choices.map(([id, name]) => `<option value="${escapeHtml(id)}"${selected === id ? ' selected' : ''}>${escapeHtml(name)}</option>`).join('');
}

export function time(value) {
  return value ? `<time class="shared-nowrap" datetime="${escapeHtml(value)}">${escapeHtml(prettyTime(value))}</time>` : '-';
}

export function pageHeader(title, count = '', actions = '', back = '') {
  return `<div class="shared-page-header"><div class="shared-page-header-text">${back}
    <h1 class="shared-page-title" tabindex="-1">${escapeHtml(title)}</h1>
    ${count === '' ? '' : `<p class="shared-page-subtitle">${escapeHtml(count)}</p>`}
  </div>${actions ? `<div class="shared-page-header-actions">${actions}</div>` : ''}</div>`;
}

export function inlineMessage(message, kind = 'information') {
  const variant = kind === 'information' ? '' : ` shared-inline-message-${kind}`;
  return `<div class="shared-inline-message${variant}" role="${kind === 'error' ? 'alert' : 'status'}">${icon(kind === 'error' ? 'alert' : kind === 'success' ? 'check' : 'information')}<p>${escapeHtml(message)}</p></div>`;
}
