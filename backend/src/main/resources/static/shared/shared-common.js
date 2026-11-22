// Provide request, session, formatting, message and dialog helpers for the live pages.

// ===== SECTION 1: PAGE ELEMENTS =====
// The frame builds its message area when the page opens.

// ===== SECTION 2: PAGE STATE =====
const sharedShowMessageTimerMap = new WeakMap();
const sharedOpenDialogOpeningMap = new WeakMap();
let sharedSendRequestSecurityToken;
let sharedSendRequestSecurityTokenHeaderName;
let sharedStartSessionCountdownIdleTimeoutSeconds = 0;
let sharedStartSessionCountdownIntervalID;

// ===== SECTION 3: START OF PAGE =====

// What it does: Hide this page before it can be stored in the browser's back and forward cache.
// Input: None.
// Output: The old contents stay hidden if the browser brings the page back.
window.addEventListener("pagehide", () => {
  document.body.hidden = true;
});

// What it does: Reopen a restored page so the server can check its session again.
// Input: The browser's page-show event.
// Output: A fresh load when the old page came from the back and forward cache.
window.addEventListener("pageshow", (sharedSendRequestPageEvent) => {
  if (sharedSendRequestPageEvent.persisted) {
    window.location.reload();
  }
});

// ===== SECTION 4: sharedSendRequest =====

// What it does: Send a request with the security token when needed and keep its button disabled.
// Input: The HTTP method, web address, optional JSON or form data body and optional button.
// Output: The parsed JSON answer or a CSV response. A failure gives a code, message and HTTP status.
async function sharedSendRequest(
  sharedSendRequestMethod,
  sharedSendRequestWebAddress,
  sharedSendRequestBody,
  sharedSendRequestButton
) {
  // Save the button's state so completion does not enable a button that was already disabled.
  const sharedSendRequestButtonWasDisabled = sharedSendRequestButton?.disabled;
  if (sharedSendRequestButton) {
    sharedSendRequestButton.disabled = true;
  }

  try {
    // Set the content type only when a JSON body is sent.
    const sharedSendRequestOptions = {
      method: sharedSendRequestMethod,
      headers: { Accept: "application/json, text/csv" }
    };
    if (sharedSendRequestBody instanceof FormData) {
      // The browser supplies the multipart content type and its boundary.
      sharedSendRequestOptions.body = sharedSendRequestBody;
    } else if (sharedSendRequestBody != null) {
      sharedSendRequestOptions.headers["Content-Type"] = "application/json";
      sharedSendRequestOptions.body = JSON.stringify(sharedSendRequestBody);
    }

    // Read a fresh token at sign-in in case the session expired while the form was open.
    if (["POST", "PUT", "DELETE"].includes(sharedSendRequestMethod)) {
      if (!sharedSendRequestSecurityToken || sharedSendRequestWebAddress === "/api/shared/login") {
        await sharedSendRequest("GET", "/api/shared/session");
      }
      sharedSendRequestOptions.headers[sharedSendRequestSecurityTokenHeaderName] = sharedSendRequestSecurityToken;
    }

    // A network failure has no HTTP status. Use zero and give the page a short message.
    let sharedSendRequestResponse;
    try {
      sharedSendRequestResponse = await fetch(sharedSendRequestWebAddress, {
        ...sharedSendRequestOptions,
        cache: "no-store"
      });
    } catch {
      throw { code: "SHARED_UNEXPECTED_ERROR", message: "Could not reach the server.", status: 0 };
    }

    // A completed request is activity for an active countdown, even if the server refused the action.
    if (sharedStartSessionCountdownIdleTimeoutSeconds > 0) {
      const sharedSendRequestKeepsOpenDialog = !sharedSendRequestResponse.ok
        && ["/api/shared/session/extend", "/api/shared/logout"].includes(sharedSendRequestWebAddress);
      sharedStartSessionCountdown(sharedStartSessionCountdownIdleTimeoutSeconds, sharedSendRequestKeepsOpenDialog);
    }

    // CSV downloads still count as activity and keep their server-supplied file name and bytes.
    if (sharedSendRequestResponse.ok
      && sharedSendRequestResponse.headers.get("Content-Type")?.startsWith("text/csv")) {
      return sharedSendRequestResponse;
    }

    // Handle a response that could not be read as JSON without showing browser or server details.
    let sharedSendRequestAnswer;
    try {
      sharedSendRequestAnswer = await sharedSendRequestResponse.json();
    } catch {
      throw {
        code: "SHARED_UNEXPECTED_ERROR",
        message: "Could not read the server's answer.",
        status: sharedSendRequestResponse.status
      };
    }

    // Leave failed sign-in on the form. A protected request with an ended session returns to login.
    if (!sharedSendRequestResponse.ok) {
      if (sharedSendRequestResponse.status === 401 && sharedSendRequestWebAddress !== "/api/shared/login") {
        const sharedSendRequestCode = sharedSendRequestAnswer?.code;
        if (sharedSendRequestWebAddress === "/api/shared/change-password" && sharedSendRequestMethod === "POST"
          && ["SHARED_SESSION_ENDED", "SHARED_NOT_LOGGED_IN"].includes(sharedSendRequestCode)) {
          window.location.replace("/shared/shared-login.html?sharedLoginUserNotice=PASSWORD_NOT_CHANGED");
        } else if (sharedSendRequestCode === "SHARED_SESSION_ENDED") {
          window.location.replace("/shared/shared-login.html?sharedLoginUserNotice=SESSION_ENDED");
        } else if (sharedSendRequestCode === "SHARED_NOT_LOGGED_IN") {
          window.location.replace("/shared/shared-login.html");
        }
      }
      throw {
        ...sharedSendRequestAnswer,
        code: sharedSendRequestAnswer?.code ?? "SHARED_UNEXPECTED_ERROR",
        message: sharedSendRequestAnswer?.message ?? "The server could not complete the request.",
        status: sharedSendRequestResponse.status
      };
    }

    // Keep the token in this page only. Authentication changes require a fresh token.
    if (sharedSendRequestWebAddress === "/api/shared/session") {
      sharedSendRequestSecurityToken = sharedSendRequestAnswer.sharedViewSessionSecurityToken;
      sharedSendRequestSecurityTokenHeaderName = sharedSendRequestAnswer.sharedViewSessionSecurityTokenHeaderName;
    } else if (["/api/shared/login", "/api/shared/logout"].includes(sharedSendRequestWebAddress)) {
      sharedSendRequestSecurityToken = undefined;
      sharedSendRequestSecurityTokenHeaderName = undefined;
      try {
        await sharedSendRequest("GET", "/api/shared/session");
      } catch {
        // The next page can read the token if the refresh request did not complete.
      }
    }
    return sharedSendRequestAnswer;
  } finally {
    // Restore the original button state even if the request failed.
    if (sharedSendRequestButton) {
      sharedSendRequestButton.disabled = sharedSendRequestButtonWasDisabled;
    }
  }
}

// ===== SECTION 5: sharedShowMessage =====

// What it does: Show a corner message and remove it after its fade.
// Input: A SUCCESS, ERROR or INFORMATION kind, and the text to display.
// Output: A temporary message box, or no change if a modal is open or the message area does not exist.
function sharedShowMessage(sharedShowMessageKind, sharedShowMessageText) {
  // A modal owns its feedback so a message never appears behind it.
  const sharedShowMessagePanel = document.getElementById("sharedShowMessagePanel");
  if (!sharedShowMessagePanel || document.querySelector("dialog:modal")) {
    return;
  }

  // Make the supplied text plain page content and use the icon for the selected message kind.
  const sharedShowMessageKindName = sharedShowMessageKind.toLowerCase();
  const sharedShowMessageBox = sharedBuildMessage(
    "shared-message-box shared-message-box-" + sharedShowMessageKindName, sharedShowMessageKind, sharedShowMessageText
  );
  sharedShowMessageBox.setAttribute("role", "status");
  sharedShowMessagePanel.querySelector("#sharedShowMessageText")?.removeAttribute("id");
  sharedShowMessageBox.lastElementChild.id = "sharedShowMessageText";
  sharedShowMessagePanel.append(sharedShowMessageBox);

  // Keep the two timers so opening a modal can cancel them.
  const sharedShowMessageTimerList = [];
  sharedShowMessageTimerMap.set(sharedShowMessageBox, sharedShowMessageTimerList);

  // What it does: Start the message fade.
  // Input: None. Uses the box and timer list for this message.
  // Output: A fading message and a scheduled removal.
  sharedShowMessageTimerList.push(window.setTimeout(() => {
    sharedShowMessageBox.classList.add("shared-message-box-leaving");

    // What it does: Remove the message after the fade.
    // Input: None. Uses the box for this message.
    // Output: The box and its timer entry are removed.
    sharedShowMessageTimerList.push(window.setTimeout(() => {
      sharedShowMessageBox.remove();
      sharedShowMessageTimerMap.delete(sharedShowMessageBox);
    }, 200));
  }, 4000));
}

// ===== SECTION 6: sharedFormatTime =====

// What it does: Display an instant in the browser's local time.
// Input: An ISO time string, which may be empty.
// Output: A local date and time through the minute, or a dash if no valid time was supplied.
function sharedFormatTime(sharedFormatTimeISOText) {
  // Read the timestamp once. A missing or unreadable value has no time to display.
  const sharedFormatTimeDate = new Date(sharedFormatTimeISOText || "");
  if (Number.isNaN(sharedFormatTimeDate.getTime())) {
    return "-";
  }

  // These date getters use the browser's local time.
  const sharedFormatTimeYear = String(sharedFormatTimeDate.getFullYear()).padStart(4, "0");
  const sharedFormatTimeMonth = String(sharedFormatTimeDate.getMonth() + 1).padStart(2, "0");
  const sharedFormatTimeDay = String(sharedFormatTimeDate.getDate()).padStart(2, "0");
  const sharedFormatTimeHour = String(sharedFormatTimeDate.getHours()).padStart(2, "0");
  const sharedFormatTimeMinute = String(sharedFormatTimeDate.getMinutes()).padStart(2, "0");
  return sharedFormatTimeYear + "-" + sharedFormatTimeMonth + "-" + sharedFormatTimeDay
    + " " + sharedFormatTimeHour + ":" + sharedFormatTimeMinute;
}

// ===== SECTION 7: sharedFormatMoney =====

// What it does: Format a money value with its currency and two decimal places.
// Input: The amount as a number.
// Output: Text such as HKD 1,234.50.
function sharedFormatMoney(sharedFormatMoneyAmount) {
  // Use the decimal punctuation shown throughout the Admin pages.
  const sharedFormatMoneyFormatter = new Intl.NumberFormat("en", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  return "HKD " + sharedFormatMoneyFormatter.format(sharedFormatMoneyAmount);
}

// ===== SECTION 8: sharedFormatStatusWord =====

// What it does: Turn a stored status word into a sentence-case screen label.
// Input: A stored status word such as READY_FOR_PICKING.
// Output: Plain words with ID, QR, SKU, IP and CSV capitalized.
function sharedFormatStatusWord(sharedFormatStatusWordValue) {
  // Staff roles keep the capitalization used throughout the LMS.
  if (sharedFormatStatusWordValue === "WAREHOUSE_WORKER") {
    return "Warehouse Worker";
  }
  if (sharedFormatStatusWordValue === "DELIVERY_WORKER") {
    return "Delivery Worker";
  }

  // Use spaces and sentence case, preserving the approved acronyms.
  const sharedFormatStatusWordText = sharedFormatStatusWordValue.replaceAll("_", " ").toLowerCase();
  const sharedFormatStatusWordSentence = sharedFormatStatusWordText.charAt(0).toUpperCase()
    + sharedFormatStatusWordText.slice(1);

  // What it does: Restore the approved capitalization for an acronym.
  // Input: One matched acronym.
  // Output: The acronym in upper case.
  return sharedFormatStatusWordSentence.replace(/\b(id|qr|sku|ip|csv)\b/gi, (sharedFormatStatusWordAcronym) => {
    return sharedFormatStatusWordAcronym.toUpperCase();
  });
}

// ===== SECTION 9: sharedReadPageParameter =====

// What it does: Read one query parameter from the current page address.
// Input: The parameter name.
// Output: The parameter value, or an empty string if the parameter is absent.
function sharedReadPageParameter(sharedReadPageParameterName) {
  // Read the current location so later address changes are included.
  return new URLSearchParams(window.location.search).get(sharedReadPageParameterName) ?? "";
}

// ===== SECTION 10: sharedOpenDialog and sharedCloseDialog =====

// What it does: Clear corner messages and open a modal.
// Input: The dialog element to open.
// Output: An open modal that supports Escape and retains focus inside.
function sharedOpenDialog(sharedOpenDialogElement) {
  // Close row menus before placing the modal in the top layer.
  for (const sharedOpenDialogMenu of document.querySelectorAll(".shared-menu-list:popover-open")) {
    sharedOpenDialogMenu.hidePopover();
  }

  // Cancel the timers before clearing the boxes so a pending fade cannot outlive them.
  const sharedShowMessagePanel = document.getElementById("sharedShowMessagePanel");
  if (sharedShowMessagePanel) {
    for (const sharedOpenDialogMessageBox of sharedShowMessagePanel.children) {
      const sharedOpenDialogTimerList = sharedShowMessageTimerMap.get(sharedOpenDialogMessageBox) ?? [];
      for (const sharedOpenDialogTimerID of sharedOpenDialogTimerList) {
        window.clearTimeout(sharedOpenDialogTimerID);
      }
      sharedShowMessageTimerMap.delete(sharedOpenDialogMessageBox);
    }
    sharedShowMessagePanel.replaceChildren();
  }

  // Each opening has its own clipboard feedback, even when the same element is used again.
  sharedOpenDialogOpeningMap.set(sharedOpenDialogElement, {});
  for (const sharedOpenDialogMessagePlace of sharedOpenDialogElement.querySelectorAll(".shared-message-place")) {
    sharedOpenDialogMessagePlace.replaceChildren();
  }

  // The native modal lets Escape close it and a backdrop click leave it open.
  sharedOpenDialogElement.showModal();
}

// What it does: Close a modal from its own controls.
// Input: The dialog element to close.
// Output: A closed dialog. Cleared corner messages stay cleared.
function sharedCloseDialog(sharedCloseDialogElement) {
  // Pending clipboard attempts for this opening must lose access to its feedback.
  sharedOpenDialogOpeningMap.delete(sharedCloseDialogElement);
  // Leave focus restoration to the browser.
  sharedCloseDialogElement.close();
}

// ===== SECTION 11: sharedWritePageParameter =====

// What it does: Store live filters and pagination in the current page address.
// Input: An iterable of parameter names and values, with empty values omitted.
// Output: The current address changes in place without adding a history entry.
function sharedWritePageParameter(sharedWritePageParameterValueList) {
  // Keep parameters owned by other parts of the page.
  const sharedWritePageParameterWebAddress = new URL(window.location.href);
  for (const [sharedWritePageParameterName, sharedWritePageParameterValue] of sharedWritePageParameterValueList) {
    if (sharedWritePageParameterValue === "" || sharedWritePageParameterValue == null) {
      sharedWritePageParameterWebAddress.searchParams.delete(sharedWritePageParameterName);
    } else {
      sharedWritePageParameterWebAddress.searchParams.set(sharedWritePageParameterName, sharedWritePageParameterValue);
    }
  }
  history.replaceState(history.state, "", sharedWritePageParameterWebAddress);
}

// ===== SECTION 12: sharedBuildPager =====

// What it does: Draw the count and the controls for moving through an account list.
// Input: A pager element, the current page, the total pages and a callback for another page.
// Output: A pager whose active page is labeled and whose end controls cannot go out of range.
function sharedBuildPager(sharedBuildPagerElement, sharedBuildPagerPageNumber, sharedBuildPagerTotalPageCount,
  sharedBuildPagerSelectPage) {
  // Label the current page separately from the button group.
  const sharedBuildPagerText = document.createElement("span");
  sharedBuildPagerText.textContent = "Page " + sharedBuildPagerPageNumber + " of " + sharedBuildPagerTotalPageCount;
  const sharedBuildPagerButtons = document.createElement("span");
  sharedBuildPagerButtons.className = "shared-pager-buttons";

  // What it does: Add a pager control for another page.
  // Input: Button label, target page number and whether moving there is unavailable.
  // Output: The new control goes at the end of the button group.
  const sharedBuildPagerAddButton = (sharedBuildPagerButtonText, sharedBuildPagerTargetPageNumber,
    sharedBuildPagerIsDisabled) => {
    const sharedBuildPagerButton = document.createElement("button");
    sharedBuildPagerButton.type = "button";
    sharedBuildPagerButton.className = "shared-button shared-button-small";
    sharedBuildPagerButton.textContent = sharedBuildPagerButtonText;
    sharedBuildPagerButton.disabled = sharedBuildPagerIsDisabled;

    // What it does: Ask the caller to load the chosen page.
    // Input: None. Uses the control's target page number.
    // Output: The caller starts loading that page.
    sharedBuildPagerButton.addEventListener("click", () => sharedBuildPagerSelectPage(sharedBuildPagerTargetPageNumber));
    sharedBuildPagerButtons.append(sharedBuildPagerButton);
  };

  // Draw the numbered controls between Previous and Next.
  sharedBuildPagerAddButton("Previous", sharedBuildPagerPageNumber - 1, sharedBuildPagerPageNumber === 1);
  for (let sharedBuildPagerNumber = 1; sharedBuildPagerNumber <= sharedBuildPagerTotalPageCount;
    sharedBuildPagerNumber += 1) {
    if (sharedBuildPagerNumber === sharedBuildPagerPageNumber) {
      const sharedBuildPagerCurrent = document.createElement("span");
      sharedBuildPagerCurrent.className = "shared-pager-current";
      sharedBuildPagerCurrent.setAttribute("aria-current", "page");
      sharedBuildPagerCurrent.textContent = sharedBuildPagerNumber;
      sharedBuildPagerButtons.append(sharedBuildPagerCurrent);
    } else {
      sharedBuildPagerAddButton(String(sharedBuildPagerNumber), sharedBuildPagerNumber, false);
    }
  }
  sharedBuildPagerAddButton("Next", sharedBuildPagerPageNumber + 1,
    sharedBuildPagerPageNumber === sharedBuildPagerTotalPageCount);
  sharedBuildPagerElement.replaceChildren(sharedBuildPagerText, sharedBuildPagerButtons);
}

// ===== SECTION 13: sharedWatchTableScroll =====

// What it does: Show a permanent horizontal thumb for each table that needs extra room.
// Input: None. Finds the table scroll regions on the page.
// Output: The thumb follows table scrolling and named regions keep their pinned first column.
function sharedWatchTableScroll() {
  const sharedWatchTableScrollRegionList = document.querySelectorAll(".shared-table-scroll");
  for (const sharedWatchTableScrollRegion of sharedWatchTableScrollRegionList) {
    const sharedWatchTableScrollTable = sharedWatchTableScrollRegion.querySelector(":scope > .shared-table");
    if (!sharedWatchTableScrollTable || sharedWatchTableScrollRegion.querySelector(".shared-table-scrollbar")) {
      continue;
    }

    const sharedWatchTableScrollBar = sharedBuildTableScrollbar(sharedWatchTableScrollRegion);
    const sharedWatchTableScrollFirstHeading = sharedWatchTableScrollRegion.matches('[role="region"]')
      ? sharedWatchTableScrollTable.querySelector("thead th")
      : null;

    // What it does: Update the thumb and pinned column after the table or its frame changes size.
    // Input: The observed table and its scroll box. Output: A usable control only for visible overflow.
    const sharedWatchTableScrollUpdate = () => {
      const sharedWatchTableScrollWidth = sharedWatchTableScrollRegion.clientWidth;
      const sharedWatchTableScrollFullWidth = sharedWatchTableScrollRegion.scrollWidth;
      const sharedWatchTableScrollMaximum = sharedWatchTableScrollFullWidth - sharedWatchTableScrollWidth;
      const sharedWatchTableScrollHasOverflow = sharedWatchTableScrollWidth > 0 && sharedWatchTableScrollMaximum > 0;
      sharedWatchTableScrollBar.hidden = !sharedWatchTableScrollHasOverflow;
      if (sharedWatchTableScrollHasOverflow) {
        sharedWatchTableScrollBar.max = sharedWatchTableScrollMaximum;
        sharedWatchTableScrollBar.value = sharedWatchTableScrollRegion.scrollLeft;
        sharedWatchTableScrollBar.style.setProperty("--shared-table-scrollbar-thumb-width",
          (100 * sharedWatchTableScrollWidth / sharedWatchTableScrollFullWidth) + "%");
      }
      if (sharedWatchTableScrollFirstHeading) {
        sharedWatchTableScrollRegion.classList.toggle("shared-table-scroll-overflow",
          sharedWatchTableScrollHasOverflow);
        if (sharedWatchTableScrollHasOverflow) {
          sharedWatchTableScrollRegion.tabIndex = 0;
          sharedWatchTableScrollRegion.style.setProperty("--shared-table-pinned-width",
            sharedWatchTableScrollFirstHeading.getBoundingClientRect().width + "px");
        } else {
          sharedWatchTableScrollRegion.removeAttribute("tabindex");
          sharedWatchTableScrollRegion.style.removeProperty("--shared-table-pinned-width");
        }
      }
    };

    const sharedWatchTableScrollObserver = new ResizeObserver(sharedWatchTableScrollUpdate);
    sharedWatchTableScrollObserver.observe(sharedWatchTableScrollRegion);
    sharedWatchTableScrollObserver.observe(sharedWatchTableScrollTable);
    sharedWatchTableScrollUpdate();
  }
}

// What it does: Put a native drag and keyboard control inside the bottom of the scroll box.
// Input: The table's scroll box. Output: The slider, linked in both directions to horizontal scrolling.
function sharedBuildTableScrollbar(sharedBuildTableScrollbarRegion) {
  const sharedBuildTableScrollbarInput = document.createElement("input");
  sharedBuildTableScrollbarInput.type = "range";
  sharedBuildTableScrollbarInput.className = "shared-table-scrollbar";
  sharedBuildTableScrollbarInput.min = "0";
  sharedBuildTableScrollbarInput.max = "0";
  sharedBuildTableScrollbarInput.value = "0";
  sharedBuildTableScrollbarInput.hidden = true;
  const sharedBuildTableScrollbarName = sharedBuildTableScrollbarRegion.getAttribute("aria-label")
    || sharedBuildTableScrollbarRegion.closest(".shared-card")?.querySelector(".shared-card-title")?.textContent.trim()
    || "table";
  sharedBuildTableScrollbarInput.setAttribute(
    "aria-label", "Scroll " + sharedBuildTableScrollbarName + " horizontally");

  // What it does: Pan the content when the slider moves. Input: Slider value. Output: The table's new offset.
  sharedBuildTableScrollbarInput.addEventListener("input", () => {
    sharedBuildTableScrollbarRegion.scrollLeft = sharedBuildTableScrollbarInput.valueAsNumber;
  });
  // What it does: Follow swipes, wheel and keyboard scrolling. Input: Table offset. Output: The slider's new value.
  sharedBuildTableScrollbarRegion.addEventListener("scroll", () => {
    sharedBuildTableScrollbarInput.value = sharedBuildTableScrollbarRegion.scrollLeft;
  }, { passive: true });
  sharedBuildTableScrollbarRegion.append(sharedBuildTableScrollbarInput);
  return sharedBuildTableScrollbarInput;
}

// ===== SECTION 14: sharedPlaceActionMenu =====

// What it does: Place an open popover by its Actions button without clipping it on a short screen.
// Input: The open row menu.
// Output: The menu is positioned below or above the button, bounded at the top and left of the window.
function sharedPlaceActionMenu(sharedPlaceActionMenuList) {
  // Measure only after the browser has opened the native popover.
  const sharedPlaceActionMenuButton = sharedPlaceActionMenuList.parentElement.querySelector("[popovertarget]");
  const sharedPlaceActionMenuAnchor = sharedPlaceActionMenuButton.getBoundingClientRect();
  const sharedPlaceActionMenuHeight = sharedPlaceActionMenuList.offsetHeight;
  const sharedPlaceActionMenuWidth = sharedPlaceActionMenuList.offsetWidth;
  let sharedPlaceActionMenuTop = sharedPlaceActionMenuAnchor.bottom + 4;

  // If there is no room below, place the menu above. The stylesheet limits its tail at the window edge.
  if (sharedPlaceActionMenuTop + sharedPlaceActionMenuHeight > window.innerHeight - 8) {
    sharedPlaceActionMenuTop = sharedPlaceActionMenuAnchor.top - sharedPlaceActionMenuHeight - 4;
  }
  sharedPlaceActionMenuList.style.top = Math.max(8, sharedPlaceActionMenuTop) + "px";
  sharedPlaceActionMenuList.style.left = Math.max(8, sharedPlaceActionMenuAnchor.right - sharedPlaceActionMenuWidth) + "px";
}

// ===== SECTION 15: sharedCopyText =====

// What it does: Copy text and show the outcome inside the window that requested the copy.
// Input: A Copy button with the value, feedback place and failure wording in its data attributes.
// Output: The clipboard is written if available. Only the newest attempt in this opening can show feedback.
async function sharedCopyText(sharedCopyTextButton) {
  // Associate this attempt with the specific displayed opening.
  const sharedCopyTextDialog = sharedCopyTextButton.closest("dialog");
  const sharedCopyTextOpening = sharedOpenDialogOpeningMap.get(sharedCopyTextDialog);
  if (!sharedCopyTextDialog?.open || !sharedCopyTextOpening) {
    return;
  }
  const sharedCopyTextAttempt = {};
  sharedCopyTextOpening.sharedCopyTextLatestAttempt = sharedCopyTextAttempt;

  // Report success only when the browser resolves the write.
  let sharedCopyTextIsCopied = false;
  try {
    if (typeof navigator.clipboard?.writeText === "function") {
      await navigator.clipboard.writeText(document.getElementById(sharedCopyTextButton.dataset.copy).textContent);
      sharedCopyTextIsCopied = true;
    }
  } catch {
    // The message below gives the user a manual way to copy if the browser refuses.
  }

  // Ignore late outcomes from a closed or reused window and from superseded attempts.
  if (!sharedCopyTextDialog.open || sharedOpenDialogOpeningMap.get(sharedCopyTextDialog) !== sharedCopyTextOpening
    || sharedCopyTextOpening.sharedCopyTextLatestAttempt !== sharedCopyTextAttempt) {
    return;
  }
  const sharedCopyTextPlace = document.getElementById(sharedCopyTextButton.dataset.copyPlace);
  if (sharedCopyTextIsCopied) {
    sharedCopyTextPlace.replaceChildren(sharedBuildMessage("shared-inline-message shared-inline-message-success",
      "SUCCESS", "Copied."));
  } else {
    sharedCopyTextPlace.replaceChildren(sharedBuildMessage("shared-inline-message shared-inline-message-error",
      "ERROR", sharedCopyTextButton.dataset.copyFailed));
  }
}

// ===== SECTION 16: sharedStartSessionCountdown =====

// What it does: Start the session countdown again after a request.
// Input: The server's idle timeout in seconds and whether a refused action should keep the warning open.
// Output: A live top-bar time, one warning per countdown, and a return to login at zero.
function sharedStartSessionCountdown(
  sharedStartSessionCountdownTimeoutSeconds,
  sharedStartSessionCountdownKeepsOpenDialog = false
) {
  // There is nothing to count on pages without a session timer.
  const sharedStartSessionCountdownText = document.getElementById("sharedStartSessionCountdownText");
  if (!sharedStartSessionCountdownText) {
    return;
  }

  // Use the deadline so the value stays accurate while this tab is in the background.
  sharedStartSessionCountdownIdleTimeoutSeconds = sharedStartSessionCountdownTimeoutSeconds;
  const sharedStartSessionCountdownEndTime = Date.now() + sharedStartSessionCountdownTimeoutSeconds * 1000;
  window.clearInterval(sharedStartSessionCountdownIntervalID);
  const sharedStartSessionCountdownDialog = document.getElementById("sharedStartSessionCountdownDialog");
  if (sharedStartSessionCountdownDialog.open && !sharedStartSessionCountdownKeepsOpenDialog) {
    sharedCloseDialog(sharedStartSessionCountdownDialog);
  }
  let sharedStartSessionCountdownHasWarned = sharedStartSessionCountdownDialog.open;

  // What it does: Draw the time from the deadline and act at the warning and end times.
  // Input: None. Uses this countdown's deadline, text and warning dialog.
  // Output: Updated text, an opened warning, or navigation to the login page.
  const sharedStartSessionCountdownUpdate = () => {
    const sharedStartSessionCountdownRemainingSeconds = Math.ceil((sharedStartSessionCountdownEndTime - Date.now())
      / 1000);
    if (sharedStartSessionCountdownRemainingSeconds <= 0) {
      window.clearInterval(sharedStartSessionCountdownIntervalID);
      window.location.assign("/shared/shared-login.html?sharedLoginUserNotice=IDLE");
      return;
    }

    const sharedStartSessionCountdownMinutes = Math.floor(sharedStartSessionCountdownRemainingSeconds / 60);
    const sharedStartSessionCountdownSeconds =
      String(sharedStartSessionCountdownRemainingSeconds % 60).padStart(2, "0");
    sharedStartSessionCountdownText.textContent = sharedStartSessionCountdownMinutes + ":"
      + sharedStartSessionCountdownSeconds;

    // Escape can close the dialog for this countdown. A new request starts a new countdown.
    if (sharedStartSessionCountdownRemainingSeconds <= 120 && !sharedStartSessionCountdownHasWarned
        && !document.querySelector("dialog:modal")) {
      sharedStartSessionCountdownHasWarned = true;
      sharedStartSessionCountdownDialog.querySelector(".shared-inline-message").hidden = true;
      sharedOpenDialog(sharedStartSessionCountdownDialog);
    }
  };
  sharedStartSessionCountdownIntervalID = window.setInterval(sharedStartSessionCountdownUpdate, 250);
  sharedStartSessionCountdownUpdate();
}

// ===== SECTION 17: sharedExtendSession =====

// What it does: Count the "Stay logged in" action as activity on the server.
// Input: The click event from the actual session extension button.
// Output: A restarted countdown, or the server's error in the warning.
async function sharedExtendSession(sharedExtendSessionEvent) {
  // The request helper restarts the countdown and closes the warning after an answer.
  const sharedExtendSessionButton = sharedExtendSessionEvent.currentTarget;
  try {
    await sharedSendRequest("POST", "/api/shared/session/extend", null, sharedExtendSessionButton);
  } catch (sharedExtendSessionError) {
    const sharedStartSessionCountdownDialog = document.getElementById("sharedStartSessionCountdownDialog");
    if (sharedStartSessionCountdownDialog.open) {
      const sharedExtendSessionMessage = sharedStartSessionCountdownDialog.querySelector(".shared-inline-message");
      sharedExtendSessionMessage.hidden = false;
      sharedExtendSessionMessage.querySelector("p").textContent = sharedExtendSessionError.message;
    } else {
      sharedShowMessage("ERROR", sharedExtendSessionError.message);
    }
  }
}

// ===== SECTION 18: sharedLogoutUser =====

// What it does: Log out from the current session.
// Input: The pressed button and an optional message place on a page without the frame.
// Output: Navigation to logged-out login, or a message if logout was refused.
async function sharedLogoutUser(sharedLogoutUserButton, sharedLogoutUserMessage) {
  // The session ends on the server before the browser switches pages.
  try {
    await sharedSendRequest("POST", "/api/shared/logout", null, sharedLogoutUserButton);
    window.clearInterval(sharedStartSessionCountdownIntervalID);
    window.location.assign("/shared/shared-login.html?sharedLoginUserNotice=LOGGED_OUT");
  } catch (sharedLogoutUserError) {
    // Give the active warning dialog its own feedback while it remains open.
    const sharedStartSessionCountdownDialog = document.getElementById("sharedStartSessionCountdownDialog");
    if (sharedStartSessionCountdownDialog?.open) {
      const sharedLogoutUserDialogMessage = sharedStartSessionCountdownDialog.querySelector(".shared-inline-message");
      sharedLogoutUserDialogMessage.hidden = false;
      sharedLogoutUserDialogMessage.querySelector("p").textContent = sharedLogoutUserError.message;
    } else if (sharedLogoutUserMessage) {
      sharedShowInlineMessage(sharedLogoutUserMessage, "ERROR", sharedLogoutUserError.message);
    } else {
      sharedShowMessage("ERROR", sharedLogoutUserError.message);
    }
  }
}

// ===== SECTION 19: sharedCheckPasswordRuleList =====

// What it does: Check the password rules for advisory feedback while the user types.
// Input: The exact new and current passwords.
// Output: The four rule results, length count, byte limit result and whether the passwords are the same.
function sharedCheckPasswordRuleList(sharedCheckPasswordRuleListNewPassword,
  sharedCheckPasswordRuleListCurrentPassword) {
  // Match the server's Unicode character and UTF-8 byte counts.
  const sharedCheckPasswordRuleListCharacterCount = Array.from(sharedCheckPasswordRuleListNewPassword).length;
  const sharedCheckPasswordRuleListIsOverByteLimit =
    new TextEncoder().encode(sharedCheckPasswordRuleListNewPassword).length > 72;
  return {
    sharedCheckPasswordRuleListCharacterCount,
    sharedCheckPasswordRuleListIsOverByteLimit,
    sharedCheckPasswordRuleListIsLengthMet: sharedCheckPasswordRuleListCharacterCount >= 8
      && sharedCheckPasswordRuleListCharacterCount <= 72 && !sharedCheckPasswordRuleListIsOverByteLimit,
    sharedCheckPasswordRuleListHasCapital: /[A-Z]/.test(sharedCheckPasswordRuleListNewPassword),
    sharedCheckPasswordRuleListHasSmall: /[a-z]/.test(sharedCheckPasswordRuleListNewPassword),
    sharedCheckPasswordRuleListHasDigit: /[0-9]/.test(sharedCheckPasswordRuleListNewPassword),
    sharedCheckPasswordRuleListIsSameAsCurrent: sharedCheckPasswordRuleListNewPassword !== ""
      && sharedCheckPasswordRuleListNewPassword === sharedCheckPasswordRuleListCurrentPassword
  };
}

// ===== SECTION 20: sharedMeasurePasswordStrength =====

// What it does: Rate length and character variety for the strength display.
// Input: The exact new password.
// Output: NOT_RATED, TOO_WEAK, FAIR, GOOD or STRONG. This advice never blocks a request.
function sharedMeasurePasswordStrength(sharedMeasurePasswordStrengthNewPassword) {
  // Use the same four rule results that the page displays.
  if (sharedMeasurePasswordStrengthNewPassword === "") {
    return "NOT_RATED";
  }
  const sharedMeasurePasswordStrengthRule = sharedCheckPasswordRuleList(sharedMeasurePasswordStrengthNewPassword, "");
  if (!sharedMeasurePasswordStrengthRule.sharedCheckPasswordRuleListIsLengthMet
    || !sharedMeasurePasswordStrengthRule.sharedCheckPasswordRuleListHasCapital
    || !sharedMeasurePasswordStrengthRule.sharedCheckPasswordRuleListHasSmall
    || !sharedMeasurePasswordStrengthRule.sharedCheckPasswordRuleListHasDigit) {
    return "TOO_WEAK";
  }

  // Test Strong first so the levels stay distinct.
  const sharedMeasurePasswordStrengthCharacterCount =
    sharedMeasurePasswordStrengthRule.sharedCheckPasswordRuleListCharacterCount;
  const sharedMeasurePasswordStrengthHasOtherCharacter = /[^A-Za-z0-9]/.test(sharedMeasurePasswordStrengthNewPassword);
  if (sharedMeasurePasswordStrengthCharacterCount >= 16
    || (sharedMeasurePasswordStrengthCharacterCount >= 12 && sharedMeasurePasswordStrengthHasOtherCharacter)) {
    return "STRONG";
  }
  if (sharedMeasurePasswordStrengthCharacterCount >= 12 || sharedMeasurePasswordStrengthHasOtherCharacter) {
    return "GOOD";
  }
  return "FAIR";
}

// ===== SECTION 21: sharedTogglePasswordVisibility =====

// What it does: Show or hide the password input next to the pressed button.
// Input: The Show or Hide button inside the password input row.
// Output: Updated visibility and a button label that describes its next action.
function sharedTogglePasswordVisibility(sharedTogglePasswordVisibilityButton) {
  // Keep the same input and value so a password remains exactly as entered.
  const sharedTogglePasswordVisibilityInput = sharedTogglePasswordVisibilityButton.parentElement.querySelector("input");
  const sharedTogglePasswordVisibilityIsShowing = sharedTogglePasswordVisibilityInput.type === "password";
  sharedTogglePasswordVisibilityInput.type = sharedTogglePasswordVisibilityIsShowing ? "text" : "password";
  const sharedTogglePasswordVisibilityAction = sharedTogglePasswordVisibilityIsShowing ? "Hide" : "Show";
  sharedTogglePasswordVisibilityButton.textContent = sharedTogglePasswordVisibilityAction;
  const sharedTogglePasswordVisibilityLabel = sharedTogglePasswordVisibilityButton.getAttribute("aria-label");
  sharedTogglePasswordVisibilityButton.setAttribute("aria-label",
    sharedTogglePasswordVisibilityLabel.replace(/^\S+/, sharedTogglePasswordVisibilityAction));
}

// ===== SECTION 22: sharedShowFieldError =====

// What it does: Draw or clear a field error and reflect the field's invalid state.
// Input: The field, its error line, optional words and whether to mark it even with no words.
// Output: Updated text, visibility and accessible field state.
function sharedShowFieldError(sharedShowFieldErrorInput, sharedShowFieldErrorText, sharedShowFieldErrorWords,
  sharedShowFieldErrorIsMarked = false) {
  // A red rule state can mark a field even when its error line has no words.
  sharedShowFieldErrorText.textContent = sharedShowFieldErrorWords;
  sharedShowFieldErrorText.hidden = sharedShowFieldErrorWords === "";
  const sharedShowFieldErrorIsInvalid = sharedShowFieldErrorIsMarked || sharedShowFieldErrorWords !== "";
  sharedShowFieldErrorInput.classList.toggle("shared-input-invalid", sharedShowFieldErrorIsInvalid);
  if (sharedShowFieldErrorIsInvalid) {
    sharedShowFieldErrorInput.setAttribute("aria-invalid", "true");
  } else {
    sharedShowFieldErrorInput.removeAttribute("aria-invalid");
  }
}

// ===== SECTION 23: sharedShowInlineMessage =====

// What it does: Put one icon and message into an existing live region.
// Input: The message place, message kind and plain text.
// Output: One inline message. The live region remains available to assistive technology.
function sharedShowInlineMessage(sharedShowInlineMessagePlace, sharedShowInlineMessageKind,
  sharedShowInlineMessageText) {
  // The information variant uses the base class alone.
  let sharedShowInlineMessageClasses = "shared-inline-message";
  if (sharedShowInlineMessageKind !== "INFORMATION") {
    sharedShowInlineMessageClasses += " shared-inline-message-" + sharedShowInlineMessageKind.toLowerCase();
  }
  sharedShowInlineMessagePlace.replaceChildren(
    sharedBuildMessage(sharedShowInlineMessageClasses, sharedShowInlineMessageKind, sharedShowInlineMessageText)
  );
}

// ===== SECTION 24: HELPER FUNCTIONS =====

// What it does: Build the icon and text shared by corner and inline messages.
// Input: The design classes, the message kind and the message text.
// Output: A detached message box with safe plain text.
function sharedBuildMessage(sharedBuildMessageClasses, sharedBuildMessageKind, sharedBuildMessageText) {
  // Select the icon that explains the message without relying on its colour.
  const sharedBuildMessageIconName = { SUCCESS: "check", ERROR: "alert", INFORMATION: "information" };
  const sharedBuildMessageBox = document.createElement("div");
  sharedBuildMessageBox.className = sharedBuildMessageClasses;
  const sharedBuildMessageIcon = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  sharedBuildMessageIcon.classList.add("shared-icon");
  sharedBuildMessageIcon.setAttribute("aria-hidden", "true");
  const sharedBuildMessageIconUse = document.createElementNS("http://www.w3.org/2000/svg", "use");
  sharedBuildMessageIconUse.setAttribute("href", "#icon-" + sharedBuildMessageIconName[sharedBuildMessageKind]);
  sharedBuildMessageIcon.append(sharedBuildMessageIconUse);

  // Add the supplied message as text so names and errors cannot become markup.
  const sharedBuildMessageParagraph = document.createElement("p");
  sharedBuildMessageParagraph.textContent = sharedBuildMessageText;
  sharedBuildMessageBox.append(sharedBuildMessageIcon, sharedBuildMessageParagraph);
  return sharedBuildMessageBox;
}
