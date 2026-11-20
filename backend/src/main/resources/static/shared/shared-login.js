// Show the login notices and send the sign-in form to the shared login endpoint.

// ===== SECTION 1: PAGE ELEMENTS =====
const sharedLoginUserForm = document.getElementById("sharedLoginUserForm");
const sharedLoginUserUsernameInput = document.getElementById("sharedLoginUserUsernameInput");
const sharedLoginUserPasswordInput = document.getElementById("sharedLoginUserPasswordInput");
const sharedLoginUserSubmitButton = document.getElementById("sharedLoginUserSubmitButton");
const sharedLoginUserMessage = document.getElementById("sharedLoginUserMessage");

// ===== SECTION 2: PAGE STATE =====
const sharedLoginUserNoticeMessageMap = new Map([
  ["IDLE", "You were logged out because you were inactive."],
  ["SESSION_ENDED", "Your session has ended. Please log in again."],
  ["PASSWORD_CHANGED", "Password changed. Please log in with your new password."],
  ["LOGGED_OUT", "You are logged out."],
  ["PASSWORD_NOT_CHANGED", "Your session ended, so your new password was not saved. Please log in again."]
]);

// ===== SECTION 3: START OF PAGE =====
const sharedLoginUserNoticeMessage = sharedLoginUserNoticeMessageMap.get(
  sharedReadPageParameter("sharedLoginUserNotice")
);
if (sharedLoginUserNoticeMessage) {
  sharedShowInlineMessage(sharedLoginUserMessage, "INFORMATION", sharedLoginUserNoticeMessage);
}
sharedLoginUserForm.addEventListener("submit", sharedLoginUser);

// Read the first token as soon as the page opens. A submit can retry if this read fails.
// What it does: Leave the form ready after a startup token read fails.
// Input: None.
// Output: No navigation. The submit request will read the token again if needed.
sharedSendRequest("GET", "/api/shared/session", null, sharedLoginUserSubmitButton).catch(() => {
  // The request helper restores the button after the attempted read.
});

// ===== SECTION 4: sharedLoginUser =====

// What it does: Send the username and exact password and follow the signed-in destination.
// Input: A submit event from the login form.
// Output: A new page on success, or the error above the same form.
async function sharedLoginUser(sharedLoginUserEvent) {
  // Keep the browser on this form and submit at most once at a time.
  sharedLoginUserEvent.preventDefault();
  if (sharedLoginUserSubmitButton.disabled) {
    return;
  }
  const sharedLoginUserRequest = {
    sharedLoginUserUsername: sharedLoginUserUsernameInput.value,
    sharedLoginUserPassword: sharedLoginUserPasswordInput.value
  };
  try {
    const sharedLoginUserResponse = await sharedSendRequest("POST", "/api/shared/login", sharedLoginUserRequest,
      sharedLoginUserSubmitButton);
    sharedLoginUserForm.reset();
    window.location.replace(sharedLoginUserResponse.sharedLoginUserNextPageWebAddress);
  } catch (sharedLoginUserError) {
    // Put the returned message on the same page so the user can try again.
    sharedShowInlineMessage(sharedLoginUserMessage, "ERROR", sharedLoginUserError.message);
  }
}
