package com.lms.backend;

import static com.lms.backend.admin.order.AdminOrderTimelineBuilder.adminBuildOrderTimeline;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_ATTEMPT_OUTCOME_FAILED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_FAILURE_REASON_CLIENT_NOT_HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lms.backend.admin.order.AdminViewOrderDeliveryAttemptRow;
import com.lms.backend.admin.order.AdminViewOrderPickingTask;
import com.lms.backend.admin.order.AdminViewOrderTimelineRow;
import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.OrderDeliveryDetail;

/** Database-free checks for ordering, missing times and safe timeline text. */
class AdminOrderTimelineRuleCheckTest {

    // ===== SECTION 1: ORDER TIMELINE RULES =====

    /**
     * What it does: Uses input versions in reverse time order and a task with no timestamps.
     * Input: Synthetic order, delivery detail versions and an unstarted task.
     * Output: Three dated rows in ascending order and public version-only text.
     */
    @Test
    void adminCheckOrderTimelineDates() {
        CustomerOrder adminCheckOrderTimelineDatesOrder = new CustomerOrder();
        adminCheckOrderTimelineDatesOrder.createdTime = Instant.parse("2026-11-18T07:00:00Z");
        OrderDeliveryDetail adminCheckOrderTimelineDatesFirstVersion = new OrderDeliveryDetail();
        adminCheckOrderTimelineDatesFirstVersion.versionNo = 1;
        adminCheckOrderTimelineDatesFirstVersion.createdTime = Instant.parse("2026-11-18T07:01:00Z");
        OrderDeliveryDetail adminCheckOrderTimelineDatesSecondVersion = new OrderDeliveryDetail();
        adminCheckOrderTimelineDatesSecondVersion.versionNo = 2;
        adminCheckOrderTimelineDatesSecondVersion.createdTime = Instant.parse("2026-11-19T09:00:00Z");
        AdminViewOrderPickingTask adminCheckOrderTimelineDatesTask =
                new AdminViewOrderPickingTask(20L, "PENDING", null, null);

        List<AdminViewOrderTimelineRow> adminCheckOrderTimelineDatesRowList = adminBuildOrderTimeline(
                adminCheckOrderTimelineDatesOrder, "test_client",
                List.of(adminCheckOrderTimelineDatesSecondVersion, adminCheckOrderTimelineDatesFirstVersion),
                List.of(), List.of(), adminCheckOrderTimelineDatesTask, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());

        assertEquals(List.of(
                new AdminViewOrderTimelineRow(adminCheckOrderTimelineDatesOrder.createdTime,
                        "ORDER_CREATED", "Order created by client test_client.", null),
                new AdminViewOrderTimelineRow(adminCheckOrderTimelineDatesFirstVersion.createdTime,
                        "DELIVERY_DETAILS_SAVED", "Delivery details version 1 saved.", null),
                new AdminViewOrderTimelineRow(adminCheckOrderTimelineDatesSecondVersion.createdTime,
                        "DELIVERY_DETAILS_SAVED", "Delivery details version 2 saved.", null)),
                adminCheckOrderTimelineDatesRowList);
    }

    /**
     * What it does: Checks the outcome and failure reason used in a finished attempt entry.
     * Input: A failed synthetic delivery attempt with an internal note.
     * Output: Public outcome and reason text without the internal note.
     */
    @Test
    void adminCheckOrderTimelineFailure() {
        CustomerOrder adminCheckOrderTimelineFailureOrder = new CustomerOrder();
        adminCheckOrderTimelineFailureOrder.createdTime = Instant.parse("2026-11-18T07:00:00Z");
        AdminViewOrderDeliveryAttemptRow adminCheckOrderTimelineFailureAttempt = new AdminViewOrderDeliveryAttemptRow(
                1L, 2L, "test_driver", Instant.parse("2026-11-18T09:00:00Z"),
                Instant.parse("2026-11-18T09:15:00Z"), SHARED_ATTEMPT_OUTCOME_FAILED,
                SHARED_FAILURE_REASON_CLIENT_NOT_HOME, "Internal note", 0, 0);

        List<AdminViewOrderTimelineRow> adminCheckOrderTimelineFailureRowList = adminBuildOrderTimeline(
                adminCheckOrderTimelineFailureOrder, "test_client", List.of(), List.of(), List.of(), null, List.of(),
                List.of(), List.of(adminCheckOrderTimelineFailureAttempt), List.of(), List.of(), List.of());

        assertEquals(new AdminViewOrderTimelineRow(adminCheckOrderTimelineFailureAttempt.endTime(),
                "ATTEMPT_ENDED", "Delivery attempt ended: Failed. Reason: Client not home.", null),
                adminCheckOrderTimelineFailureRowList.getLast());
    }
}
