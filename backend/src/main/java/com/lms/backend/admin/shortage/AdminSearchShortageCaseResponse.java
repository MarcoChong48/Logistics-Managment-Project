package com.lms.backend.admin.shortage;

import java.util.List;

/** A page of warehouse shortage cases and the result counts. */
public record AdminSearchShortageCaseResponse(
        List<AdminSearchShortageCaseRow> adminSearchShortageCaseRowList,
        int adminSearchShortageCasePageNumber,
        int adminSearchShortageCaseTotalPageCount,
        long adminSearchShortageCaseTotalRowCount) {
}
