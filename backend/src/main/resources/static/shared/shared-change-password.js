// Show live password feedback and ask the server to change the signed-in user's password.

// ===== SECTION 1: PAGE ELEMENTS =====
const sharedChangePasswordForm = document.getElementById("sharedChangePasswordForm");
const sharedChangePasswordTitleText = document.getElementById("sharedChangePasswordTitleText");
const sharedChangePasswordNoticePanel = document.getElementById("sharedChangePasswordNoticePanel");
const sharedChangePasswordNoticeText = document.getElementById("sharedChangePasswordNoticeText");
const sharedChangePasswordUsernameInput = document.getElementById("sharedChangePasswordUsernameInput");
const sharedChangePasswordCurrentPasswordInput = document.getElementById("sharedChangePasswordCurrentPasswordInput");
const sharedChangePasswordCurrentPasswordLabelText =
  document.getElementById("sharedChangePasswordCurrentPasswordLabelText");
const sharedChangePasswordCurrentPasswordHelpText =
  document.getElementById("sharedChangePasswordCurrentPasswordHelpText");
const sharedChangePasswordCurrentPasswordErrorText =
  document.getElementById("sharedChangePasswordCurrentPasswordErrorText");
const sharedTogglePasswordVisibilityCurrentPasswordButton =
  document.getElementById("sharedTogglePasswordVisibilityCurrentPasswordButton");
const sharedChangePasswordNewPasswordInput = document.getElementById("sharedChangePasswordNewPasswordInput");
const sharedChangePasswordNewPasswordErrorText = document.getElementById("sharedChangePasswordNewPasswordErrorText");
const sharedTogglePasswordVisibilityNewPasswordButton =
  document.getElementById("sharedTogglePasswordVisibilityNewPasswordButton");
const sharedMeasurePasswordStrengthPanel = document.getElementById("sharedMeasurePasswordStrengthPanel");
const sharedMeasurePasswordStrengthText = document.getElementById("sharedMeasurePasswordStrengthText");
const sharedChangePasswordRuleLengthText = document.getElementById("sharedChangePasswordRuleLengthText");
const sharedChangePasswordRuleCapitalText = document.getElementById("sharedChangePasswordRuleCapitalText");
const sharedChangePasswordRuleSmallText = document.getElementById("sharedChangePasswordRuleSmallText");
const sharedChangePasswordRuleDigitText = document.getElementById("sharedChangePasswordRuleDigitText");
const sharedChangePasswordConfirmPasswordInput = document.getElementById("sharedChangePasswordConfirmPasswordInput");
const sharedChangePasswordConfirmPasswordErrorText =
  document.getElementById("sharedChangePasswordConfirmPasswordErrorText");
const sharedTogglePasswordVisibilityConfirmPasswordButton =
  document.getElementById("sharedTogglePasswordVisibilityConfirmPasswordButton");
const sharedChangePasswordMatchText = document.getElementById("sharedChangePasswordMatchText");
const sharedChangePasswordSubmitButton = document.getElementById("sharedChangePasswordSubmitButton");
const sharedChangePasswordMessage = document.getElementById("sharedChangePasswordMessage");
const sharedChangePasswordLogoutButton = document.getElementById("sharedChangePasswordLogoutButton");
const sharedChangePasswordCancelLink = document.getElementById("sharedChangePasswordCancelLink");

// ===== SECTION 2: PAGE STATE =====
const sharedChangePasswordInputList = [
  sharedChangePasswordCurrentPasswordInput,
  sharedChangePasswordNewPasswordInput,
  sharedChangePasswordConfirmPasswordInput
];
const sharedChangePasswordFieldErrorMap = new Map([
  ["SHARED_CURRENT_PASSWORD_WRONG", sharedChangePasswordCurrentPasswordInput],
  ["SHARED_PASSWORD_RULE_FAILED", sharedChangePasswordNewPasswordInput],
  ["SHARED_CONFIRM_PASSWORD_DIFFERENT", sharedChangePasswordConfirmPasswordInput]
]);
const sharedChangePasswordServerMessageMap = new Map();
const sharedMeasurePasswordStrengthPresentationMap = new Map([
  ["NOT_RATED", ["", "Strength: not rated yet"]],
  ["TOO_WEAK", ["shared-strength-too-weak", "Strength: too weak"]],
  ["FAIR", ["shared-strength-fair", "Strength: fair"]],
  ["GOOD", ["shared-strength-good", "Strength: good"]],
  ["STRONG", ["shared-strength-strong", "Strength: strong"]]
]);
let sharedChangePasswordMustChangePassword = false;
let sharedChangePasswordHasPressed = false;
let sharedMeasurePasswordStrengthLevel = "NOT_RATED";

// ===== SECTION 3: START OF PAGE =====
sharedChangePasswordForm.addEventListener("submit", sharedChangePassword);

// What it does: Drop the server's old answer for the field being edited and refresh the local feedback.
// Input: An input event from one of the password fields.
// Output: Updated rule marks and field errors.
sharedChangePasswordForm.addEventListener("input", (sharedShowPasswordFeedbackEvent) => {
  sharedChangePasswordServerMessageMap.delete(sharedShowPasswordFeedbackEvent.target);
  sharedShowPasswordFeedback();
});

for (const sharedTogglePasswordVisibilityButton of [
  sharedTogglePasswordVisibilityCurrentPasswordButton,
  sharedTogglePasswordVisibilityNewPasswordButton,
  sharedTogglePasswordVisibilityConfirmPasswordButton
]) {
  // What it does: Show or hide just the password next to this button.
  // Input: None. Uses the button that owns this click.
  // Output: The current visibility and matching action label.
  sharedTogglePasswordVisibilityButton.addEventListener("click", () => {
    sharedTogglePasswordVisibility(sharedTogglePasswordVisibilityButton);
  });
}

// What it does: Let a person with a temporary password end their session from this page.
// Input: None. Uses the logout button and the active message place.
// Output: Login after logout, or an inline error here.
sharedChangePasswordLogoutButton.addEventListener("click", () => {
  sharedLogoutUser(sharedChangePasswordLogoutButton, sharedChangePasswordMessage);
});

// What it does: Configure the form from the signed-in session.
// Input: The session record from F1.3.
// Output: A form ready for the correct flow, or a return to login.
sharedSendRequest("GET", "/api/shared/session").then((sharedViewSessionResponse) => {
  // A direct visit still requires sign-in before the form can send anything.
  if (!sharedViewSessionResponse.sharedViewSessionIsLoggedIn) {
    window.location.replace("/shared/shared-login.html");
    return;
  }

  // The password manager uses the same account name in both flows.
  sharedChangePasswordUsernameInput.value = sharedViewSessionResponse.username;
  sharedChangePasswordMustChangePassword = sharedViewSessionResponse.mustChangePassword;
  if (sharedChangePasswordMustChangePassword) {
    document.title = "Choose your own password - LMS";
    sharedChangePasswordTitleText.textContent = "Choose your own password";
    sharedChangePasswordNoticeText.textContent = "You logged in as " + sharedViewSessionResponse.username
      + " with a temporary password from an Admin. Choose your own password to continue.";
    sharedChangePasswordNoticePanel.hidden = false;
    sharedChangePasswordCurrentPasswordLabelText.textContent = "Temporary password";
    sharedChangePasswordCurrentPasswordHelpText.textContent = "Type it exactly as you received it, with the hyphens.";
    sharedChangePasswordCurrentPasswordHelpText.hidden = false;
    sharedTogglePasswordVisibilityCurrentPasswordButton.setAttribute("aria-label",
      sharedTogglePasswordVisibilityCurrentPasswordButton.textContent + " temporary password");
    sharedChangePasswordLogoutButton.hidden = false;
  } else {
    sharedChangePasswordCancelLink.href = sharedViewSessionResponse.sharedViewSessionNextPageWebAddress;
    sharedChangePasswordCancelLink.hidden = false;
  }
  sharedChangePasswordSubmitButton.disabled = false;

  // Some password managers fill inputs before the session answer returns.
  sharedShowPasswordFeedback();
// What it does: Handle a session check that could not be completed.
// Input: The request error.
// Output: A return to login if the request helper has not already sent a session-ended notice.
}).catch((sharedViewSessionError) => {
  if (sharedViewSessionError.status !== 401) {
    window.location.replace("/shared/shared-login.html");
  }
});

// ===== SECTION 4: sharedChangePassword =====

// What it does: Send all three exact passwords and show the server's answer.
// Input: A submit event from the password-change form.
// Output: Login after success or session expiry, or an error on this form.
async function sharedChangePassword(sharedChangePasswordEvent) {
  sharedChangePasswordEvent.preventDefault();
  if (sharedChangePasswordSubmitButton.disabled) {
    return;
  }

  // Every press begins a new attempt with fresh feedback.
  sharedChangePasswordHasPressed = true;
  sharedChangePasswordServerMessageMap.clear();
  sharedChangePasswordMessage.replaceChildren();
  sharedShowPasswordFeedback();
  if (sharedChangePasswordInputList.some((sharedChangePasswordInput) => sharedChangePasswordInput.value === "")) {
    sharedChangePasswordForm.querySelector('[aria-invalid="true"]')?.focus();
    return;
  }

  // The local checks advise. Only the server can refuse filled fields.
  const sharedChangePasswordRequest = {
    sharedChangePasswordCurrentPassword: sharedChangePasswordCurrentPasswordInput.value,
    sharedChangePasswordNewPassword: sharedChangePasswordNewPasswordInput.value,
    sharedChangePasswordConfirmPassword: sharedChangePasswordConfirmPasswordInput.value
  };
  sharedChangePasswordSubmitButton.textContent = "Changing password";
  try {
    await sharedSendRequest("POST", "/api/shared/change-password", sharedChangePasswordRequest,
      sharedChangePasswordSubmitButton);
    sharedChangePasswordForm.reset();
    window.location.replace("/shared/shared-login.html?sharedLoginUserNotice=PASSWORD_CHANGED");
  } catch (sharedChangePasswordError) {
    // The request helper already handles the two failures confirmed by the session check.
    if (sharedChangePasswordError.status === 401
      && ["SHARED_NOT_LOGGED_IN", "SHARED_SESSION_ENDED"].includes(sharedChangePasswordError.code)) {
      return;
    }

    // Preserve a server field error until the person edits that field or presses again.
    const sharedChangePasswordErrorInput = sharedChangePasswordFieldErrorMap.get(sharedChangePasswordError.code);
    if (sharedChangePasswordError.status === 400 && sharedChangePasswordErrorInput) {
      sharedChangePasswordServerMessageMap.set(sharedChangePasswordErrorInput, sharedChangePasswordError.message);
      sharedShowPasswordFeedback();
      sharedChangePasswordErrorInput.focus();
      return;
    }

    // Only confirmed validation and security-token refusals are known to have made no change.
    let sharedChangePasswordErrorWords = sharedChangePasswordError.message;
    if (sharedChangePasswordError.status === 403
      && sharedChangePasswordError.code === "SHARED_SECURITY_TOKEN_INVALID") {
      sharedChangePasswordErrorWords =
        "This page is out of date. Reload it and try again. Your password was not changed.";
    } else if (sharedChangePasswordError.status !== 400) {
      if (sharedChangePasswordError.status === 0) {
        sharedChangePasswordErrorWords = "The server did not answer.";
      }
      sharedChangePasswordErrorWords += " It is not known whether your password was changed. Log out, then log in"
        + " with your new password; if it is refused, use your old password.";
    }
    sharedShowInlineMessage(sharedChangePasswordMessage, "ERROR", sharedChangePasswordErrorWords);
  } finally {
    sharedChangePasswordSubmitButton.textContent = "Change password";
  }
}

// ===== SECTION 5: sharedShowPasswordFeedback =====

// What it does: Draw rule and match marks, field errors and advisory strength.
// Input: None. Reads the three fields and the answers for the current form.
// Output: Visible feedback and accessible field states that reflect what the person has entered.
function sharedShowPasswordFeedback() {
  const sharedShowPasswordFeedbackNewPassword = sharedChangePasswordNewPasswordInput.value;
  const sharedShowPasswordFeedbackCurrentPassword = sharedChangePasswordCurrentPasswordInput.value;
  const sharedShowPasswordFeedbackRule = sharedCheckPasswordRuleList(sharedShowPasswordFeedbackNewPassword,
    sharedShowPasswordFeedbackCurrentPassword);
  const sharedShowPasswordFeedbackIsOverCharacterLimit = sharedShowPasswordFeedbackRule
    .sharedCheckPasswordRuleListCharacterCount > 72;
  const sharedShowPasswordFeedbackIsOverByteLimit = sharedShowPasswordFeedbackRule
    .sharedCheckPasswordRuleListIsOverByteLimit;

  // A too-long password turns the length rule red at once. Other rules wait for the first press.
  const sharedShowPasswordFeedbackRuleList = [
    [sharedChangePasswordRuleLengthText, sharedShowPasswordFeedbackRule.sharedCheckPasswordRuleListIsLengthMet,
      sharedShowPasswordFeedbackIsOverCharacterLimit || sharedShowPasswordFeedbackIsOverByteLimit],
    [sharedChangePasswordRuleCapitalText, sharedShowPasswordFeedbackRule.sharedCheckPasswordRuleListHasCapital, false],
    [sharedChangePasswordRuleSmallText, sharedShowPasswordFeedbackRule.sharedCheckPasswordRuleListHasSmall, false],
    [sharedChangePasswordRuleDigitText, sharedShowPasswordFeedbackRule.sharedCheckPasswordRuleListHasDigit, false]
  ];
  let sharedShowPasswordFeedbackHasUnmetRule = false;
  for (const [sharedShowPasswordFeedbackLine, sharedShowPasswordFeedbackIsMet, sharedShowPasswordFeedbackFailsNow]
    of sharedShowPasswordFeedbackRuleList) {
    const sharedShowPasswordFeedbackIsUnmet = !sharedShowPasswordFeedbackIsMet
      && (sharedChangePasswordHasPressed || sharedShowPasswordFeedbackFailsNow);
    sharedShowPasswordRuleState(sharedShowPasswordFeedbackLine, sharedShowPasswordFeedbackIsMet,
      sharedShowPasswordFeedbackIsUnmet);
    sharedShowPasswordFeedbackHasUnmetRule ||= sharedShowPasswordFeedbackIsUnmet;
  }

  // Show immediate errors for a multi-byte overflow or for reusing the current password.
  let sharedShowPasswordFeedbackNewWords = "";
  if (sharedShowPasswordFeedbackIsOverByteLimit && !sharedShowPasswordFeedbackIsOverCharacterLimit) {
    sharedShowPasswordFeedbackNewWords =
      "Too long: some of these characters count as more than one. Use fewer characters.";
  } else if (sharedShowPasswordFeedbackRule.sharedCheckPasswordRuleListIsSameAsCurrent) {
    sharedShowPasswordFeedbackNewWords = "The new password must be different from the current one.";
  } else if (sharedChangePasswordHasPressed && sharedShowPasswordFeedbackHasUnmetRule) {
    sharedShowPasswordFeedbackNewWords = "Meet every rule marked in red.";
  }
  sharedShowFieldError(sharedChangePasswordNewPasswordInput, sharedChangePasswordNewPasswordErrorText,
    sharedChangePasswordServerMessageMap.get(sharedChangePasswordNewPasswordInput)
      ?? sharedShowPasswordFeedbackNewWords,
    sharedShowPasswordFeedbackHasUnmetRule);

  // Empty password fields do not count as a match. The current field only needs to be filled.
  const sharedShowPasswordFeedbackIsMatch = sharedShowPasswordFeedbackNewPassword !== ""
    && sharedShowPasswordFeedbackNewPassword === sharedChangePasswordConfirmPasswordInput.value;
  const sharedShowPasswordFeedbackIsMatchUnmet = sharedChangePasswordHasPressed && !sharedShowPasswordFeedbackIsMatch;
  sharedShowPasswordRuleState(sharedChangePasswordMatchText.firstElementChild, sharedShowPasswordFeedbackIsMatch,
    sharedShowPasswordFeedbackIsMatchUnmet);
  sharedShowFieldError(sharedChangePasswordConfirmPasswordInput, sharedChangePasswordConfirmPasswordErrorText,
    sharedChangePasswordServerMessageMap.get(sharedChangePasswordConfirmPasswordInput) ?? "",
    sharedShowPasswordFeedbackIsMatchUnmet);

  let sharedShowPasswordFeedbackCurrentWords = "";
  if (sharedChangePasswordHasPressed && sharedShowPasswordFeedbackCurrentPassword === "") {
    sharedShowPasswordFeedbackCurrentWords = sharedChangePasswordMustChangePassword
      ? "Type your temporary password." : "Type your current password.";
  }
  sharedShowFieldError(sharedChangePasswordCurrentPasswordInput, sharedChangePasswordCurrentPasswordErrorText,
    sharedChangePasswordServerMessageMap.get(sharedChangePasswordCurrentPasswordInput)
      ?? sharedShowPasswordFeedbackCurrentWords);

  // Change the live strength word only when the level changes.
  const sharedShowPasswordFeedbackStrength = sharedMeasurePasswordStrength(sharedShowPasswordFeedbackNewPassword);
  if (sharedShowPasswordFeedbackStrength !== sharedMeasurePasswordStrengthLevel) {
    const [sharedShowPasswordFeedbackPreviousClass] = sharedMeasurePasswordStrengthPresentationMap.get(
      sharedMeasurePasswordStrengthLevel);
    const [sharedShowPasswordFeedbackStrengthClass, sharedShowPasswordFeedbackStrengthWords] =
      sharedMeasurePasswordStrengthPresentationMap.get(sharedShowPasswordFeedbackStrength);
    if (sharedShowPasswordFeedbackPreviousClass) {
      sharedMeasurePasswordStrengthPanel.classList.remove(sharedShowPasswordFeedbackPreviousClass);
    }
    if (sharedShowPasswordFeedbackStrengthClass) {
      sharedMeasurePasswordStrengthPanel.classList.add(sharedShowPasswordFeedbackStrengthClass);
    }
    sharedMeasurePasswordStrengthText.textContent = sharedShowPasswordFeedbackStrengthWords;
    sharedMeasurePasswordStrengthLevel = sharedShowPasswordFeedbackStrength;
  }
}

// ===== SECTION 6: HELPER FUNCTIONS =====

// What it does: Draw the approved rule mark and its accessible state words.
// Input: A rule line and flags for a rule that is met or visibly unmet.
// Output: Updated mark, colour and text for screen readers.
function sharedShowPasswordRuleState(sharedShowPasswordRuleStateLine, sharedShowPasswordRuleStateIsMet,
  sharedShowPasswordRuleStateIsUnmet) {
  sharedShowPasswordRuleStateLine.classList.toggle("shared-rule-met", sharedShowPasswordRuleStateIsMet);
  sharedShowPasswordRuleStateLine.classList.toggle("shared-rule-unmet", sharedShowPasswordRuleStateIsUnmet);
  let sharedShowPasswordRuleStateWords = "Still needed: ";
  if (sharedShowPasswordRuleStateIsMet) {
    sharedShowPasswordRuleStateWords = "Done: ";
  } else if (sharedShowPasswordRuleStateIsUnmet) {
    sharedShowPasswordRuleStateWords = "Not met: ";
  }
  sharedShowPasswordRuleStateLine.querySelector(".shared-visually-hidden").textContent =
    sharedShowPasswordRuleStateWords;
}
