// Open the password change form for a signed-in user and send its three values to the server.

// ===== SECTION 1: PAGE ELEMENTS =====
const sharedChangePasswordForm = document.getElementById("sharedChangePasswordForm");
const sharedChangePasswordCurrentPasswordInput = document.getElementById("sharedChangePasswordCurrentPasswordInput");
const sharedChangePasswordNewPasswordInput = document.getElementById("sharedChangePasswordNewPasswordInput");
const sharedChangePasswordConfirmPasswordInput = document.getElementById("sharedChangePasswordConfirmPasswordInput");
const sharedChangePasswordSubmitButton = document.getElementById("sharedChangePasswordSubmitButton");
const sharedChangePasswordMessage = document.getElementById("sharedChangePasswordMessage");

// ===== SECTION 2: PAGE STATE =====
// The server holds the password rules and decides whether a session may change its password.

// ===== SECTION 3: START OF PAGE =====
sharedChangePasswordForm.addEventListener("submit", sharedChangePassword);

// What it does: Show the form only for a signed-in session.
// Input: The server's session record.
// Output: The visible form, or a return to login.
sharedSendRequest("GET", "/api/shared/session").then((sharedViewSessionResponse) => {
  if (sharedViewSessionResponse.sharedViewSessionIsLoggedIn) {
    sharedChangePasswordForm.closest("main").hidden = false;
  } else {
    window.location.replace("/shared/shared-login.html");
  }
// What it does: Handle a session check that could not be completed.
// Input: The request error.
// Output: A return to login when the request helper has not already sent the session-ended notice.
}).catch((sharedViewSessionError) => {
  if (sharedViewSessionError.status !== 401) {
    window.location.replace("/shared/shared-login.html");
  }
});

// ===== SECTION 4: sharedChangePassword =====

// What it does: Ask the server to change the signed-in user's password.
// Input: The password-change form submit event.
// Output: Login after the password changes, or the failed rule above the first field.
async function sharedChangePassword(sharedChangePasswordEvent) {
  // Send all three values unmodified so the server can apply the exact password rules.
  sharedChangePasswordEvent.preventDefault();
  if (sharedChangePasswordSubmitButton.disabled) {
    return;
  }
  const sharedChangePasswordRequest = {
    sharedChangePasswordCurrentPassword: sharedChangePasswordCurrentPasswordInput.value,
    sharedChangePasswordNewPassword: sharedChangePasswordNewPasswordInput.value,
    sharedChangePasswordConfirmPassword: sharedChangePasswordConfirmPasswordInput.value
  };
  try {
    await sharedSendRequest("POST", "/api/shared/change-password", sharedChangePasswordRequest,
      sharedChangePasswordSubmitButton);
    sharedChangePasswordForm.reset();
    window.location.replace("/shared/shared-login.html?sharedLoginUserNotice=PASSWORD_CHANGED");
  } catch (sharedChangePasswordError) {
    sharedChangePasswordMessage.parentElement.hidden = false;
    sharedChangePasswordMessage.textContent = sharedChangePasswordError.message;
  }
}
