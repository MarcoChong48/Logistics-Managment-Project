package com.lms.backend.admin.user;

import java.util.List;

// One page of matching accounts and the count across all pages.
public record AdminSearchUserResponse(
        List<AdminSearchUserRow> adminSearchUserRowList,
        int adminSearchUserPageNumber,
        int adminSearchUserTotalPageCount,
        long adminSearchUserTotalRowCount) {
}
