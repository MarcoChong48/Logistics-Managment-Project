# Delivery verification

## Source reorganization checks on 9 November 2026

The repository layout was verified with a fresh React production build and a new Spring Boot JAR. All 10 existing backend behavior tests and the new Java package boundary test passed. The Vite build also checked the frontend imports.

The new JAR passed all 19 HTTP API checks against isolated demo data. Chrome desktop and mobile viewport checks passed. On a separate fresh demo store, the browser completed login, list claim, package collection, delivery start, signature upload, delivery confirmation and read-only history. The restart and backup evidence below dates from the earlier package.

## Baseline evidence

The reports below record checks against the **5 November 2026 runnable package**. The repository carries those reports as baseline evidence. Build and run the checks against the current checkout to verify the reorganized source.

| Evidence | Environment and result |
|---|---|
| [Backend test report](../qa/evidence/backend-tests.txt) | `DeliveryServiceTest`: 10 Spring Boot/JUnit tests, zero failures/errors/skips. In-memory H2 plus local private proof fixtures; includes direct stock and transaction checks. |
| [HTTP API report](../qa/evidence/api-result.json) | 19 checks passed, zero failed, against the packaged server at `http://127.0.0.1:8080` and fresh `qa/.data/api`. The run lasted 18:42:37–18:42:42 Hong Kong time. |
| [Browser report](../qa/evidence/browser-result.json) | Headless Chrome at 1440×1000 and mobile emulation at 390×844: login and Place holder notice passed, no horizontal overflow and no page errors. |
| [UI workflow report](../qa/evidence/ui-flow-result.json) | Actual interface login → list claim → individual pickup → Start Delivery → drawn/uploaded signature → delivered confirmation → read-only history passed; no page errors. |
| [Restart report](../qa/evidence/persistence-result.json) | Selected IDs/statuses for 3 packages, 3 attempts and 2 returns matched before and after a real server restart. This is persistence evidence, not a backup/restore test. |
| [Offline backup report](../qa/evidence/backup-result.json) | Database and proof backup restored into a new folder; both compared file hashes matched. This does not establish application startup/semantic checks from the restored copy or the 15-minute recovery objective. |

Warehouse, Client and Admin actions used the labelled **Place holder** sandbox. Camera/physical QR hardware, a real phone camera, the full U-02 workload/24-hour observation, other teams' live modules, cloud services and external payment/refund integration were not tested. Mobile viewport emulation does not establish physical-device compatibility.

## Scenario checklist

| ID | Action | Required observation | Result |
|---|---|---|---|
| D01 | Log in as Ken, then Mei in separate sessions. | Separate authenticated Delivery identities; invalid credentials fail; mutation without CSRF is rejected. | API/JUnit passed; browser login passed |
| D02 | Both workers try to claim the same open list. | Exactly one collector; claim changes no custody or stock. | API/JUnit passed, including direct claim/stock and concurrency checks |
| D03 | Scan a valid package, then repeat the same request and then use another worker. | One pickup effect; duplicate is safe; other worker is denied. | API/JUnit passed, including exact physical/reserved stock and movement counts |
| D04 | Scan an unknown QR, a wrong-list package or a released list entry. | Clear error, no state or stock change. | API unknown/wrong-list and JUnit stale-list rejection passed; JUnit also checks rollback on later item failure |
| D05 | Mark Vehicle full or damaged packaging before pickup, then close the list. | Reasons retained; only eligible uncollected items released; damage held; no customer attempt created. | Partial: supervised release preserves collected custody/stock and rejects stale scans; individual cannot-collect reasons and normal close not exercised |
| D06 | Start one of several collected packages. | Only that package becomes OUT_FOR_DELIVERY and receives a new attempt. | API start/retry/one-attempt checks passed; several-collected-packages case pending |
| D07 | Complete delivery without proof, with another attempt's proof, then with valid own proof. | Invalid requests fail; valid signature or photo completes exactly once. | API PHOTO/missing/cross-attempt/retry passed; JUnit SIGNATURE passed; actual UI drawing/upload/completion passed |
| D08 | Upload invalid bytes, excessive size or unsupported image content. | Backend rejects invalid proof; no public file access or false delivery completion. | API invalid bytes/private retrieval and JUnit blank-signature rejection passed; oversized/other-format cases pending |
| D09 | Record Other failure without a note, then valid failure. | Note validation; separate failed attempt; holder retained; no stock restoration. | API/JUnit passed, including unchanged physical stock before receipt and safe notification text |
| D10 | Request return before visiting the customer. | Pending handoff is saved without a fake failed attempt or stock restoration. | API no-attempt/custody passed; JUnit verifies second legal pickup cycle and stock after re-collection |
| D11 | Use the Warehouse Place holder to confirm an intact allocated return. | Receipt provenance; physical/reserved restored once; Delivery contact/proof access revoked. | API repeat-safe receipt/custody/contact and JUnit physical/reserved restoration passed; explicit proof-after-return case pending |
| D12 | Confirm a damaged return in the sandbox. | Damaged goods unavailable/held; no automatic redelivery or sellable-stock increase. | JUnit passed: unavailable count, redelivery hold and cancellation preserve unavailable stock |
| D13 | Try Admin redelivery approval without return or instruction. | Rejected; no new pickup eligibility. | API passed |
| D14 | After receipt, submit a current Client instruction and Admin approval, then collect again. | New list/cycle and accepted pickup; prior attempt and custody history remain. | API/JUnit new pickup, separate attempts and stock re-deduction passed; full raw custody-ledger audit remains a further check |
| D15 | Submit cancellation, try redelivery approval and finally approve cancellation. | Pending cancel blocks redelivery; final CANCELLED cannot restart; refund remains a Place holder. | API passed; external refund not tested |
| D16 | Reuse a request key with different data. | Conflict/error; no second effect. | API passed |
| D17 | Restart after saved Delivery activity. | Confirmed work persists; seed data does not overwrite it. | Restart report passed for selected state of 3 packages, 3 attempts and 2 returns; interrupted active-attempt restart not separately exercised |
| D18 | View another worker's task/proof or completed/received package contacts. | Backend withholds private fields/files. | API/JUnit passed; JUnit also denies a deactivated account's existing session; UI history is metadata-only |
| D19 | Review a stale claim and aged failed-return case. | 30-minute/two-failure/three-day/seven-day flags prompt review only; no automatic custody/cancellation. | Partial: JUnit 31-minute stale flag and supervised release passed; aged reminder/final-review timing not exercised |
| D20 | Run retention/backup checks in an isolated test store. | Detailed proof/attempt cleanup and permitted summary/orphan handling agree; restored database/proof checkpoint is consistent. | Partial: JUnit expiry records/minimal summary and offline backup/restore two-file hashes passed; orphan/file expiry and restored-app semantic/timed recovery checks pending |

## Scope of evidence

The ten backend tests cover real Spring services/transactions with in-memory H2, a private image fixture and a MockMvc security check. They assert stock quantities, movement counts, rollback, claim races, damaged-goods handling, instruction supersession and one-year detailed-record expiry. They do not establish behavior of a future shared database product or cloud storage.

Browser checks used installed Chrome in headless mode. Manual QR text entry and signature drawing passed in the UI workflow. Camera permission/device compatibility needs actual supported camera hardware; manual entry and phone-sized emulation do not prove camera operation.

The larger U-02 workload, first-order usability trial, external provider integration, shared-role acceptance, hosted uptime and cloud restore cannot be passed using only isolated Delivery fixtures. Record those as not run or external-owner work until the real environment is available.

## Recorded HTTP checks

All nineteen items below passed in the linked run. HTTP traces and timings are in the JSON report.

1. Anonymous workspace access and login CSRF enforcement.
2. Separate Ken/Mei sessions and mutation CSRF enforcement.
3. Fresh seeded packages and absence of contacts in pickup-list rows.
4. Start Delivery rejected before actual pickup.
5. Exclusive/repeat-safe claim, key-reuse conflict and unchanged custody.
6. Unknown/wrong-list QR rejection.
7. Repeat-safe individual collection and collector ownership.
8. One active attempt and missing-proof rejection.
9. Invalid image rejection, valid private PNG retrieval and other-worker denial.
10. Repeat-safe final delivery and revoked handler proof access.
11. Other failure note validation, separate attempt and retained holder.
12. Return report alone cannot authorize redelivery or Client instruction.
13. Repeat-safe simulated Warehouse receipt; Client instruction still required.
14. Current simulated Client/Admin approval; approval is not physical pickup.
15. New legitimate pickup and preserved first failed attempt; wrong-attempt proof rejected.
16. Pending cancellation blocks redelivery; approved cancellation cannot restart.
17. Pre-attempt return without a fake customer failure.
18. Concurrent replacement-list claims select exactly one collector.
19. Logout removes authenticated workspace access.

## Reproduce the checks

Run the backend tests from `features/delivery/backend` with `mvn test`. From `features/delivery`, `Build-Delivery.ps1` builds the interface, runs backend tests and packages the JAR. The backend test fixture resets only its in-memory H2 database. GitHub Actions reads its workflow from the repository root.

The QA scripts need Node.js, their locked dependencies (`npm --prefix qa ci`) and installed Chrome for browser/image checks. Run these commands from `features/delivery` with an isolated demo server running.

```powershell
$env:DELIVERY_TEST_URL = 'http://127.0.0.1:8080'
node qa/api-check.cjs
node qa/browser-check.cjs
```

The API suite changes all three seeded orders and requires a fresh dedicated database. The UI workflow also requires fresh seed data; run `node qa/ui-flow.cjs` against a separate fresh demo instance.

For persistence, run `node qa/persistence-check.cjs before`, stop and restart the actual server with the same data directory, then run `node qa/persistence-check.cjs`. Comparing two requests without a real restart is not the recorded persistence procedure.

The offline backup helper was exercised with the app stopped. Its ZIP was restored with `Restore-Delivery.ps1` into a new folder and database/proof hashes were compared. The helper never overwrites the working store. Validate business records after starting a restored copy before treating a recovery as complete.

Keep partial and not-run cases visible. The six evidence records describe different checks and must not be presented as a full end-to-end acceptance of the team's integrated LMS.
