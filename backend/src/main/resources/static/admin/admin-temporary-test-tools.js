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
const adminTemporarySetProductStockButton = document.getElementById("adminTemporarySetProductStockButton");
const adminTemporarySetProductStockSKUInput = document.getElementById("adminTemporarySetProductStockSKUInput");
const adminTemporarySetProductStockPhysicalStockInput =
  document.getElementById("adminTemporarySetProductStockPhysicalStockInput");
const adminTemporarySetProductStockReservedStockInput =
  document.getElementById("adminTemporarySetProductStockReservedStockInput");
const adminTemporarySetProductStockResultText = document.getElementById("adminTemporarySetProductStockResultText");
const adminTemporaryCreateSampleOrderSetButton = document.getElementById("adminTemporaryCreateSampleOrderSetButton");
const adminTemporaryCreateSampleOrderSetResultText =
  document.getElementById("adminTemporaryCreateSampleOrderSetResultText");
const adminTemporaryCreateShortageReportOrderStatusSelect =
  document.getElementById("adminTemporaryCreateShortageReportOrderStatusSelect");
const adminTemporaryCreateShortageReportMissingQuantityInput =
  document.getElementById("adminTemporaryCreateShortageReportMissingQuantityInput");
const adminTemporaryCreateShortageReportOrderReferenceInput =
  document.getElementById("adminTemporaryCreateShortageReportOrderReferenceInput");
const adminTemporaryCreateShortageReportButton =
  document.getElementById("adminTemporaryCreateShortageReportButton");
const adminTemporaryCreateShortageReportResultText =
  document.getElementById("adminTemporaryCreateShortageReportResultText");
const adminTemporaryResolveShortageCaseShortageCaseIDInput =
  document.getElementById("adminTemporaryResolveShortageCaseShortageCaseIDInput");
const adminTemporaryResolveShortageCaseButton =
  document.getElementById("adminTemporaryResolveShortageCaseButton");
const adminTemporaryResolveShortageCaseResultText =
  document.getElementById("adminTemporaryResolveShortageCaseResultText");
const adminTemporaryCreatePickingOrderWorkerUsernameInput =
  document.getElementById("adminTemporaryCreatePickingOrderWorkerUsernameInput");
const adminTemporaryCreatePickingOrderButton =
  document.getElementById("adminTemporaryCreatePickingOrderButton");
const adminTemporaryCreatePickingOrderResultText =
  document.getElementById("adminTemporaryCreatePickingOrderResultText");
const adminTemporaryCreateFailedDeliveryCaseFailureCountInput =
  document.getElementById("adminTemporaryCreateFailedDeliveryCaseFailureCountInput");
const adminTemporaryCreateFailedDeliveryCaseIsReturnedCheckbox =
  document.getElementById("adminTemporaryCreateFailedDeliveryCaseIsReturnedCheckbox");
const adminTemporaryCreateFailedDeliveryCaseDaysSinceReturnInput =
  document.getElementById("adminTemporaryCreateFailedDeliveryCaseDaysSinceReturnInput");
const adminTemporaryCreateFailedDeliveryCaseClientRequestSelect =
  document.getElementById("adminTemporaryCreateFailedDeliveryCaseClientRequestSelect");
const adminTemporaryCreateFailedDeliveryCaseButton =
  document.getElementById("adminTemporaryCreateFailedDeliveryCaseButton");
const adminTemporaryCreateFailedDeliveryCaseResultText =
  document.getElementById("adminTemporaryCreateFailedDeliveryCaseResultText");
const adminTemporaryCreateHeldPackageWorkerUsernameInput =
  document.getElementById("adminTemporaryCreateHeldPackageWorkerUsernameInput");
const adminTemporaryCreateHeldPackageOrderStatusSelect =
  document.getElementById("adminTemporaryCreateHeldPackageOrderStatusSelect");
const adminTemporaryCreateHeldPackageButton = document.getElementById("adminTemporaryCreateHeldPackageButton");
const adminTemporaryCreateHeldPackageResultText = document.getElementById("adminTemporaryCreateHeldPackageResultText");
const adminTemporaryCreateRefundCaseRefundStatusSelect =
  document.getElementById("adminTemporaryCreateRefundCaseRefundStatusSelect");
const adminTemporaryCreateRefundCaseAgeHoursInput = document.getElementById("adminTemporaryCreateRefundCaseAgeHoursInput");
const adminTemporaryCreateRefundCaseButton = document.getElementById("adminTemporaryCreateRefundCaseButton");
const adminTemporaryCreateRefundCaseResultText = document.getElementById("adminTemporaryCreateRefundCaseResultText");
const adminTemporarySetProviderAnswerRefundIDInput =
  document.getElementById("adminTemporarySetProviderAnswerRefundIDInput");
const adminTemporarySetProviderAnswerOutcomeSelect =
  document.getElementById("adminTemporarySetProviderAnswerOutcomeSelect");
const adminTemporarySetProviderAnswerButton = document.getElementById("adminTemporarySetProviderAnswerButton");
const adminTemporarySetProviderAnswerResultText = document.getElementById("adminTemporarySetProviderAnswerResultText");
const adminTemporaryRunPrivacyCheckButton = document.getElementById("adminTemporaryRunPrivacyCheckButton");
const adminTemporaryRunPrivacyCheckResultText = document.getElementById("adminTemporaryRunPrivacyCheckResultText");
const adminTemporaryRemoveAllTestDataConfirmTextInput =
  document.getElementById("adminTemporaryRemoveAllTestDataConfirmTextInput");
const adminTemporaryRemoveAllTestDataButton = document.getElementById("adminTemporaryRemoveAllTestDataButton");
const adminTemporaryRemoveAllTestDataResultText = document.getElementById("adminTemporaryRemoveAllTestDataResultText");

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
adminTemporarySetProductStockButton.form.addEventListener("submit", adminTemporarySetProductStock);
adminTemporaryCreateSampleOrderSetButton.addEventListener("click", adminTemporaryCreateSampleOrderSet);
adminTemporaryCreateShortageReportButton.form.addEventListener("submit", adminTemporaryCreateShortageReport);
adminTemporaryResolveShortageCaseButton.form.addEventListener("submit", adminTemporaryResolveShortageCase);
adminTemporaryCreatePickingOrderButton.form.addEventListener("submit", adminTemporaryCreatePickingOrder);
adminTemporaryCreateFailedDeliveryCaseButton.form.addEventListener("submit", adminTemporaryCreateFailedDeliveryCase);
adminTemporaryCreateHeldPackageButton.form.addEventListener("submit", adminTemporaryCreateHeldPackage);
adminTemporaryCreateRefundCaseButton.form.addEventListener("submit", adminTemporaryCreateRefundCase);
adminTemporarySetProviderAnswerButton.form.addEventListener("submit", adminTemporarySetProviderAnswer);
adminTemporaryRunPrivacyCheckButton.addEventListener("click", adminTemporaryRunPrivacyCheck);
adminTemporaryRemoveAllTestDataButton.form.addEventListener("submit", adminTemporaryRemoveAllTestData);

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

// ===== SECTION 14: adminTemporarySetProductStock =====

// TEMPORARY TEST CODE - remove before integration
// Stage 4 checks low stock and shortfall on a product that the user has added.
// What it does: Send the chosen stock numbers and show the saved result or the server's refusal.
// Input: The form submission and the three tool inputs.
// Output: The tool's plain-text result.
async function adminTemporarySetProductStock(adminTemporarySetProductStockEvent) {
  adminTemporarySetProductStockEvent.preventDefault();
  adminTemporarySetProductStockResultText.hidden = true;
  adminTemporarySetProductStockResultText.textContent = "";
  const adminTemporarySetProductStockRequest = {
    adminTemporarySetProductStockSKU: adminTemporarySetProductStockSKUInput.value,
    adminTemporarySetProductStockPhysicalStock: adminTemporarySetProductStockPhysicalStockInput.valueAsNumber,
    adminTemporarySetProductStockReservedStock: adminTemporarySetProductStockReservedStockInput.valueAsNumber
  };
  try {
    const adminTemporarySetProductStockResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/product-stock", adminTemporarySetProductStockRequest,
      adminTemporarySetProductStockButton
    );
    adminTemporarySetProductStockResultText.textContent = adminTemporarySetProductStockResponse.message;
  } catch (adminTemporarySetProductStockError) {
    adminTemporarySetProductStockResultText.textContent = adminTemporarySetProductStockError.code + ": "
      + adminTemporarySetProductStockError.message;
  }
  adminTemporarySetProductStockResultText.hidden = false;
}

// ===== SECTION 15: adminTemporaryCreateSampleOrderSet =====

// TEMPORARY TEST CODE - remove before integration
// Stage 5 needs examples of all ten order statuses.
// What it does: Create one sample order for each status and display its reference.
// Input: None.
// Output: The list of sample references and statuses, or the server's error.
async function adminTemporaryCreateSampleOrderSet() {
  // The shared helper disables the button until the body-free request finishes.
  try {
    const adminTemporaryCreateSampleOrderSetResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/sample-order-set", undefined, adminTemporaryCreateSampleOrderSetButton
    );
    adminTemporaryCreateSampleOrderSetResultText.textContent = adminTemporaryCreateSampleOrderSetResponse.message;
  } catch (adminTemporaryCreateSampleOrderSetError) {
    adminTemporaryCreateSampleOrderSetResultText.textContent = adminTemporaryCreateSampleOrderSetError.code + ": "
      + adminTemporaryCreateSampleOrderSetError.message;
  }
  adminTemporaryCreateSampleOrderSetResultText.hidden = false;
}

// ===== SECTION 16: adminTemporaryCreateShortageReport =====

// TEMPORARY TEST CODE - remove before integration
// What it does: Submit the shortage report fixture for Stage 6.
// Input: The test card's submit event and its current fields.
// Output: The fixture result or rule error in the same card.
async function adminTemporaryCreateShortageReport(adminTemporaryCreateShortageReportEvent) {
  adminTemporaryCreateShortageReportEvent.preventDefault();

  // Send the fixture with the same names as the test tool request record.
  try {
    const adminTemporaryCreateShortageReportResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/shortage-report", {
        adminTemporaryCreateShortageReportOrderStatus: adminTemporaryCreateShortageReportOrderStatusSelect.value,
        adminTemporaryCreateShortageReportMissingQuantity: adminTemporaryCreateShortageReportMissingQuantityInput.valueAsNumber,
        adminTemporaryCreateShortageReportOrderReference: adminTemporaryCreateShortageReportOrderReferenceInput.value
      }, adminTemporaryCreateShortageReportButton);
    adminTemporaryCreateShortageReportResultText.textContent = adminTemporaryCreateShortageReportResponse.message;
  } catch (adminTemporaryCreateShortageReportError) {
    adminTemporaryCreateShortageReportResultText.textContent = adminTemporaryCreateShortageReportError.code + ": "
      + adminTemporaryCreateShortageReportError.message;
  }
  adminTemporaryCreateShortageReportResultText.hidden = false;
}

// ===== SECTION 17: adminTemporaryResolveShortageCase =====

// TEMPORARY TEST CODE - remove before integration
// What it does: Submit the shortage resolved fixture for Stage 6.
// Input: The test card's submit event and its current fields.
// Output: The fixture result or rule error in the same card.
async function adminTemporaryResolveShortageCase(adminTemporaryResolveShortageCaseEvent) {
  adminTemporaryResolveShortageCaseEvent.preventDefault();

  // Send the fixture with the same names as the test tool request record.
  try {
    const adminTemporaryResolveShortageCaseResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/shortage-resolved", {
        adminTemporaryResolveShortageCaseShortageCaseID: adminTemporaryResolveShortageCaseShortageCaseIDInput.valueAsNumber
      }, adminTemporaryResolveShortageCaseButton);
    adminTemporaryResolveShortageCaseResultText.textContent = adminTemporaryResolveShortageCaseResponse.message;
  } catch (adminTemporaryResolveShortageCaseError) {
    adminTemporaryResolveShortageCaseResultText.textContent = adminTemporaryResolveShortageCaseError.code + ": "
      + adminTemporaryResolveShortageCaseError.message;
  }
  adminTemporaryResolveShortageCaseResultText.hidden = false;
}

// ===== SECTION 18: adminTemporaryCreatePickingOrder =====

// TEMPORARY TEST CODE - remove before integration
// What it does: Submit the picking order fixture for Stage 6.
// Input: The test card's submit event and its current fields.
// Output: The fixture result or rule error in the same card.
async function adminTemporaryCreatePickingOrder(adminTemporaryCreatePickingOrderEvent) {
  adminTemporaryCreatePickingOrderEvent.preventDefault();

  // Send the fixture with the same names as the test tool request record.
  try {
    const adminTemporaryCreatePickingOrderResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/picking-order", {
        adminTemporaryCreatePickingOrderWorkerUsername: adminTemporaryCreatePickingOrderWorkerUsernameInput.value
      }, adminTemporaryCreatePickingOrderButton);
    adminTemporaryCreatePickingOrderResultText.textContent = adminTemporaryCreatePickingOrderResponse.message;
  } catch (adminTemporaryCreatePickingOrderError) {
    adminTemporaryCreatePickingOrderResultText.textContent = adminTemporaryCreatePickingOrderError.code + ": "
      + adminTemporaryCreatePickingOrderError.message;
  }
  adminTemporaryCreatePickingOrderResultText.hidden = false;
}

// ===== SECTION 19: adminTemporaryCreateFailedDeliveryCase =====

// TEMPORARY TEST CODE - remove before integration
// Stage 7 needs sample orders with configurable failed-delivery history.
// What it does: Create the failed-delivery case described by the form.
// Input: The submit event and the failure count, return state, return age and request.
// Output: The tool's result message, or its error code and message.
async function adminTemporaryCreateFailedDeliveryCase(adminTemporaryCreateFailedDeliveryCaseEvent) {
  adminTemporaryCreateFailedDeliveryCaseEvent.preventDefault();
  adminTemporaryCreateFailedDeliveryCaseResultText.hidden = true;
  try {
    const adminTemporaryCreateFailedDeliveryCaseResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/failed-delivery-case", {
        adminTemporaryCreateFailedDeliveryCaseFailureCount: adminTemporaryCreateFailedDeliveryCaseFailureCountInput.valueAsNumber,
        adminTemporaryCreateFailedDeliveryCaseIsReturned: adminTemporaryCreateFailedDeliveryCaseIsReturnedCheckbox.checked,
        adminTemporaryCreateFailedDeliveryCaseDaysSinceReturn: adminTemporaryCreateFailedDeliveryCaseDaysSinceReturnInput.valueAsNumber,
        adminTemporaryCreateFailedDeliveryCaseClientRequest: adminTemporaryCreateFailedDeliveryCaseClientRequestSelect.value
      }, adminTemporaryCreateFailedDeliveryCaseButton);
    adminTemporaryCreateFailedDeliveryCaseResultText.textContent = adminTemporaryCreateFailedDeliveryCaseResponse.message;
  } catch (adminTemporaryCreateFailedDeliveryCaseError) {
    adminTemporaryCreateFailedDeliveryCaseResultText.textContent = adminTemporaryCreateFailedDeliveryCaseError.code
      + ": " + adminTemporaryCreateFailedDeliveryCaseError.message;
  }
  adminTemporaryCreateFailedDeliveryCaseResultText.hidden = false;
}

// ===== SECTION 20: adminTemporaryCreateHeldPackage =====

// TEMPORARY TEST CODE - remove before integration
// Stage 7 needs held-package fixtures for reassignment tests.
// What it does: Create a sample order with a worker holding its package.
// Input: The submit event, optional worker username and order status.
// Output: The tool's result message, or its error code and message.
async function adminTemporaryCreateHeldPackage(adminTemporaryCreateHeldPackageEvent) {
  adminTemporaryCreateHeldPackageEvent.preventDefault();
  adminTemporaryCreateHeldPackageResultText.hidden = true;
  try {
    const adminTemporaryCreateHeldPackageResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/held-package", {
        adminTemporaryCreateHeldPackageWorkerUsername: adminTemporaryCreateHeldPackageWorkerUsernameInput.value,
        adminTemporaryCreateHeldPackageOrderStatus: adminTemporaryCreateHeldPackageOrderStatusSelect.value
      }, adminTemporaryCreateHeldPackageButton);
    adminTemporaryCreateHeldPackageResultText.textContent = adminTemporaryCreateHeldPackageResponse.message;
  } catch (adminTemporaryCreateHeldPackageError) {
    adminTemporaryCreateHeldPackageResultText.textContent = adminTemporaryCreateHeldPackageError.code
      + ": " + adminTemporaryCreateHeldPackageError.message;
  }
  adminTemporaryCreateHeldPackageResultText.hidden = false;
}

// ===== SECTION 21: adminTemporaryCreateRefundCase =====

// TEMPORARY TEST CODE - remove before integration
// Stage 8 needs refund attempts of different ages and statuses.
// What it does: Create a cancelled sample order with one refund.
// Input: The submit event, refund status and age in whole hours.
// Output: The order reference, refund and payment numbers, or a rule error in the same card.
async function adminTemporaryCreateRefundCase(adminTemporaryCreateRefundCaseEvent) {
  adminTemporaryCreateRefundCaseEvent.preventDefault();
  adminTemporaryCreateRefundCaseResultText.hidden = true;

  // The request uses the fields of the Stage 8 fixture record.
  try {
    const adminTemporaryCreateRefundCaseResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/refund-case", {
        adminTemporaryCreateRefundCaseRefundStatus: adminTemporaryCreateRefundCaseRefundStatusSelect.value,
        adminTemporaryCreateRefundCaseAgeHours: adminTemporaryCreateRefundCaseAgeHoursInput.valueAsNumber
      }, adminTemporaryCreateRefundCaseButton);
    adminTemporaryCreateRefundCaseResultText.textContent = adminTemporaryCreateRefundCaseResponse.message;
  } catch (adminTemporaryCreateRefundCaseError) {
    adminTemporaryCreateRefundCaseResultText.textContent = adminTemporaryCreateRefundCaseError.code
      + ": " + adminTemporaryCreateRefundCaseError.message;
  }
  adminTemporaryCreateRefundCaseResultText.hidden = false;
}

// ===== SECTION 22: adminTemporarySetProviderAnswer =====

// TEMPORARY TEST CODE - remove before integration
// Stage 8 needs the stand-in provider to reproduce all supported outcomes.
// What it does: Set the provider's answer for one refund number.
// Input: The submit event, refund number and outcome.
// Output: The saved answer message or a rule error in the same card.
async function adminTemporarySetProviderAnswer(adminTemporarySetProviderAnswerEvent) {
  adminTemporarySetProviderAnswerEvent.preventDefault();
  adminTemporarySetProviderAnswerResultText.hidden = true;

  // Setting the answer only changes what a later provider check receives.
  try {
    const adminTemporarySetProviderAnswerResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/provider-answer", {
        adminTemporarySetProviderAnswerRefundID: adminTemporarySetProviderAnswerRefundIDInput.valueAsNumber,
        adminTemporarySetProviderAnswerOutcome: adminTemporarySetProviderAnswerOutcomeSelect.value
      }, adminTemporarySetProviderAnswerButton);
    adminTemporarySetProviderAnswerResultText.textContent = adminTemporarySetProviderAnswerResponse.message;
  } catch (adminTemporarySetProviderAnswerError) {
    adminTemporarySetProviderAnswerResultText.textContent = adminTemporarySetProviderAnswerError.code
      + ": " + adminTemporarySetProviderAnswerError.message;
  }
  adminTemporarySetProviderAnswerResultText.hidden = false;
}

// ===== SECTION 23: adminTemporaryRunPrivacyCheck =====

// TEMPORARY TEST CODE - remove before integration
// What it does: Run the final Admin privacy check using the existing data.
// Input: None.
// Output: The number of answers and findings, or an error in the same card.
async function adminTemporaryRunPrivacyCheck() {
  adminTemporaryRunPrivacyCheckResultText.hidden = true;
  try {
    const adminTemporaryRunPrivacyCheckResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/privacy-check", null, adminTemporaryRunPrivacyCheckButton);
    adminTemporaryRunPrivacyCheckResultText.textContent = adminTemporaryRunPrivacyCheckResponse.message;
  } catch (adminTemporaryRunPrivacyCheckError) {
    adminTemporaryRunPrivacyCheckResultText.textContent = adminTemporaryRunPrivacyCheckError.code
      + ": " + adminTemporaryRunPrivacyCheckError.message;
  }
  adminTemporaryRunPrivacyCheckResultText.hidden = false;
}

// ===== SECTION 24: adminTemporaryRemoveAllTestData =====

// TEMPORARY TEST CODE - remove before integration
// What it does: Ask the server to remove the local test data when the confirmation matches.
// Input: The submit event and the exact text entered by the Admin.
// Output: A success message or a refusal in the same card.
async function adminTemporaryRemoveAllTestData(adminTemporaryRemoveAllTestDataEvent) {
  adminTemporaryRemoveAllTestDataEvent.preventDefault();
  adminTemporaryRemoveAllTestDataResultText.hidden = true;
  try {
    const adminTemporaryRemoveAllTestDataResponse = await sharedSendRequest(
      "POST", "/api/admin/temporary-test/remove-all-test-data", {
        adminTemporaryRemoveAllTestDataConfirmText: adminTemporaryRemoveAllTestDataConfirmTextInput.value
      }, adminTemporaryRemoveAllTestDataButton);
    adminTemporaryRemoveAllTestDataConfirmTextInput.value = "";
    adminTemporaryRemoveAllTestDataResultText.textContent = adminTemporaryRemoveAllTestDataResponse.message;
    void adminViewMenuCounter();
  } catch (adminTemporaryRemoveAllTestDataError) {
    adminTemporaryRemoveAllTestDataResultText.textContent = adminTemporaryRemoveAllTestDataError.code
      + ": " + adminTemporaryRemoveAllTestDataError.message;
  }
  adminTemporaryRemoveAllTestDataResultText.hidden = false;
}
