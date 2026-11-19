// Run the live Users list, account forms and confirmation windows.

// ===== SECTION 1: PAGE ELEMENTS =====
const adminSearchUserKeywordInput = document.getElementById("adminSearchUserKeywordInput");
const adminSearchUserRoleSelect = document.getElementById("adminSearchUserRoleSelect");
const adminSearchUserAccountStatusSelect = document.getElementById("adminSearchUserAccountStatusSelect");
const adminSearchUserSubmitButton = document.getElementById("adminSearchUserSubmitButton");
const adminSearchUserClearButton = document.getElementById("adminSearchUserClearButton");
const adminSearchUserResultTable = document.getElementById("adminSearchUserResultTable");
const adminSearchUserResultTableBody = document.getElementById("adminSearchUserResultTableBody");
const adminSearchUserEmptyMessage = document.getElementById("adminSearchUserEmptyMessage");
const adminSearchUserTotalRowCountText = document.getElementById("adminSearchUserTotalRowCountText");
const adminSearchUserPager = document.getElementById("adminSearchUserPager");

const adminCreateUserOpenButton = document.getElementById("adminCreateUserOpenButton");
const adminCreateUserDialog = document.getElementById("adminCreateUserDialog");
const adminCreateUserUsernameInput = document.getElementById("adminCreateUserUsernameInput");
const adminCreateUserRoleSelect = document.getElementById("adminCreateUserRoleSelect");
const adminCreateUserDeliveryProfilePanel = document.getElementById("adminCreateUserDeliveryProfilePanel");
const adminCreateUserEmployeeNumberInput = document.getElementById("adminCreateUserEmployeeNumberInput");
const adminCreateUserVehicleTypeInput = document.getElementById("adminCreateUserVehicleTypeInput");
const adminCreateUserVehiclePlateInput = document.getElementById("adminCreateUserVehiclePlateInput");
const adminCreateUserEquipmentNoteInput = document.getElementById("adminCreateUserEquipmentNoteInput");
const adminCreateUserConfirmButton = document.getElementById("adminCreateUserConfirmButton");

const adminShowTemporaryPasswordDialog = document.getElementById("adminShowTemporaryPasswordDialog");
const adminShowTemporaryPasswordUsernameText = document.getElementById("adminShowTemporaryPasswordUsernameText");
const adminShowTemporaryPasswordValueText = document.getElementById("adminShowTemporaryPasswordValueText");
const adminShowTemporaryPasswordExpiryTimeText = document.getElementById("adminShowTemporaryPasswordExpiryTimeText");
const adminShowTemporaryPasswordCopyButton = document.getElementById("adminShowTemporaryPasswordCopyButton");
const adminShowTemporaryPasswordCopyMessage = document.getElementById("adminShowTemporaryPasswordCopyMessage");
const adminShowTemporaryPasswordCloseButton = document.getElementById("adminShowTemporaryPasswordCloseButton");

const adminViewUserDialog = document.getElementById("adminViewUserDialog");
const adminViewUserDialogTitle = document.getElementById("adminViewUserDialogTitle");
const adminViewUserUsernameText = document.getElementById("adminViewUserUsernameText");
const adminViewUserRoleText = document.getElementById("adminViewUserRoleText");
const adminViewUserAccountStatusBadge = document.getElementById("adminViewUserAccountStatusBadge");
const adminViewUserCreatedTimeText = document.getElementById("adminViewUserCreatedTimeText");
const adminViewUserTemporaryPasswordExpiryTimeText =
  document.getElementById("adminViewUserTemporaryPasswordExpiryTimeText");
const adminEditDeliveryWorkerProfilePanel = document.getElementById("adminEditDeliveryWorkerProfilePanel");
const adminEditDeliveryWorkerProfileEmployeeNumberInput =
  document.getElementById("adminEditDeliveryWorkerProfileEmployeeNumberInput");
const adminEditDeliveryWorkerProfileVehicleTypeInput =
  document.getElementById("adminEditDeliveryWorkerProfileVehicleTypeInput");
const adminEditDeliveryWorkerProfileVehiclePlateInput =
  document.getElementById("adminEditDeliveryWorkerProfileVehiclePlateInput");
const adminEditDeliveryWorkerProfileEquipmentNoteInput =
  document.getElementById("adminEditDeliveryWorkerProfileEquipmentNoteInput");
const adminEditDeliveryWorkerProfileSaveButton = document.getElementById("adminEditDeliveryWorkerProfileSaveButton");

const adminChangeUserRoleDialog = document.getElementById("adminChangeUserRoleDialog");
const adminChangeUserRoleUsernameText = document.getElementById("adminChangeUserRoleUsernameText");
const adminChangeUserRoleCurrentRoleText = document.getElementById("adminChangeUserRoleCurrentRoleText");
const adminChangeUserRoleNewRoleSelect = document.getElementById("adminChangeUserRoleNewRoleSelect");
const adminChangeUserRoleReasonInput = document.getElementById("adminChangeUserRoleReasonInput");
const adminPreviewUserImpactPanel = document.getElementById("adminPreviewUserImpactPanel");
const adminChangeUserRoleConfirmButton = document.getElementById("adminChangeUserRoleConfirmButton");

const adminResetPasswordDialog = document.getElementById("adminResetPasswordDialog");
const adminResetPasswordUsernameText = document.getElementById("adminResetPasswordUsernameText");
const adminResetPasswordIdentityIsConfirmedCheckbox =
  document.getElementById("adminResetPasswordIdentityIsConfirmedCheckbox");
const adminResetPasswordIdentityCheckMethodInput =
  document.getElementById("adminResetPasswordIdentityCheckMethodInput");
const adminResetPasswordConfirmButton = document.getElementById("adminResetPasswordConfirmButton");

// ===== SECTION 2: PAGE STATE =====
let adminSearchUserPageNumber = 1;
let adminSearchUserFilterList = [];
let adminSearchUserRequestCount = 0;
let adminSearchUserIsActionPending = false;
let adminEditDeliveryWorkerProfileUserID;
let adminChangeUserRoleUserID;
let adminResetPasswordUserID;

// ===== SECTION 3: START OF PAGE =====

// What it does: Restore the list after the shared frame authorizes the page.
// Input: None. Reads the current page address.
// Output: The matching filters, table and pager are loaded.
adminBuildPageFrame("Users").then(() => {
  // The frame keeps the work area hidden when the user is being redirected.
  if (document.querySelector("main").hidden) {
    return;
  }
  adminSearchUserKeywordInput.value = sharedReadPageParameter("adminSearchUserKeyword");
  adminSearchUserRoleSelect.value = sharedReadPageParameter("adminSearchUserRole");
  adminSearchUserAccountStatusSelect.value = sharedReadPageParameter("adminSearchUserAccountStatus");
  sharedWatchTableScroll();
  adminSearchUserApplyFilters();
  const adminSearchUserRestoredPageNumber = Number(sharedReadPageParameter("adminSearchUserPageNumber"));
  adminSearchUser(Number.isSafeInteger(adminSearchUserRestoredPageNumber) && adminSearchUserRestoredPageNumber > 0
    ? adminSearchUserRestoredPageNumber : 1);
});

// What it does: Apply the current filter bar and return to the first page.
// Input: The search form's submit event.
// Output: A live search with no page reload.
adminSearchUserSubmitButton.form.addEventListener("submit", (adminSearchUserSubmitEvent) => {
  adminSearchUserSubmitEvent.preventDefault();
  adminSearchUserApplyFilters();
  adminSearchUser(1);
});
adminSearchUserClearButton.addEventListener("click", adminSearchUserClearFilters);
adminSearchUserEmptyMessage.querySelector("button").addEventListener("click", adminSearchUserClearFilters);

adminCreateUserOpenButton.addEventListener("click", adminCreateUserOpen);
adminCreateUserRoleSelect.addEventListener("change", adminCreateUserShowDeliveryProfile);
adminCreateUserConfirmButton.addEventListener("click", adminCreateUser);
adminEditDeliveryWorkerProfileSaveButton.addEventListener("click", adminEditDeliveryWorkerProfile);
adminChangeUserRoleConfirmButton.addEventListener("click", adminChangeUserRole);
adminResetPasswordConfirmButton.addEventListener("click", adminResetPassword);
adminChangeUserRoleDialog.addEventListener("input", adminSearchUserCheckDialogInput);
adminResetPasswordDialog.addEventListener("input", adminSearchUserCheckDialogInput);

// What it does: Close each form from its own Cancel or Close control.
// Input: The form's closing button click.
// Output: The browser closes the modal and restores focus to its opener.
for (const adminSearchUserDialog of [
  adminCreateUserDialog, adminViewUserDialog, adminChangeUserRoleDialog, adminResetPasswordDialog
]) {
  adminSearchUserDialog.querySelector("[data-close-dialog]").addEventListener("click", () => {
    sharedCloseDialog(adminSearchUserDialog);
  });
}

// What it does: Attempt to copy the one-time password.
// Input: The clipboard button's click.
// Output: Feedback appears inside this opening when the latest write finishes.
adminShowTemporaryPasswordCopyButton.addEventListener("click", () => {
  sharedCopyText(adminShowTemporaryPasswordCopyButton);
});

// What it does: Clear the one-time value immediately when Close is pressed.
// Input: None. Uses the password window and its display elements.
// Output: The value is removed and the window closes.
adminShowTemporaryPasswordCloseButton.addEventListener("click", () => {
  adminShowTemporaryPasswordClear();
  sharedCloseDialog(adminShowTemporaryPasswordDialog);
});
adminShowTemporaryPasswordDialog.addEventListener("cancel", adminShowTemporaryPasswordClear);
window.addEventListener("pagehide", adminShowTemporaryPasswordClear);

// What it does: Cover close routes other than our Close button and Escape.
// Input: The dialog's native close event.
// Output: Any value left in a closed window is removed.
adminShowTemporaryPasswordDialog.addEventListener("close", () => {
  if (!adminShowTemporaryPasswordDialog.open) {
    adminShowTemporaryPasswordClear();
  }
});

// ===== SECTION 4: adminSearchUser =====

// What it does: Search for matching users and draw the latest requested page.
// Input: The requested page number. Uses the applied filters.
// Output: An updated table, count, pager and address, or an error message.
async function adminSearchUser(adminSearchUserRequestedPageNumber) {
  // Use a request number so a slower previous search cannot replace a newer result.
  const adminSearchUserCurrentRequestNumber = ++adminSearchUserRequestCount;
  const adminSearchUserParameterList = new URLSearchParams(adminSearchUserFilterList);
  adminSearchUserParameterList.set("adminSearchUserPageNumber", adminSearchUserRequestedPageNumber);
  for (const [adminSearchUserParameterName, adminSearchUserParameterValue] of adminSearchUserFilterList) {
    if (adminSearchUserParameterValue === "") {
      adminSearchUserParameterList.delete(adminSearchUserParameterName);
    }
  }

  try {
    const adminSearchUserResponse = await sharedSendRequest("GET", "/api/admin/users?" + adminSearchUserParameterList);
    if (adminSearchUserCurrentRequestNumber !== adminSearchUserRequestCount) {
      return;
    }
    adminSearchUserPageNumber = adminSearchUserResponse.adminSearchUserPageNumber;
    sharedWritePageParameter([...adminSearchUserFilterList, ["adminSearchUserPageNumber", adminSearchUserPageNumber]]);
    adminSearchUserRenderRows(adminSearchUserResponse);
  } catch (adminSearchUserError) {
    if (adminSearchUserCurrentRequestNumber === adminSearchUserRequestCount) {
      sharedShowMessage("ERROR", adminSearchUserError.message);
    }
  }
}

// ===== SECTION 5: adminViewUser =====

// What it does: Load the current profile and show the selected user's account facts.
// Input: The selected search row, which supplies last login.
// Output: A detail window with editable profile fields for a Delivery Worker.
async function adminViewUser(adminViewUserRow) {
  // Keep detail fields current by reading the user again when its window is opened.
  const adminViewUserResponse = await sharedSendRequest("GET", "/api/admin/users/" + adminViewUserRow.userID);
  adminEditDeliveryWorkerProfileUserID = adminViewUserResponse.userID;
  adminViewUserDialogTitle.textContent = "User " + adminViewUserResponse.username;
  adminViewUserUsernameText.textContent = adminViewUserResponse.username;
  adminViewUserRoleText.textContent = sharedFormatStatusWord(adminViewUserResponse.role);
  const adminViewUserBadge = adminSearchUserBuildStatusBadge(adminViewUserResponse.accountStatus,
    adminViewUserResponse.adminViewUserIsLockedNow, adminViewUserResponse.lockUntil);
  adminViewUserAccountStatusBadge.className = adminViewUserBadge.className;
  adminViewUserAccountStatusBadge.textContent = adminViewUserBadge.textContent;

  // Last login is available in the search result. Show dates using the browser's local time.
  adminViewUserCreatedTimeText.textContent = sharedFormatTime(adminViewUserResponse.createdTime);
  const adminViewUserLastLoginText = adminViewUserCreatedTimeText.parentElement.nextElementSibling.lastElementChild;
  const adminViewUserFailedLoginCountText =
    adminViewUserLastLoginText.parentElement.nextElementSibling.lastElementChild;
  adminViewUserLastLoginText.textContent = sharedFormatTime(adminViewUserRow.adminSearchUserLastLoginTime);
  adminViewUserFailedLoginCountText.textContent = adminViewUserResponse.failedLoginCount + " of 5";
  adminViewUserTemporaryPasswordExpiryTimeText.textContent = "Not in use";
  if (adminViewUserResponse.mustChangePassword) {
    adminViewUserTemporaryPasswordExpiryTimeText.textContent = sharedFormatTime(
      adminViewUserResponse.temporaryPasswordExpiryTime
    );
  }

  // Populate profile fields only for the role that owns them.
  const adminViewUserHasDeliveryProfile = adminViewUserResponse.role === "DELIVERY_WORKER";
  adminEditDeliveryWorkerProfilePanel.hidden = !adminViewUserHasDeliveryProfile;
  adminEditDeliveryWorkerProfileSaveButton.hidden = !adminViewUserHasDeliveryProfile;
  adminEditDeliveryWorkerProfileEmployeeNumberInput.value = adminViewUserResponse.employeeNumber ?? "";
  adminEditDeliveryWorkerProfileVehicleTypeInput.value = adminViewUserResponse.vehicleType ?? "";
  adminEditDeliveryWorkerProfileVehiclePlateInput.value = adminViewUserResponse.vehiclePlate ?? "";
  adminEditDeliveryWorkerProfileEquipmentNoteInput.value = adminViewUserResponse.equipmentNote ?? "";
  sharedOpenDialog(adminViewUserDialog);
}

// ===== SECTION 6: adminCreateUser =====

// What it does: Start a fresh Create user form.
// Input: None.
// Output: The default role and blank fields are visible inside a new opening.
function adminCreateUserOpen() {
  // Keep a pending write from being displaced by a new form.
  if (adminSearchUserIsActionPending) {
    return;
  }
  adminCreateUserUsernameInput.value = "";
  adminCreateUserRoleSelect.value = "CLIENT";
  adminCreateUserEmployeeNumberInput.value = "";
  adminCreateUserVehicleTypeInput.value = "";
  adminCreateUserVehiclePlateInput.value = "";
  adminCreateUserEquipmentNoteInput.value = "";
  adminCreateUserShowDeliveryProfile();
  sharedOpenDialog(adminCreateUserDialog);
}

// What it does: Show the optional profile fields only for a Delivery Worker.
// Input: None. Reads the form's role select.
// Output: The Delivery Worker fields are either visible or hidden.
function adminCreateUserShowDeliveryProfile() {
  adminCreateUserDeliveryProfilePanel.hidden = adminCreateUserRoleSelect.value !== "DELIVERY_WORKER";
}

// What it does: Create the requested account and show its one-time password.
// Input: None. Reads the Create user window.
// Output: A one-time password on success or inline validation on refusal.
function adminCreateUser() {
  // Include profile values only for Delivery Worker accounts.
  const adminCreateUserRequest = {
    adminCreateUserUsername: adminCreateUserUsernameInput.value,
    adminCreateUserRole: adminCreateUserRoleSelect.value
  };
  if (adminCreateUserRequest.adminCreateUserRole === "DELIVERY_WORKER") {
    adminCreateUserRequest.adminCreateUserEmployeeNumber = adminCreateUserEmployeeNumberInput.value;
    adminCreateUserRequest.adminCreateUserVehicleType = adminCreateUserVehicleTypeInput.value;
    adminCreateUserRequest.adminCreateUserVehiclePlate = adminCreateUserVehiclePlateInput.value;
    adminCreateUserRequest.adminCreateUserEquipmentNote = adminCreateUserEquipmentNoteInput.value;
  }

  // What it does: Hand only the new password and expiry to the one-time display.
  // Input: The new account's create response.
  // Output: The temporary password window opens.
  return adminSearchUserSubmitDialog("POST", "/api/admin/users", adminCreateUserRequest, adminCreateUserConfirmButton,
    (adminCreateUserResponse) => {
      adminShowTemporaryPassword(adminCreateUserResponse.username,
        adminCreateUserResponse.adminCreateUserTemporaryPassword,
        adminCreateUserResponse.adminCreateUserTemporaryPasswordExpiryTime);
    });
}

// ===== SECTION 7: adminEditDeliveryWorkerProfile =====

// What it does: Save the selected Delivery Worker's optional profile fields.
// Input: None. Reads the current View user window.
// Output: Refreshes the list on success or shows the error in the open window.
function adminEditDeliveryWorkerProfile() {
  const adminEditDeliveryWorkerProfileRequest = {
    adminEditDeliveryWorkerProfileEmployeeNumber: adminEditDeliveryWorkerProfileEmployeeNumberInput.value,
    adminEditDeliveryWorkerProfileVehicleType: adminEditDeliveryWorkerProfileVehicleTypeInput.value,
    adminEditDeliveryWorkerProfileVehiclePlate: adminEditDeliveryWorkerProfileVehiclePlateInput.value,
    adminEditDeliveryWorkerProfileEquipmentNote: adminEditDeliveryWorkerProfileEquipmentNoteInput.value
  };
  const adminEditDeliveryWorkerProfileWebAddress = "/api/admin/users/" + adminEditDeliveryWorkerProfileUserID
    + "/delivery-worker-profile";
  return adminSearchUserSubmitDialog("PUT", adminEditDeliveryWorkerProfileWebAddress,
    adminEditDeliveryWorkerProfileRequest, adminEditDeliveryWorkerProfileSaveButton);
}

// ===== SECTION 8: adminPreviewUserImpact =====

// What it does: Format a preview of a user's work as the explanatory text in an action window.
// Input: The user's current impact response, including example order references.
// Output: Plain sentences about the work, or a note that there is no active work.
function adminBuildUserImpactText(adminBuildUserImpactResponse) {
  // Include the counts that affect a worker and the separate count for a client.
  const adminBuildUserImpactTextList = [];
  const adminBuildUserImpactPickingTaskCount =
    adminBuildUserImpactResponse.adminPreviewUserImpactActivePickingTaskCount;
  const adminBuildUserImpactPickupListCount = adminBuildUserImpactResponse.adminPreviewUserImpactOpenPickupListCount;
  const adminBuildUserImpactPackageCount = adminBuildUserImpactResponse.adminPreviewUserImpactHeldPackageCount;
  const adminBuildUserImpactClientOrderCount = adminBuildUserImpactResponse.adminPreviewUserImpactOpenClientOrderCount;
  if (adminBuildUserImpactPickingTaskCount > 0) {
    adminBuildUserImpactTextList.push(adminBuildUserImpactCountText(adminBuildUserImpactPickingTaskCount,
      "active picking task", adminBuildUserImpactResponse.adminPreviewUserImpactActivePickingOrderReferenceList));
  }
  if (adminBuildUserImpactPickupListCount > 0) {
    adminBuildUserImpactTextList.push(adminBuildUserImpactCountText(adminBuildUserImpactPickupListCount,
      "open pickup list"));
  }
  if (adminBuildUserImpactPackageCount > 0) {
    adminBuildUserImpactTextList.push(adminBuildUserImpactCountText(adminBuildUserImpactPackageCount,
      "held package", adminBuildUserImpactResponse.adminPreviewUserImpactHeldPackageOrderReferenceList));
  }
  if (adminBuildUserImpactClientOrderCount > 0) {
    const adminBuildUserImpactOrderText =
      adminBuildUserImpactCountText(adminBuildUserImpactClientOrderCount, "open order");
    adminBuildUserImpactTextList.push("This client has " + adminBuildUserImpactOrderText + ".");
  }
  if (adminBuildUserImpactTextList.length === 0) {
    return "This user has no active work.";
  }
  return adminBuildUserImpactTextList.join(" ");
}

// ===== SECTION 9: adminChangeUserRole =====

// What it does: Show the current role and active work before asking for a change.
// Input: The selected staff user's search row.
// Output: The role window with only the two other staff roles and a required reason.
async function adminChangeUserRoleOpen(adminChangeUserRoleRow) {
  const adminChangeUserRoleImpact = await sharedSendRequest("GET", "/api/admin/users/" + adminChangeUserRoleRow.userID
    + "/impact");
  adminChangeUserRoleUserID = adminChangeUserRoleRow.userID;
  adminChangeUserRoleUsernameText.textContent = adminChangeUserRoleRow.username;
  adminChangeUserRoleCurrentRoleText.textContent = sharedFormatStatusWord(adminChangeUserRoleRow.role);
  adminChangeUserRoleNewRoleSelect.replaceChildren();
  for (const adminChangeUserRoleChoice of ["WAREHOUSE_WORKER", "DELIVERY_WORKER", "ADMIN"]) {
    if (adminChangeUserRoleChoice !== adminChangeUserRoleRow.role) {
      adminChangeUserRoleNewRoleSelect.add(new Option(sharedFormatStatusWord(adminChangeUserRoleChoice),
        adminChangeUserRoleChoice));
    }
  }
  adminChangeUserRoleReasonInput.value = "";
  adminChangeUserRoleConfirmButton.disabled = true;
  adminPreviewUserImpactPanel.textContent = adminBuildUserImpactText(adminChangeUserRoleImpact);
  if (!adminChangeUserRoleImpact.adminPreviewUserImpactHasActiveWork) {
    adminPreviewUserImpactPanel.textContent += " After the change the user must log in again.";
  }
  sharedOpenDialog(adminChangeUserRoleDialog);
}

// What it does: Submit the selected staff role change and its reason.
// Input: None. Reads the Change role window.
// Output: A refreshed account row or the refusal inside the window.
function adminChangeUserRole() {
  const adminChangeUserRoleRequest = {
    adminChangeUserRoleNewRole: adminChangeUserRoleNewRoleSelect.value,
    adminChangeUserRoleReason: adminChangeUserRoleReasonInput.value
  };
  return adminSearchUserSubmitDialog("POST", "/api/admin/users/" + adminChangeUserRoleUserID + "/role",
    adminChangeUserRoleRequest, adminChangeUserRoleConfirmButton);
}

// ===== SECTION 10: BAN, UNBAN, DEACTIVATE, REACTIVATE AND UNLOCK =====

// What it does: Explain an account action, collect its confirmation and apply it.
// Input: The selected row, action's API path, wording, danger state and optional reason field name.
// Output: The new status is loaded and the server message shown, or no request on cancellation.
async function adminSearchUserConfirmAction(adminSearchUserActionRow, adminSearchUserActionPath,
  adminSearchUserActionLabel, adminSearchUserActionExplanation, adminSearchUserActionIsDangerous,
  adminSearchUserActionReasonName) {
  // Explain any accounts or work affected by actions that switch an account off.
  const adminSearchUserActionUserWebAddress = "/api/admin/users/" + adminSearchUserActionRow.userID;
  let adminSearchUserActionText = adminSearchUserActionExplanation;
  if (adminSearchUserActionIsDangerous) {
    const adminSearchUserActionImpact = await sharedSendRequest("GET", adminSearchUserActionUserWebAddress + "/impact");
    adminSearchUserActionText += "\n" + adminBuildUserImpactText(adminSearchUserActionImpact);
  }

  // Unlock is a confirmation without a reason field.
  const adminSearchUserActionReason = await adminOpenReasonDialog(
    adminSearchUserActionLabel + " " + adminSearchUserActionRow.username + "?",
    adminSearchUserActionText,
    adminSearchUserActionReasonName ? "REQUIRED_REASON" : "NONE",
    adminSearchUserActionLabel + " user",
    "Cancel",
    adminSearchUserActionIsDangerous
  );
  if (adminSearchUserActionReason === null) {
    return;
  }

  // Send only one change after the confirmation window's button closes it.
  const adminSearchUserActionRequest = adminSearchUserActionReasonName
    ? { [adminSearchUserActionReasonName]: adminSearchUserActionReason } : null;
  const adminSearchUserActionResponse = await sharedSendRequest("POST",
    adminSearchUserActionUserWebAddress + "/" + adminSearchUserActionPath, adminSearchUserActionRequest);
  sharedShowMessage("SUCCESS", adminSearchUserActionResponse.message);
  await adminSearchUser(adminSearchUserPageNumber);
}

// ===== SECTION 11: adminResetPassword =====

// What it does: Ask the Admin to confirm the user's identity before resetting a password.
// Input: The user whose Actions menu was opened.
// Output: A new reset window with its confirmation disabled until both fields are valid.
function adminResetPasswordOpen(adminResetPasswordRow) {
  adminResetPasswordUserID = adminResetPasswordRow.userID;
  adminResetPasswordUsernameText.textContent = adminResetPasswordRow.username;
  adminResetPasswordIdentityIsConfirmedCheckbox.checked = false;
  adminResetPasswordIdentityCheckMethodInput.value = "";
  adminResetPasswordConfirmButton.disabled = true;
  sharedOpenDialog(adminResetPasswordDialog);
}

// What it does: Request a new temporary password for the selected user.
// Input: None. Reads the identity confirmation and check method from the open window.
// Output: A one-time password on success or inline validation on refusal.
function adminResetPassword() {
  const adminResetPasswordRequest = {
    adminResetPasswordIdentityIsConfirmed: adminResetPasswordIdentityIsConfirmedCheckbox.checked,
    adminResetPasswordIdentityCheckMethod: adminResetPasswordIdentityCheckMethodInput.value
  };

  // What it does: Hand the issued password and expiry to the one-time display.
  // Input: The successful reset response.
  // Output: The temporary password window opens.
  return adminSearchUserSubmitDialog("POST", "/api/admin/users/" + adminResetPasswordUserID + "/reset-password",
    adminResetPasswordRequest, adminResetPasswordConfirmButton, (adminResetPasswordResponse) => {
      adminShowTemporaryPassword(adminResetPasswordResponse.username,
        adminResetPasswordResponse.adminResetPasswordTemporaryPassword,
        adminResetPasswordResponse.adminResetPasswordTemporaryPasswordExpiryTime);
    });
}

// ===== SECTION 12: adminShowTemporaryPassword =====

// What it does: Display a newly issued password for one opening only.
// Input: The username, temporary password and expiry from the create or reset response.
// Output: The information window contains the one-time value until it closes.
function adminShowTemporaryPassword(adminShowTemporaryPasswordUsername, adminShowTemporaryPasswordValue,
  adminShowTemporaryPasswordExpiryTime) {
  adminShowTemporaryPasswordUsernameText.textContent = adminShowTemporaryPasswordUsername;
  adminShowTemporaryPasswordValueText.textContent = adminShowTemporaryPasswordValue;
  adminShowTemporaryPasswordExpiryTimeText.dateTime = adminShowTemporaryPasswordExpiryTime;
  adminShowTemporaryPasswordExpiryTimeText.textContent = sharedFormatTime(adminShowTemporaryPasswordExpiryTime);
  sharedOpenDialog(adminShowTemporaryPasswordDialog);
}

// What it does: Remove the displayed one-time value and its copy feedback.
// Input: None. Uses the window's value and copy message elements.
// Output: The closed window no longer contains a password.
function adminShowTemporaryPasswordClear() {
  adminShowTemporaryPasswordValueText.replaceChildren();
  adminShowTemporaryPasswordCopyMessage.replaceChildren();
}

// ===== SECTION 13: HELPER FUNCTIONS =====

// What it does: Remember the filters the Admin applied.
// Input: None. Reads the three search controls.
// Output: A stable set of parameter values for searching, paging and refreshing.
function adminSearchUserApplyFilters() {
  adminSearchUserFilterList = [
    ["adminSearchUserKeyword", adminSearchUserKeywordInput.value.trim()],
    ["adminSearchUserRole", adminSearchUserRoleSelect.value],
    ["adminSearchUserAccountStatus", adminSearchUserAccountStatusSelect.value]
  ];
}

// What it does: Restore the unfiltered account list.
// Input: None.
// Output: Empty search controls and the first page of all accounts.
function adminSearchUserClearFilters() {
  adminSearchUserKeywordInput.value = "";
  adminSearchUserRoleSelect.value = "";
  adminSearchUserAccountStatusSelect.value = "";
  adminSearchUserApplyFilters();
  adminSearchUser(1);
}

// What it does: Replace the Users table rows and refresh the pager.
// Input: A successful account search response.
// Output: The appropriate results or empty state, with a live account count.
function adminSearchUserRenderRows(adminSearchUserResponse) {
  // Remember a row's action trigger so updating the table does not drop keyboard focus.
  const adminSearchUserFocusedControl = document.activeElement;
  const adminSearchUserRows = adminSearchUserResponse.adminSearchUserRowList;
  const adminSearchUserTotalRowCount = adminSearchUserResponse.adminSearchUserTotalRowCount;
  adminSearchUserTotalRowCountText.textContent = adminSearchUserTotalRowCount
    + (adminSearchUserTotalRowCount === 1 ? " account" : " accounts");
  adminSearchUserResultTable.parentElement.hidden = adminSearchUserRows.length === 0;
  adminSearchUserEmptyMessage.hidden = adminSearchUserRows.length > 0;
  adminSearchUserEmptyMessage.querySelector("button").hidden = !adminSearchUserFilterList
    .some(([, adminSearchUserFilterValue]) => adminSearchUserFilterValue !== "");
  adminSearchUserResultTableBody.replaceChildren(...adminSearchUserRows.map(adminSearchUserBuildRow));
  sharedBuildPager(adminSearchUserPager, adminSearchUserPageNumber,
    adminSearchUserResponse.adminSearchUserTotalPageCount, adminSearchUser);
  adminSearchUserPager.hidden = false;

  // Restore a refreshed row's trigger, or the search field when the row is no longer in the list.
  if (adminSearchUserFocusedControl.id.startsWith("adminBuildActionMenuButton-")) {
    const adminSearchUserReplacementControl = document.getElementById(adminSearchUserFocusedControl.id);
    (adminSearchUserReplacementControl ?? adminSearchUserKeywordInput).focus({ preventScroll: true });
  }
}

// What it does: Build one account row from server data.
// Input: The account's search row.
// Output: A row with its current status and permitted account actions.
function adminSearchUserBuildRow(adminSearchUserRow) {
  // The row name is also the accessible name of the Actions button.
  const adminSearchUserTableRow = document.createElement("tr");
  const adminSearchUserUsernameText = document.createElement("th");
  adminSearchUserUsernameText.scope = "row";
  adminSearchUserUsernameText.textContent = adminSearchUserRow.username;
  if (adminSearchUserRow.adminSearchUserIsOwnAccount) {
    const adminSearchUserOwnAccountText = document.createElement("span");
    adminSearchUserOwnAccountText.className = "shared-muted";
    adminSearchUserOwnAccountText.textContent = " (you)";
    adminSearchUserUsernameText.append(adminSearchUserOwnAccountText);
  }
  const adminSearchUserRoleText = document.createElement("td");
  adminSearchUserRoleText.textContent = sharedFormatStatusWord(adminSearchUserRow.role);

  // Keep the status, failed tries and temporary-password flag on one line.
  const adminSearchUserStatusCell = document.createElement("td");
  const adminSearchUserStatusLine = document.createElement("span");
  adminSearchUserStatusLine.className = "shared-cell-line";
  adminSearchUserStatusLine.append(adminSearchUserBuildStatusBadge(adminSearchUserRow.accountStatus,
    adminSearchUserRow.adminSearchUserIsLockedNow, adminSearchUserRow.lockUntil));
  if (adminSearchUserRow.failedLoginCount > 0) {
    const adminSearchUserFailedLoginCountText = document.createElement("span");
    adminSearchUserFailedLoginCountText.className = "shared-cell-note";
    adminSearchUserFailedLoginCountText.textContent = adminSearchUserRow.failedLoginCount + " of 5 tries";
    adminSearchUserStatusLine.append(adminSearchUserFailedLoginCountText);
  }
  if (adminSearchUserRow.mustChangePassword) {
    adminSearchUserStatusLine.append(adminBuildFlag("Temporary password", "closed"));
  }
  adminSearchUserStatusCell.append(adminSearchUserStatusLine);

  // Show login time as local time and omit all actions on the signed-in Admin's row.
  const adminSearchUserLastLoginTimeText = document.createElement("td");
  adminSearchUserLastLoginTimeText.className = "shared-table-time";
  adminSearchUserLastLoginTimeText.textContent = sharedFormatTime(adminSearchUserRow.adminSearchUserLastLoginTime);
  const adminSearchUserActionsCell = document.createElement("td");
  adminSearchUserActionsCell.className = "shared-table-actions";
  if (adminSearchUserRow.adminSearchUserIsOwnAccount) {
    const adminSearchUserOwnAccountText = document.createElement("span");
    adminSearchUserOwnAccountText.className = "shared-muted";
    adminSearchUserOwnAccountText.textContent = "Your own account";
    adminSearchUserActionsCell.append(adminSearchUserOwnAccountText);
  } else {
    adminSearchUserActionsCell.append(adminBuildActionMenu(adminSearchUserRow.userID, adminSearchUserRow.username,
      adminSearchUserBuildActionList(adminSearchUserRow)));
  }
  adminSearchUserTableRow.append(adminSearchUserUsernameText, adminSearchUserRoleText,
    adminSearchUserStatusCell, adminSearchUserLastLoginTimeText, adminSearchUserActionsCell);
  return adminSearchUserTableRow;
}

// What it does: Apply the approved account-specific status labels and colour families.
// Input: The stored account status, the timed lock flag and the current lock deadline.
// Output: A status badge for the list or detail window.
function adminSearchUserBuildStatusBadge(adminSearchUserAccountStatus, adminSearchUserIsLockedNow,
  adminSearchUserLockUntil) {
  // The stored Banned and Inactive states determine whether those accounts can log in.
  if (adminSearchUserAccountStatus === "INACTIVE") {
    return adminBuildStatusBadge("Deactivated");
  }
  const adminSearchUserRemainingLockMinutes = Math.ceil((Date.parse(adminSearchUserLockUntil) - Date.now()) / 60000);
  const adminSearchUserHasTimedLock = adminSearchUserAccountStatus === "ACTIVE" && adminSearchUserIsLockedNow
    && adminSearchUserRemainingLockMinutes > 0;
  const adminSearchUserBadge = adminBuildStatusBadge(
    adminSearchUserHasTimedLock ? "LOCKED" : adminSearchUserAccountStatus
  );
  if (adminSearchUserAccountStatus === "LOCKED") {
    adminSearchUserBadge.textContent = "Locked (old status)";
  } else if (adminSearchUserHasTimedLock) {
    adminSearchUserBadge.textContent = "Locked, " + adminSearchUserRemainingLockMinutes + " min left";
  }
  return adminSearchUserBadge;
}

// What it does: Choose the valid actions for a user from the stored state, role and timed lock.
// Input: The user's search row.
// Output: The action entries for its popover.
function adminSearchUserBuildActionList(adminSearchUserRow) {
  // What it does: Attach an ordinary row action to the selected account.
  // Input: The action's label, name and callback.
  // Output: An entry with the expected ID and its action bound to this row.
  const adminSearchUserAddAction = (adminSearchUserActionLabel, adminSearchUserActionName, adminSearchUserAction) => {
    return adminSearchUserBuildAction(adminSearchUserRow, adminSearchUserActionLabel,
      adminSearchUserActionName, false, adminSearchUserAction);
  };
  const adminSearchUserActionList = [adminSearchUserAddAction("View", "adminViewUser", adminViewUser)];

  // Active staff can change between staff roles. All active accounts can have a password reset.
  if (adminSearchUserRow.accountStatus === "ACTIVE") {
    if (adminSearchUserRow.role !== "CLIENT") {
      adminSearchUserActionList.push(adminSearchUserAddAction("Change role", "adminChangeUserRole",
        adminChangeUserRoleOpen));
    }
    adminSearchUserActionList.push(adminSearchUserAddAction("Reset password", "adminResetPassword",
      adminResetPasswordOpen));
    if (adminSearchUserRow.adminSearchUserIsLockedNow) {
      adminSearchUserActionList.push(adminSearchUserBuildConfirmAction(adminSearchUserRow, "Unlock", "adminUnlockUser",
        "unlock", "The lock is removed and the count of wrong passwords is set back to 0."
        + " The user can log in at once."));
    }
    adminSearchUserActionList.push(adminSearchUserBuildConfirmAction(adminSearchUserRow, "Ban", "adminBanUser",
      "ban", "The user is logged out at once and cannot log in again until an Admin lifts the ban.",
      "adminBanUserReason", true));
  }

  // Only recovery actions remain available for accounts whose stored status is switched off.
  if (adminSearchUserRow.accountStatus === "BANNED") {
    adminSearchUserActionList.push(adminSearchUserBuildConfirmAction(adminSearchUserRow, "Unban", "adminUnbanUser",
      "unban", "The user can log in again. The count of wrong passwords is set back to 0.", "adminUnbanUserReason"));
  }
  if (["ACTIVE", "BANNED"].includes(adminSearchUserRow.accountStatus)) {
    adminSearchUserActionList.push(adminSearchUserBuildConfirmAction(adminSearchUserRow, "Deactivate",
      "adminDeactivateUser", "deactivate", "The account is switched off and the user is logged out at once."
      + " The account and its history are kept. An Admin can reactivate it later.", "adminDeactivateUserReason", true));
  }
  if (adminSearchUserRow.accountStatus === "INACTIVE") {
    adminSearchUserActionList.push(adminSearchUserBuildConfirmAction(adminSearchUserRow, "Reactivate",
      "adminReactivateUser", "reactivate", "The account is switched on again and the user can log in.",
      "adminReactivateUserReason"));
  }
  if (adminSearchUserRow.accountStatus === "LOCKED") {
    adminSearchUserActionList.push(adminSearchUserBuildConfirmAction(adminSearchUserRow, "Unlock", "adminUnlockUser",
      "unlock", "The lock is removed and the count of wrong passwords is set back to 0. The user can log in at once."));
  }
  return adminSearchUserActionList;
}

// What it does: Make a menu entry for an account confirmation.
// Input: The row, labels, API path, explanation and optional reason key and danger state.
// Output: An entry that opens the approved confirm window for this account.
function adminSearchUserBuildConfirmAction(adminSearchUserRow, adminSearchUserActionLabel, adminSearchUserActionName,
  adminSearchUserActionPath, adminSearchUserActionExplanation, adminSearchUserActionReasonName,
  adminSearchUserActionIsDangerous = false) {
  // What it does: Run the confirmation for the chosen row.
  // Input: The row referred to by this menu entry.
  // Output: A promise for the confirmation and account update.
  return adminSearchUserBuildAction(adminSearchUserRow, adminSearchUserActionLabel, adminSearchUserActionName,
    adminSearchUserActionIsDangerous, (adminSearchUserActionRow) => {
      return adminSearchUserConfirmAction(adminSearchUserActionRow, adminSearchUserActionPath,
        adminSearchUserActionLabel, adminSearchUserActionExplanation, adminSearchUserActionIsDangerous,
        adminSearchUserActionReasonName);
    });
}

// What it does: Bind an account action to a menu entry.
// Input: The row, label, plan function name, danger state and function to call.
// Output: An action descriptor for adminBuildActionMenu.
function adminSearchUserBuildAction(adminSearchUserRow, adminSearchUserActionLabel, adminSearchUserActionName,
  adminSearchUserActionIsDangerous, adminSearchUserAction) {
  return {
    adminBuildActionMenuEntryText: adminSearchUserActionLabel,
    adminBuildActionMenuEntryID: adminSearchUserActionName + "OpenButton-" + adminSearchUserRow.userID,
    adminBuildActionMenuEntryIsDangerous: adminSearchUserActionIsDangerous,

    // What it does: Prevent overlapping row actions while a window is loading or confirming.
    // Input: None. Uses the clicked row and action.
    // Output: The requested window or action result, or a visible error outside the window.
    async adminBuildActionMenuEntryAction() {
      if (adminSearchUserIsActionPending) {
        return;
      }
      adminSearchUserIsActionPending = true;
      try {
        await adminSearchUserAction(adminSearchUserRow);
      } catch (adminSearchUserActionError) {
        sharedShowMessage("ERROR", adminSearchUserActionError.message);
      } finally {
        adminSearchUserIsActionPending = false;
      }
    }
  };
}

// What it does: Update the permission to confirm a role change or password reset.
// Input: The input event from either window.
// Output: Confirm is disabled until all required entries are present and no write is running.
function adminSearchUserCheckDialogInput(adminSearchUserInputEvent) {
  const adminSearchUserDialog = adminSearchUserInputEvent.currentTarget;
  const adminSearchUserReasonInput = adminSearchUserDialog.querySelector("[data-reason]");
  const adminSearchUserRequiredCheckbox = adminSearchUserDialog.querySelector("[data-required-tick]");
  const adminSearchUserHasReason = Array.from(adminSearchUserReasonInput.value.trim()).length >= 5;
  const adminSearchUserHasRequiredTick = !adminSearchUserRequiredCheckbox || adminSearchUserRequiredCheckbox.checked;
  adminSearchUserDialog.querySelector("[data-confirm]").disabled = adminSearchUserIsActionPending
    || !adminSearchUserHasReason || !adminSearchUserHasRequiredTick;
}

// What it does: Submit one write from a Users form and report the result in the right place.
// Input: Method, address, body, confirmation button and optional success callback.
// Output: A refreshed list and success feedback, or an error inside the still-open form.
async function adminSearchUserSubmitDialog(adminSearchUserSubmitMethod, adminSearchUserSubmitWebAddress,
  adminSearchUserSubmitBody, adminSearchUserSubmitControl, adminSearchUserSubmitShowSuccess) {
  if (adminSearchUserIsActionPending || adminSearchUserSubmitControl.disabled) {
    return;
  }
  const adminSearchUserSubmitDialogElement = adminSearchUserSubmitControl.closest("dialog");
  const adminSearchUserSubmitMessage = adminSearchUserSubmitDialogElement.querySelector(".shared-message-place");
  adminSearchUserSubmitMessage.replaceChildren();
  adminSearchUserIsActionPending = true;
  try {
    // A one-time value takes the place of the form. Other successes use the corner message.
    const adminSearchUserSubmitResponse = await sharedSendRequest(adminSearchUserSubmitMethod,
      adminSearchUserSubmitWebAddress, adminSearchUserSubmitBody, adminSearchUserSubmitControl);
    sharedCloseDialog(adminSearchUserSubmitDialogElement);
    if (adminSearchUserSubmitShowSuccess) {
      adminSearchUserSubmitShowSuccess(adminSearchUserSubmitResponse);
    } else {
      sharedShowMessage("SUCCESS", adminSearchUserSubmitResponse.message);
    }
    await adminSearchUser(adminSearchUserPageNumber);
  } catch (adminSearchUserSubmitError) {
    // Keep declined field values visible so the Admin can correct a form and send it again.
    if (adminSearchUserSubmitDialogElement.open) {
      adminSearchUserSubmitMessage.replaceChildren(
        sharedBuildMessage("shared-inline-message shared-inline-message-error", "ERROR", adminSearchUserSubmitError.message)
      );
    } else {
      sharedShowMessage("ERROR", adminSearchUserSubmitError.message);
    }
  } finally {
    adminSearchUserIsActionPending = false;
    if (adminSearchUserSubmitDialogElement.open && adminSearchUserSubmitDialogElement.querySelector("[data-reason]")) {
      adminSearchUserCheckDialogInput({ currentTarget: adminSearchUserSubmitDialogElement });
    }
  }
}

// What it does: Add plural wording and any order references to one impact count.
// Input: The count, singular label and optional order reference list.
// Output: Plain text such as "2 active picking tasks: TEST-0003, TEST-0004".
function adminBuildUserImpactCountText(adminBuildUserImpactCount, adminBuildUserImpactLabel,
  adminBuildUserImpactReferenceList = []) {
  let adminBuildUserImpactCountTextValue = adminBuildUserImpactCount + " " + adminBuildUserImpactLabel;
  if (adminBuildUserImpactCount !== 1) {
    adminBuildUserImpactCountTextValue += "s";
  }
  if (adminBuildUserImpactReferenceList.length > 0) {
    adminBuildUserImpactCountTextValue += ": " + adminBuildUserImpactReferenceList.join(", ");
  }
  return adminBuildUserImpactCountTextValue;
}
