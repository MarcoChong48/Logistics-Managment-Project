// TEMPORARY TEST CODE - remove before integration
// The test-tools page confirms the profile and runs local fixture tools.

// ===== SECTION 1: PAGE ELEMENTS =====
const adminTemporaryViewTestModeText = document.getElementById("adminTemporaryViewTestModeText");
const adminTemporaryCreateTestUserRoleSelect = document.getElementById("adminTemporaryCreateTestUserRoleSelect");
const adminTemporaryCreateTestUserButton = document.getElementById("adminTemporaryCreateTestUserButton");
const adminTemporaryCreateTestUserResultText = document.getElementById("adminTemporaryCreateTestUserResultText");
const adminTemporaryEndUserLockUsernameInput = document.getElementById("adminTemporaryEndUserLockUsernameInput");
const adminTemporaryEndUserLockButton = document.getElementById("adminTemporaryEndUserLockButton");
const adminTemporaryEndUserLockResultText = document.getElementById("adminTemporaryEndUserLockResultText");
const adminTemporaryExpireTemporaryPasswordUsernameInput =
  document.getElementById("adminTemporaryExpireTemporaryPasswordUsernameInput");
const adminTemporaryExpireTemporaryPasswordButton =
  document.getElementById("adminTemporaryExpireTemporaryPasswordButton");
const adminTemporaryExpireTemporaryPasswordResultText =
  document.getElementById("adminTemporaryExpireTemporaryPasswordResultText");
const adminTemporarySetUserStatusUsernameInput = document.getElementById("adminTemporarySetUserStatusUsernameInput");
const adminTemporarySetUserStatusAccountStatusSelect =
  document.getElementById("adminTemporarySetUserStatusAccountStatusSelect");
const adminTemporarySetUserStatusButton = document.getElementById("adminTemporarySetUserStatusButton");
const adminTemporarySetUserStatusResultText = document.getElementById("adminTemporarySetUserStatusResultText");
const adminTemporaryCreateManyTestUserButton = document.getElementById("adminTemporaryCreateManyTestUserButton");
const adminTemporaryCreateManyTestUserResultText =
  document.getElementById("adminTemporaryCreateManyTestUserResultText");
const adminTemporaryCreateWorkerWithActiveWorkButton =
  document.getElementById("adminTemporaryCreateWorkerWithActiveWorkButton");
const adminTemporaryCreateWorkerWithActiveWorkResultText =
  document.getElementById("adminTemporaryCreateWorkerWithActiveWorkResultText");
const adminTemporaryCreatePickingScanRowButton =
  document.getElementById("adminTemporaryCreatePickingScanRowButton");
const adminTemporaryCreatePickingScanRowResultText =
  document.getElementById("adminTemporaryCreatePickingScanRowResultText");
const adminTemporaryCreateOldLogRowButton =
  document.getElementById("adminTemporaryCreateOldLogRowButton");
const adminTemporaryCreateOldLogRowResultText =
  document.getElementById("adminTemporaryCreateOldLogRowResultText");
const adminTemporaryRunLogCleanupButton =
  document.getElementById("adminTemporaryRunLogCleanupButton");
const adminTemporaryRunLogCleanupResultText =
  document.getElementById("adminTemporaryRunLogCleanupResultText");
const adminTemporaryCreatePrivateValueLogRowButton =
  document.getElementById("adminTemporaryCreatePrivateValueLogRowButton");
const adminTemporaryCreatePrivateValueLogRowResultText =
  document.getElementById("adminTemporaryCreatePrivateValueLogRowResultText");

// ===== SECTION 2: PAGE STATE =====
// The frame checks the Admin's session. The tool page also checks the dedicated mode endpoint.

// ===== SECTION 3: START OF PAGE =====

// TEMPORARY TEST CODE - remove before integration
// Stage 0 and Stage 1 only show the Test tools page in test mode.
// What it does: Show the Test tools heading and the current tools only when the server confirms test mode.
// Input: The health answer used to draw the frame, or null if the answer is unavailable.
// Output: The test-mode message, or navigation to Dashboard when test mode is off.
adminBuildPageFrame("Test tools").then(async (adminTemporaryViewTestModeHealthResponse) => {
  // The frame already shows an error if the health answer was unavailable.
  if (adminTemporaryViewTestModeHealthResponse === null) {
    return;
  }

  // A copied Test tools address can still be opened directly after the profile is switched off.
  if (!adminTemporaryViewTestModeHealthResponse.sharedCheckSystemHealthTestModeIsEnabled) {
    window.location.replace("/admin/admin-dashboard.html");
    return;
  }

  // Reveal the heading and mode text when the dedicated route confirms the profile is active.
  try {
    const adminTemporaryViewTestModeResponse = await sharedSendRequest("GET", "/api/admin/temporary-test/mode");
    if (adminTemporaryViewTestModeResponse.adminTemporaryViewTestModeIsEnabled) {
      const adminTemporaryViewTestModeMain = adminTemporaryViewTestModeText.closest("main");
      const adminTemporaryViewTestModeBlockList = adminTemporaryViewTestModeMain.querySelectorAll(":scope > [hidden]");
      for (const adminTemporaryViewTestModeBlock of adminTemporaryViewTestModeBlockList) {
        adminTemporaryViewTestModeBlock.hidden = false;
      }
    }
  } catch (adminTemporaryViewTestModeError) {
    sharedShowMessage("ERROR", adminTemporaryViewTestModeError.message);
  }
});
adminTemporaryCreateTestUserButton.addEventListener("click", adminTemporaryCreateTestUser);
adminTemporaryEndUserLockButton.addEventListener("click", adminTemporaryEndUserLock);
adminTemporaryExpireTemporaryPasswordButton.addEventListener("click", adminTemporaryExpireTemporaryPassword);
adminTemporarySetUserStatusButton.addEventListener("click", adminTemporarySetUserStatus);
adminTemporaryCreateManyTestUserButton.addEventListener("click", adminTemporaryCreateManyTestUser);
adminTemporaryCreateWorkerWithActiveWorkButton.addEventListener("click", adminTemporaryCreateWorkerWithActiveWork);
adminTemporaryCreatePickingScanRowButton.addEventListener("click", adminTemporaryCreatePickingScanRow);
adminTemporaryCreateOldLogRowButton.addEventListener("click", adminTemporaryCreateOldLogRow);
adminTemporaryRunLogCleanupButton.addEventListener("click", adminTemporaryRunLogCleanup);
adminTemporaryCreatePrivateValueLogRowButton.addEventListener("click", adminTemporaryCreatePrivateValueLogRow);

// ===== SECTION 4: adminTemporaryCreateTestUser =====

// TEMPORARY TEST CODE - remove before integration
// Stage 1 uses test users for login and permission tests.
// What it does: Create a test user with the selected role and display the issued password once.
// Input: None. Reads the role from the select field.
// Output: The created username and temporary password, or the error code and message.
async function adminTemporaryCreateTestUser() {
  // Clear the previous result before starting another request.
  adminTemporaryCreateTestUserResultText.hidden = true;
  adminTemporaryCreateTestUserResultText.textContent = "";
  const adminTemporaryCreateTestUserRequest = {
    adminTemporaryCreateTestUserRole: adminTemporaryCreateTestUserRoleSelect.value
  };
  try {
    const adminTemporaryCreateTestUserResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/test-user", adminTemporaryCreateTestUserRequest,
      adminTemporaryCreateTestUserButton
    );
    adminTemporaryCreateTestUserResultText.hidden = false;
    adminTemporaryCreateTestUserResultText.textContent = adminTemporaryCreateTestUserResponse.message;
  } catch (adminTemporaryCreateTestUserError) {
    adminTemporaryCreateTestUserResultText.hidden = false;
    adminTemporaryCreateTestUserResultText.textContent = adminTemporaryCreateTestUserError.code + ": "
      + adminTemporaryCreateTestUserError.message;
  }
}

// ===== SECTION 5: adminTemporaryEndUserLock =====

// TEMPORARY TEST CODE - remove before integration
// Stage 1 exercises automatic unlock without the 15-minute wait.
// What it does: Move a test user's lock time into the past.
// Input: None. Reads the test user's username from the field.
// Output: The server's message, or the error code and message.
async function adminTemporaryEndUserLock() {
  // Send the username used by the login test.
  const adminTemporaryEndUserLockRequest = {
    adminTemporaryEndUserLockUsername: adminTemporaryEndUserLockUsernameInput.value
  };
  try {
    const adminTemporaryEndUserLockResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/end-lock", adminTemporaryEndUserLockRequest,
      adminTemporaryEndUserLockButton
    );
    adminTemporaryEndUserLockResultText.hidden = false;
    adminTemporaryEndUserLockResultText.textContent = adminTemporaryEndUserLockResponse.message;
  } catch (adminTemporaryEndUserLockError) {
    adminTemporaryEndUserLockResultText.hidden = false;
    adminTemporaryEndUserLockResultText.textContent = adminTemporaryEndUserLockError.code + ": "
      + adminTemporaryEndUserLockError.message;
  }
}

// ===== SECTION 6: adminTemporaryExpireTemporaryPassword =====

// TEMPORARY TEST CODE - remove before integration
// Stage 1 exercises denial of expired temporary passwords without waiting a day.
// What it does: Move a test user's temporary-password expiry into the past.
// Input: None. Reads the test user's username from the field.
// Output: The server's message, or the error code and message.
async function adminTemporaryExpireTemporaryPassword() {
  // Send the test account that should have an expired temporary password.
  const adminTemporaryExpireTemporaryPasswordRequest = {
    adminTemporaryExpireTemporaryPasswordUsername: adminTemporaryExpireTemporaryPasswordUsernameInput.value
  };
  try {
    const adminTemporaryExpireTemporaryPasswordResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/expire-temporary-password", adminTemporaryExpireTemporaryPasswordRequest,
      adminTemporaryExpireTemporaryPasswordButton
    );
    adminTemporaryExpireTemporaryPasswordResultText.hidden = false;
    adminTemporaryExpireTemporaryPasswordResultText.textContent = adminTemporaryExpireTemporaryPasswordResponse.message;
  } catch (adminTemporaryExpireTemporaryPasswordError) {
    adminTemporaryExpireTemporaryPasswordResultText.hidden = false;
    adminTemporaryExpireTemporaryPasswordResultText.textContent = adminTemporaryExpireTemporaryPasswordError.code
      + ": " + adminTemporaryExpireTemporaryPasswordError.message;
  }
}

// ===== SECTION 7: adminTemporarySetUserStatus =====

// TEMPORARY TEST CODE - remove before integration
// Stage 1 uses this tool to check that access follows the stored account status.
// What it does: Set a test user's stored account status and report the result.
// Input: None. Reads the username and status controls.
// Output: The server's message, or the error code and message.
async function adminTemporarySetUserStatus() {
  // Leave the input on the page for the following test step.
  const adminTemporarySetUserStatusRequest = {
    adminTemporarySetUserStatusUsername: adminTemporarySetUserStatusUsernameInput.value,
    adminTemporarySetUserStatusAccountStatus: adminTemporarySetUserStatusAccountStatusSelect.value
  };
  try {
    const adminTemporarySetUserStatusResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/user-status", adminTemporarySetUserStatusRequest,
      adminTemporarySetUserStatusButton
    );
    adminTemporarySetUserStatusResultText.hidden = false;
    adminTemporarySetUserStatusResultText.textContent = adminTemporarySetUserStatusResponse.message;
  } catch (adminTemporarySetUserStatusError) {
    adminTemporarySetUserStatusResultText.hidden = false;
    adminTemporarySetUserStatusResultText.textContent = adminTemporarySetUserStatusError.code + ": "
      + adminTemporarySetUserStatusError.message;
  }
}

// ===== SECTION 8: adminTemporaryCreateManyTestUser =====

// TEMPORARY TEST CODE - remove before integration
// Stage 2 uses the numbered Client accounts for search and pagination tests.
// What it does: Create the Client accounts for the Users list.
// Input: None.
// Output: The server's message or the error code and message in the result line.
async function adminTemporaryCreateManyTestUser() {
  // Send the setup request without a body and display the answer as plain text.
  try {
    const adminTemporaryCreateManyTestUserResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/many-test-users", undefined, adminTemporaryCreateManyTestUserButton
    );
    adminTemporaryCreateManyTestUserResultText.textContent = adminTemporaryCreateManyTestUserResponse.message;
  } catch (adminTemporaryCreateManyTestUserError) {
    adminTemporaryCreateManyTestUserResultText.textContent = adminTemporaryCreateManyTestUserError.code + ": "
      + adminTemporaryCreateManyTestUserError.message;
  }
  adminTemporaryCreateManyTestUserResultText.hidden = false;
}

// ===== SECTION 9: adminTemporaryCreateWorkerWithActiveWork =====

// TEMPORARY TEST CODE - remove before integration
// Stage 2 uses this fixture for account-impact warnings.
// What it does: Create the workers and sample orders.
// Input: None.
// Output: The worker credentials and order references, or the error code and message.
async function adminTemporaryCreateWorkerWithActiveWork() {
  // Clear the last fixture's credentials before asking the server for a new one.
  adminTemporaryCreateWorkerWithActiveWorkResultText.textContent = "";
  adminTemporaryCreateWorkerWithActiveWorkResultText.hidden = true;
  try {
    const adminTemporaryCreateWorkerWithActiveWorkResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/worker-with-active-work", undefined,
      adminTemporaryCreateWorkerWithActiveWorkButton
    );
    adminTemporaryCreateWorkerWithActiveWorkResultText.textContent =
      adminTemporaryCreateWorkerWithActiveWorkResponse.message;
  } catch (adminTemporaryCreateWorkerWithActiveWorkError) {
    adminTemporaryCreateWorkerWithActiveWorkResultText.textContent =
      adminTemporaryCreateWorkerWithActiveWorkError.code + ": " + adminTemporaryCreateWorkerWithActiveWorkError.message;
  }
  adminTemporaryCreateWorkerWithActiveWorkResultText.hidden = false;
}

// ===== SECTION 10: adminTemporaryCreatePickingScanRow =====

// TEMPORARY TEST CODE - remove before integration
// Creates a sample order that is being picked, with six scan rows.
// What it does: Create scans for a synthetic picking order.
// Input: None.
// Output: The server's message or its error in the tool's result line.
async function adminTemporaryCreatePickingScanRow() {
  // Send the body-free tool request and display its result as plain text.
  try {
    const adminTemporaryCreatePickingScanRowResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/picking-scan-rows", undefined, adminTemporaryCreatePickingScanRowButton
    );
    adminTemporaryCreatePickingScanRowResultText.textContent = adminTemporaryCreatePickingScanRowResponse.message;
  } catch (adminTemporaryCreatePickingScanRowError) {
    adminTemporaryCreatePickingScanRowResultText.textContent = adminTemporaryCreatePickingScanRowError.code + ": "
      + adminTemporaryCreatePickingScanRowError.message;
  }
  adminTemporaryCreatePickingScanRowResultText.hidden = false;
}

// ===== SECTION 11: adminTemporaryCreateOldLogRow =====

// TEMPORARY TEST CODE - remove before integration
// Adds log rows just older and just newer than the keeping times.
// What it does: Replace the tagged retention fixtures.
// Input: None.
// Output: The server's message or its error in the tool's result line.
async function adminTemporaryCreateOldLogRow() {
  // Send the body-free tool request and display its result as plain text.
  try {
    const adminTemporaryCreateOldLogRowResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/old-log-rows", undefined, adminTemporaryCreateOldLogRowButton
    );
    adminTemporaryCreateOldLogRowResultText.textContent = adminTemporaryCreateOldLogRowResponse.message;
  } catch (adminTemporaryCreateOldLogRowError) {
    adminTemporaryCreateOldLogRowResultText.textContent = adminTemporaryCreateOldLogRowError.code + ": "
      + adminTemporaryCreateOldLogRowError.message;
  }
  adminTemporaryCreateOldLogRowResultText.hidden = false;
}

// ===== SECTION 12: adminTemporaryRunLogCleanup =====

// TEMPORARY TEST CODE - remove before integration
// Runs the daily clean-up of old log rows at once.
// What it does: Run the same cleanup as the daily task.
// Input: None.
// Output: The server's message or its error in the tool's result line.
async function adminTemporaryRunLogCleanup() {
  // Send the body-free tool request and display its result as plain text.
  try {
    const adminTemporaryRunLogCleanupResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/run-log-cleanup", undefined, adminTemporaryRunLogCleanupButton
    );
    adminTemporaryRunLogCleanupResultText.textContent = adminTemporaryRunLogCleanupResponse.message;
  } catch (adminTemporaryRunLogCleanupError) {
    adminTemporaryRunLogCleanupResultText.textContent = adminTemporaryRunLogCleanupError.code + ": "
      + adminTemporaryRunLogCleanupError.message;
  }
  adminTemporaryRunLogCleanupResultText.hidden = false;
}

// ===== SECTION 13: adminTemporaryCreatePrivateValueLogRow =====

// TEMPORARY TEST CODE - remove before integration
// Adds one audit row and one security row that hold private test values.
// What it does: Insert synthetic audit and security values for the privacy checks.
// Input: None.
// Output: The server's message or its error in the tool's result line.
async function adminTemporaryCreatePrivateValueLogRow() {
  // Send the body-free tool request and display its result as plain text.
  try {
    const adminTemporaryCreatePrivateValueLogRowResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/private-value-log-rows", undefined, adminTemporaryCreatePrivateValueLogRowButton
    );
    adminTemporaryCreatePrivateValueLogRowResultText.textContent = adminTemporaryCreatePrivateValueLogRowResponse.message;
  } catch (adminTemporaryCreatePrivateValueLogRowError) {
    adminTemporaryCreatePrivateValueLogRowResultText.textContent = adminTemporaryCreatePrivateValueLogRowError.code + ": "
      + adminTemporaryCreatePrivateValueLogRowError.message;
  }
  adminTemporaryCreatePrivateValueLogRowResultText.hidden = false;
}
