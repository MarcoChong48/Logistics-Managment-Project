// Show the live system health in the dashboard card.

// ===== SECTION 1: PAGE ELEMENTS =====
const sharedCheckSystemHealthDatabaseText = document.getElementById("sharedCheckSystemHealthDatabaseText");
const sharedCheckSystemHealthTableCountText = document.getElementById("sharedCheckSystemHealthTableCountText");
const sharedCheckSystemHealthServerTimeText = document.getElementById("sharedCheckSystemHealthServerTimeText");

// ===== SECTION 2: PAGE STATE =====
// The initial health answer supplies both the frame flag and the dashboard facts.

// ===== SECTION 3: START OF PAGE =====

// What it does: Show the health facts once the frame reads them from the server.
// Input: The health answer used to draw the frame, or null if the answer is unavailable.
// Output: The connection badge, table count and local server time on the page.
adminBuildPageFrame("Dashboard").then((sharedCheckSystemHealthResponse) => {
  // Leave the facts unknown when the web request could not read the server's state.
  if (sharedCheckSystemHealthResponse === null) {
    return;
  }

  // Use the same health record for a connected and a disconnected database.
  const sharedCheckSystemHealthStatus = sharedCheckSystemHealthResponse.sharedCheckSystemHealthDatabaseIsConnected
    ? "CONNECTED"
    : "NOT_CONNECTED";
  sharedCheckSystemHealthDatabaseText.replaceChildren(adminBuildStatusBadge(sharedCheckSystemHealthStatus));
  sharedCheckSystemHealthTableCountText.textContent = sharedCheckSystemHealthResponse.sharedCheckSystemHealthTableCount;

  // Keep the underlying instant and show it in the browser's local time.
  const sharedCheckSystemHealthServerTime = sharedCheckSystemHealthResponse.sharedCheckSystemHealthServerTime;
  sharedCheckSystemHealthServerTimeText.dateTime = sharedCheckSystemHealthServerTime;
  sharedCheckSystemHealthServerTimeText.textContent = sharedFormatTime(sharedCheckSystemHealthServerTime);
});
