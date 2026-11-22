package com.lms.backend.delivery.workspace;

import static com.lms.backend.delivery.support.DeliveryValues.fields;

import java.util.List;
import java.util.Map;

final class DeliveryDecisions {
    private static final String LOCAL = "LOCAL_IMPLEMENTATION";
    private static final String PARTIAL = "PARTIAL_PLACE_HOLDER";
    private static final String PENDING = "PLACE_HOLDER";

    private DeliveryDecisions() {}

    static List<Map<String, Object>> items(boolean full) {
        return List.of(
                decision("U-01", "Deployment and recovery", PENDING,
                        "Cloud hosting and automated backups are team integrations."),
                decision("U-02", "Repeatable acceptance tests", PARTIAL,
                        "Run the flows with local test data. Team acceptance and load tests are pending."),
                decision("U-03", "API contracts and automated checks", PARTIAL,
                        "The shared login and Delivery endpoints can be reviewed and tested together."),
                decision("U-04", "Presentation evidence", PENDING,
                        "Use the local app and its test results for the team demonstration."),
                decision("U-05", "Shared application and database", LOCAL,
                        "Java 21 and Spring Boot serve HTML, CSS and JavaScript. Delivery uses shared PostgreSQL."),
                decision("U-06", "Return discrepancies and unavailable stock", full ? LOCAL : PENDING,
                        "The full version records actual returned quantities and excludes damaged stock from sale."),
                decision("U-07", "Re-verification after a stock issue", PENDING,
                        "Place holder: Warehouse provides packing revisions and clearance after repair."),
                decision("U-08", "Versioned client instructions", full ? PARTIAL : PENDING,
                        "The full version tests instructions tied to a return. The Client integration is pending."),
                decision("U-09", "Private proof and retention", full ? PARTIAL : PENDING,
                        "The full version keeps proofs in private local storage. Cloud storage is a Place holder."),
                decision("U-10", "Assigned lists and supervised recovery", full ? LOCAL : PENDING,
                        "The full version assigns each list to one worker, who confirms it at the warehouse."),
                decision("U-11", "Access and physical custody", PARTIAL,
                        "Shared roles protect Delivery. A change in responsibility alone does not move stock."),
                decision("U-12", "Review and response reminders", full ? PARTIAL : PENDING,
                        "The full version flags reviews. Simulated approvals apply only to marked fixtures."),
                decision("U-13", "Versioned delivery details", full ? PARTIAL : PENDING,
                        "The full version saves the address version used. The Client address book is a Place holder."),
                decision("U-14", "Delivery dashboard and filters", LOCAL,
                        "The dashboard and history show each worker's authorized records."),
                decision("U-15", "Refund outcome", PENDING,
                        "Place holder: Admin and the payment owner confirm the provider's refund result."),
                decision("U-16", "Payment eligibility", full ? PARTIAL : PENDING,
                        "The full version checks the shared payment status before pickup. Delivery does not collect card data.")
        );
    }

    private static Map<String, Object> decision(String id, String choice, String status, String reason) {
        return fields("id", id, "plan", "B", "choice", choice, "status", status, "reason", reason);
    }
}
