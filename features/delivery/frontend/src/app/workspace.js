import { escapeHtml as esc, icon, inlineMessage } from '../shared/html.js';
import { shell } from './shell.js';
import { login, changePassword } from '../features/access/login.js';
import renderOverview from '../features/overview/index.js';
import renderHistory from '../features/history/index.js';
import renderNotifications from '../features/notifications/index.js';
import renderIntegrations from '../features/integrations/index.js';
import { renderPickup, handlePickup } from '../features/pickup/index.js';
import {
  renderDeliveries, renderDeliveryDetails, renderOutcome,
  handleDelivery, handleOutcomeField, attachSignature,
} from '../features/deliveries/index.js';
import renderReturns from '../features/returns/index.js';
import { renderSandbox, handleSandbox } from '../features/sandbox/index.js';

const screens = {
  overview: renderOverview, pickup: renderPickup, deliveries: renderDeliveries,
  'delivery-detail': renderDeliveryDetails, 'record-outcome': renderOutcome,
  returns: renderReturns, history: renderHistory, notifications: renderNotifications,
  integrations: renderIntegrations, 'test-tools': renderSandbox, 'change-password': changePassword,
};
const itemPages = new Set(['delivery-detail', 'record-outcome']);

function readRoute() {
  const [page, id] = location.hash.slice(1).split('/');
  try {
    if (Object.hasOwn(screens, page)) {
      return [page, itemPages.has(page) && id ? decodeURIComponent(id) : null];
    }
  } catch { /* A malformed link opens the overview. */ }
  return ['overview', null];
}

export default function start(root) {
  const defaultView = {
    page: 'overview', menuOpen: false, historySearch: '', historyOutcome: 'ALL',
    historyProof: 'ALL', notificationFilter: 'ALL', deliverySearch: '', deliveryStatus: 'ALL',
    outcome: 'DELIVERED', selected: null, passwordBack: 'overview', deliveredAttemptId: null,
  };
  const state = { ...defaultView, loginError: '', loginNotice: '', bootError: '' };
  let session;
  let workspace;
  let busy = false;
  let currentDialog;
  let detachSignature;
  let deadline = null;
  let expiryCheck = false;
  let messageTimeout;
  const requestKeys = new Map();
  const ctx = {
    state, api, mutate, upload, modal, confirm, navigate, render, flash,
    get workspace() { return workspace; },
    get busy() { return busy; },
  };

  function headingFocus() {
    const heading = root.querySelector('h1[tabindex="-1"]') || root.querySelector('main[tabindex="-1"]');
    heading?.focus({ preventScroll: true });
  }

  function render() {
    detachSignature?.();
    detachSignature = undefined;
    const active = document.activeElement;
    const setting = active?.dataset.setting;
    const selection = active?.tagName === 'INPUT' ? active.selectionStart : null;
    if (state.bootError) {
      root.innerHTML = `<main class="shared-centre-page"><section class="shared-card shared-centre-card">
        <h1 class="shared-centre-title">LMS Delivery</h1>${inlineMessage(state.bootError, 'error')}
        <button class="shared-button shared-button-primary" data-action="retry">Retry connection</button>
      </section></main>`;
    } else if (!session?.authenticated || !workspace) {
      root.innerHTML = login(state, session);
    } else {
      const screen = screens[state.page](ctx);
      root.innerHTML = state.page === 'change-password' ? screen : shell(ctx, screen);
      detachSignature = attachSignature(root, ctx);
      showCountdown();
    }
    const title = root.querySelector('h1')?.textContent;
    if (title) document.title = `${title} - LMS Delivery`;
    if (setting) {
      const target = [...root.querySelectorAll('[data-setting]')].find(node => node.dataset.setting === setting);
      target?.focus();
      if (selection !== null && target?.tagName === 'INPUT') target.setSelectionRange(selection, selection);
    }
  }

  function navigate(page, packageId, fromHistory = false) {
    if (!workspace || !Object.hasOwn(screens, page)) return;
    if (page === 'test-tools' && workspace.mode !== 'demo') page = 'overview';
    if (itemPages.has(page) && !packageId) page = 'deliveries';
    if (page === 'change-password' && state.page !== page) {
      state.passwordBack = itemPages.has(state.page) ? 'deliveries' : state.page;
    }
    currentDialog?.close();
    if (page !== 'deliveries') state.deliveredAttemptId = null;
    state.page = page;
    state.menuOpen = false;
    state.selected = itemPages.has(page) ? packageId : null;
    state.outcome = 'DELIVERED';
    const hash = '#' + page + (state.selected ? '/' + encodeURIComponent(state.selected) : '');
    if (!fromHistory && location.hash !== hash) history.pushState(null, '', hash);
    if (fromHistory && location.hash !== hash) history.replaceState(null, '', hash);
    render();
    window.scrollTo(0, 0);
    headingFocus();
  }

  function menu(open) {
    state.menuOpen = open;
    const frame = root.querySelector('.shared-page-frame');
    frame?.classList.toggle('shared-page-frame-menu-open', open);
    const trigger = root.querySelector('[data-action="open-menu"]');
    trigger?.setAttribute('aria-expanded', String(open));
    for (const part of root.querySelectorAll('.shared-skip-link, .shared-page-frame-top-bar, #delivery-main')) {
      part.inert = open;
    }
    if (open) {
      const scrim = document.createElement('div');
      scrim.className = 'delivery-menu-scrim';
      scrim.dataset.action = 'close-menu';
      scrim.setAttribute('aria-hidden', 'true');
      frame?.insertBefore(scrim, frame.querySelector('header'));
      root.querySelector('[data-action="close-menu"]')?.focus();
    } else {
      root.querySelector('.delivery-menu-scrim')?.remove();
      trigger?.focus();
    }
  }

  function setDeadline(expiresAt, serverTime) {
    if (!Number.isFinite(expiresAt) || !Number.isFinite(serverTime)) return;
    deadline = performance.now() + Math.max(0, expiresAt - serverTime);
    showCountdown();
  }

  function showCountdown() {
    const display = root.querySelector('[data-countdown]');
    if (!display || deadline === null) return;
    const seconds = Math.max(0, Math.ceil((deadline - performance.now()) / 1000));
    display.textContent = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
  }

  async function checkExpiry() {
    showCountdown();
    if (!session?.authenticated || deadline === null || busy || expiryCheck ||
        performance.now() < deadline) return;
    expiryCheck = true;
    try {
      await loadSession();
      if (!session.authenticated) endSession('Your session ended. Log in again.');
    } catch {
      session = null;
      endSession('Your session could not be verified. Log in again.');
    } finally {
      expiryCheck = false;
    }
  }

  function endSession(message, kind = 'information') {
    currentDialog?.close();
    workspace = null;
    deadline = null;
    requestKeys.clear();
    Object.assign(state, defaultView, { loginError: '', loginNotice: message, loginNoticeKind: kind });
    history.replaceState(null, '', location.pathname + location.search);
    render();
    headingFocus();
  }

  async function api(path, options = {}) {
    const headers = new Headers(options.headers);
    if (options.method === 'POST' && session?.csrfToken) headers.set(session.csrfHeader, session.csrfToken);
    let response;
    try {
      response = await fetch(path, { ...options, headers, credentials: 'same-origin', cache: 'no-store' });
    } catch {
      const error = new Error('Cannot reach the server. Try again when the connection returns.');
      error.uncertain = true;
      throw error;
    }
    if (response.headers.has('X-Session-Expires-At') && response.headers.has('X-Server-Time')) {
      setDeadline(Number(response.headers.get('X-Session-Expires-At')), Number(response.headers.get('X-Server-Time')));
    }
    const data = response.headers.get('content-type')?.includes('application/json') ? await response.json() : null;
    if (!response.ok) {
      if (path !== '/api/login' && path !== '/api/session' && (response.status === 401 || response.status === 403)) {
        await loadSession().catch(() => { session = null; });
        if (!session?.authenticated) endSession('Your session ended. Log in again.');
      }
      const error = new Error(data?.message || `Request failed (${response.status}).`);
      error.status = response.status;
      error.uncertain = response.status >= 500;
      throw error;
    }
    return data;
  }

  async function loadSession() {
    session = await api('/api/session');
    if (session.authenticated) setDeadline(session.sessionExpiresAt, session.serverTime);
    else deadline = null;
  }

  async function load() {
    await loadSession();
    workspace = session.authenticated ? await api('/api/delivery/workspace') : null;
  }

  async function refresh(control) {
    if (busy) return;
    busy = true;
    control.disabled = true;
    try {
      workspace = await api('/api/delivery/workspace');
      state.deliveredAttemptId = null;
      render();
      root.querySelector('[data-action="refresh"]')?.focus({ preventScroll: true });
      flash('Updated.');
    } catch (error) {
      flash(error.message, 'error');
    } finally {
      busy = false;
      control.disabled = false;
    }
  }

  function clearMessages() {
    clearTimeout(messageTimeout);
    root.querySelector('[data-messages]')?.replaceChildren();
  }

  function flash(message, tone = 'success') {
    clearMessages();
    if (currentDialog?.open) {
      const feedback = currentDialog.querySelector('[data-dialog-feedback]');
      if (feedback) feedback.innerHTML = inlineMessage(message, tone);
      return;
    }
    const host = root.querySelector('[data-messages]');
    if (!host) return;
    host.innerHTML = `<div class="shared-message-box shared-message-box-${tone}" role="${tone === 'error' ? 'alert' : 'status'}">
      ${icon(tone === 'error' ? 'alert' : 'check')}<p>${esc(message)}</p></div>`;
    messageTimeout = setTimeout(clearMessages, tone === 'error' ? 7000 : 4000);
  }

  async function submit(path, key, body, message) {
    if (busy) return false;
    busy = true;
    const requestId = requestKeys.get(key) || crypto.randomUUID();
    requestKeys.set(key, requestId);
    const dialog = currentDialog;
    const disabled = [...(dialog || root).querySelectorAll('button:not(:disabled)')];
    disabled.forEach(button => { button.disabled = true; });
    try {
      const result = await api(path, { method: 'POST', ...body(requestId) });
      workspace = await api('/api/delivery/workspace');
      requestKeys.delete(key);
      render();
      const status = result?.message || message;
      if (dialog?.open) dialog.addEventListener('close', () => flash(status), { once: true });
      else flash(status);
      return true;
    } catch (error) {
      if (!error.uncertain) requestKeys.delete(key);
      if (error.status === 409 && workspace) {
        try {
          workspace = await api('/api/delivery/workspace');
          if (!dialog?.open) render();
        } catch { /* Keep the current screen if refresh fails. */ }
      }
      flash(error.message, 'error');
      return false;
    } finally {
      busy = false;
      disabled.forEach(button => { button.disabled = false; });
    }
  }

  function mutate(path, payload = {}) {
    return submit(path, path + ':' + JSON.stringify(payload), requestId => ({
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, requestId }),
    }), 'Saved.');
  }

  async function upload(path, type, file) {
    const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
    const fingerprint = [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
    return submit(path, path + ':' + type + ':' + fingerprint, requestId => {
      const body = new FormData();
      body.append('type', type);
      body.append('file', file);
      body.append('requestId', requestId);
      return { body };
    }, 'Proof saved.');
  }

  function modal(title, contents, setup) {
    currentDialog?.close();
    clearMessages();
    const opener = document.activeElement;
    const dialog = document.createElement('dialog');
    dialog.className = 'shared-dialog';
    dialog.setAttribute('aria-labelledby', 'delivery-dialog-title');
    dialog.innerHTML = `<h2 id="delivery-dialog-title" class="shared-dialog-title">${esc(title)}</h2>
      <div class="shared-dialog-body"><div class="shared-message-place" aria-live="polite" data-dialog-feedback></div>${contents}</div>`;
    if (!dialog.querySelector('[data-close]')) {
      dialog.insertAdjacentHTML('beforeend', '<div class="shared-dialog-footer"><button type="button" class="shared-button shared-button-primary" data-close>Close</button></div>');
    }
    for (const cancel of dialog.querySelectorAll('[data-close]')) cancel.onclick = () => dialog.close();
    dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
    dialog.addEventListener('close', () => {
      if (currentDialog !== dialog) {
        dialog.remove();
        return;
      }
      currentDialog = null;
      dialog.remove();
      if (opener?.isConnected && !opener.disabled) opener.focus();
      else if (opener?.dataset.package) {
        const record = [...root.querySelectorAll('a[data-package]')].find(link => link.dataset.package === opener.dataset.package);
        if (record) record.focus();
        else headingFocus();
      } else headingFocus();
    });
    document.body.append(dialog);
    currentDialog = dialog;
    dialog.showModal();
    setup?.(dialog);
    return dialog;
  }

  function confirm(title, message, path, payload, text, after) {
    const finalAction = payload.decision === 'APPROVE_CANCELLATION';
    modal(title, `<p>${esc(message)}</p><div class="shared-dialog-footer">
      <button type="button" class="shared-button" data-close>${finalAction ? 'Close' : 'Cancel'}</button>
      <button type="button" class="shared-button ${finalAction ? 'shared-button-danger-filled' : 'shared-button-primary'}" data-accept>${esc(text)}</button>
    </div>`, dialog => {
      dialog.querySelector('[data-accept]').onclick = async () => {
        if (await mutate(path, payload)) {
          if (after) dialog.addEventListener('close', after, { once: true });
          dialog.close();
        }
      };
    });
  }

  async function boot() {
    state.bootError = '';
    try { await load(); }
    catch (error) { state.bootError = error.message; }
    if (workspace) navigate(...readRoute(), true);
    else render();
  }

  async function signIn(form) {
    const fields = new FormData(form);
    state.username = String(fields.get('username') || '');
    state.loginNotice = '';
    try {
      if (!session?.csrfToken) await loadSession();
      await api('/api/login', { method: 'POST', body: new URLSearchParams(fields) });
      await load();
      state.loginError = '';
      if (workspace) navigate(...readRoute(), true);
      else render();
    } catch (error) {
      state.loginError = error.message;
      await loadSession().catch(() => {});
      render();
    }
  }

  async function savePassword(form) {
    const values = Object.fromEntries(new FormData(form));
    const feedback = form.querySelector('[data-password-feedback]');
    if (values.newPassword !== values.confirmPassword) {
      feedback.innerHTML = inlineMessage('The new passwords do not match.', 'error');
      form.elements.confirmPassword.focus();
      return;
    }
    feedback.replaceChildren();
    try {
      const result = await api('/api/change-password', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(values),
      });
      form.reset();
      session = null;
      endSession(result?.message || 'Password changed. Log in again.', 'success');
      await loadSession().catch(() => {});
      render();
      headingFocus();
    } catch (error) {
      feedback.innerHTML = inlineMessage(error.message, 'error');
    }
  }

  root.addEventListener('submit', async event => {
    const form = event.target;
    if (form.matches('[data-login], [data-change-password]')) {
      event.preventDefault();
      if (busy) return;
      busy = true;
      const button = form.querySelector('[type=submit]');
      button.disabled = true;
      try {
        if (form.matches('[data-login]')) await signIn(form);
        else await savePassword(form);
      } finally {
        busy = false;
        button.disabled = false;
      }
    } else if (form.matches('[data-pickup-form], [data-failure-form]')) {
      event.preventDefault();
      if (form.matches('[data-pickup-form]')) handlePickup(form, ctx);
      else handleDelivery(form, ctx);
    }
  });

  root.addEventListener('input', event => {
    const { setting } = event.target.dataset;
    if (setting && Object.hasOwn(state, setting)) {
      state[setting] = event.target.type === 'checkbox' && !event.target.checked ? 'ALL' : event.target.value;
      render();
    } else handleOutcomeField(event, ctx);
  });

  root.addEventListener('keydown', event => {
    if (event.key === 'Escape' && state.menuOpen) menu(false);
    else handleOutcomeField(event, ctx);
  });

  root.addEventListener('click', async event => {
    const control = event.target.closest('button, a[data-nav], a[data-action], a[data-delivery], [data-action="close-menu"]');
    if (!control) return;
    if (control.tagName === 'A') {
      if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
    }
    if (control.dataset.nav) return navigate(control.dataset.nav);
    if (control.dataset.preview) {
      if (!/^[A-Za-z0-9-]+$/.test(control.dataset.preview)) return;
      modal('Proof preview', `<img class="delivery-proof-image" alt="Delivery proof" src="/api/proofs/${encodeURIComponent(control.dataset.preview)}">`);
      return;
    }
    switch (control.dataset.action) {
      case 'skip': return root.querySelector('#delivery-main')?.focus();
      case 'retry': return boot();
      case 'open-menu': return menu(true);
      case 'close-menu': return menu(false);
      case 'refresh': return refresh(control);
      case 'back-password': return navigate(state.passwordBack);
      case 'logout':
        if (busy) return;
        try {
          await api('/api/logout', { method: 'POST' });
          session = null;
          endSession('You are logged out.');
          await loadSession().catch(() => {});
          render();
          headingFocus();
        } catch (error) { flash(error.message, 'error'); }
        return;
      default:
        if (!workspace) return;
        handlePickup(control, ctx);
        handleDelivery(control, ctx);
        handleSandbox(control, ctx);
    }
  });

  root.addEventListener('change', event => {
    if (event.target.matches('[data-photo]')) handleDelivery(event.target, ctx);
  });

  window.addEventListener('hashchange', () => navigate(...readRoute(), true));
  window.setInterval(checkExpiry, 1000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) checkExpiry(); });
  boot();
}
