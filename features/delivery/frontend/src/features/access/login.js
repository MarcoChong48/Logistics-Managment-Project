import { escapeHtml as esc, inlineMessage } from '../../shared/html.js';

export function login(state, session) {
  const demo = session?.mode === 'demo';
  return `<main class="shared-centre-page" data-testid="login-page">
    <p class="shared-centre-name">LMS Delivery</p>
    <form class="shared-card shared-centre-card" data-login>
      <h1 class="shared-centre-title" tabindex="-1">Log in</h1>
      ${state.loginError ? inlineMessage(state.loginError, 'error') : ''}
      ${state.loginNotice ? inlineMessage(state.loginNotice, state.loginNoticeKind) : ''}
      <label class="shared-field"><span class="shared-field-label">Username</span>
        <input class="shared-input" name="username" autocomplete="username" value="${esc(state.username || (demo ? 'ken' : ''))}" required>
      </label>
      <label class="shared-field"><span class="shared-field-label">Password</span>
        <input class="shared-input" name="password" type="password" autocomplete="current-password" required>
      </label>
      <button type="submit" class="shared-button shared-button-primary shared-button-wide">Log in</button>
      ${demo ? '<p class="shared-note">Test mode. Use a demo account.</p>' : ''}
    </form>
  </main>`;
}

export function changePassword({ state }) {
  return `<main class="shared-centre-page">
    <p class="shared-centre-name">LMS Delivery</p>
    <form class="shared-card shared-centre-card shared-centre-card-wide delivery-password-form" data-change-password>
      <h1 class="shared-centre-title" tabindex="-1">Change your password</h1>
      <div class="shared-message-place" data-password-feedback aria-live="polite"></div>
      <p class="shared-note">Session ends in <span data-countdown role="timer" aria-label="Time until session expires">-</span></p>
      <p class="shared-note" id="change-password-rule">Use 8 to 72 characters, including an uppercase letter A–Z, a lowercase letter a–z and a digit. The password must fit within 72 bytes. Some characters use more than one byte.</p>
      <label class="shared-field"><span class="shared-field-label">Current password</span>
        <input class="shared-input" name="currentPassword" type="password" autocomplete="current-password" required>
      </label>
      <label class="shared-field"><span class="shared-field-label">New password</span>
        <input class="shared-input" name="newPassword" type="password" autocomplete="new-password" aria-describedby="change-password-rule" required>
      </label>
      <label class="shared-field"><span class="shared-field-label">Repeat new password</span>
        <input class="shared-input" name="confirmPassword" type="password" autocomplete="new-password" required>
      </label>
      <div class="shared-button-row">
        <a href="#${esc(state.passwordBack)}" class="shared-button" data-action="back-password">Cancel</a>
        <button type="submit" class="shared-button shared-button-primary">Change password</button>
      </div>
      <p class="shared-note">After changing your password, log in again with the new one.</p>
    </form>
  </main>`;
}
