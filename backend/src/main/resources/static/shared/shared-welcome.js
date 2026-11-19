// Show the signed-in name and role until the other roles supply their own start pages.

// ===== SECTION 1: PAGE ELEMENTS =====
const sharedShowWelcomeUsernameText = document.getElementById("sharedShowWelcomeUsernameText");
const sharedShowWelcomeRoleText = document.getElementById("sharedShowWelcomeRoleText");
const sharedShowWelcomeNoticeMessage = document.getElementById("sharedShowWelcomeNoticeMessage");
const sharedShowWelcomeLogoutButton = document.getElementById("sharedShowWelcomeLogoutButton");

// ===== SECTION 2: PAGE STATE =====
const sharedShowWelcomeRoleNameMap = {
  CLIENT: "Client",
  WAREHOUSE_WORKER: "Warehouse Worker",
  DELIVERY_WORKER: "Delivery Worker",
  ADMIN: "Admin"
};

// ===== SECTION 3: START OF PAGE =====
sharedShowWelcome();

// What it does: End the welcome page's session when the user chooses Log out.
// Input: None. Uses the welcome page's button and inline message.
// Output: A return to login or an inline logout error.
sharedShowWelcomeLogoutButton.addEventListener("click", () => {
  sharedLogoutUser(sharedShowWelcomeLogoutButton, sharedShowWelcomeNoticeMessage);
});

// ===== SECTION 4: sharedShowWelcome =====

// What it does: Read the current session and show its welcome details.
// Input: None.
// Output: The signed-in name, role and any access-denied notice, or navigation to login or password change.
async function sharedShowWelcome() {
  try {
    // Direct requests to a shared page still need the current session to show signed-in details.
    const sharedViewSessionResponse = await sharedSendRequest("GET", "/api/shared/session");
    if (!sharedViewSessionResponse.sharedViewSessionIsLoggedIn) {
      window.location.replace("/shared/shared-login.html");
      return;
    }
    if (sharedViewSessionResponse.mustChangePassword) {
      window.location.replace("/shared/shared-change-password.html");
      return;
    }

    // Copy the name as text and use the approved role label.
    sharedShowWelcomeUsernameText.textContent = "Welcome, " + sharedViewSessionResponse.username;
    sharedShowWelcomeRoleText.textContent = "Role: " + sharedShowWelcomeRoleNameMap[sharedViewSessionResponse.role];
    if (sharedReadPageParameter("sharedShowWelcomeNotice") === "ACCESS_DENIED") {
      sharedShowWelcomeNoticeMessage.textContent = "You are not allowed to open that page.";
      sharedShowWelcomeNoticeMessage.parentElement.hidden = false;
    }
    sharedShowWelcomeUsernameText.closest("main").hidden = false;
  } catch (sharedViewSessionError) {
    // The request helper already directs an expired or revoked session to login.
    if (sharedViewSessionError.status !== 401) {
      window.location.replace("/shared/shared-login.html");
    }
  }
}
