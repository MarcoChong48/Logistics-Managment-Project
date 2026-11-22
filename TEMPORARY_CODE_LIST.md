# Temporary Admin code removal checklist

Search the backend source for `TEMPORARY TEST CODE` to find the marked code and its containing files below. Keep the test tools and flow checks while the team needs them. Apply this checklist at final hand-in.

## Test data and configuration

- [ ] Start from a fresh database volume, or run **Test tools → Stage 9 → Remove all test data** before removing the test tools. The tool clears operational tables and fixture accounts while keeping the signed-in Admin and non-test accounts.
- [ ] In `compose.yaml`, remove the Stage 0 backend setting `SPRING_PROFILES_ACTIVE: admintest`.
- [ ] In `compose.yaml`, remove the Stage 4 `flow-check` service declaration and its `check` profile, image build target, inherited environment, data source for `lms_flow_check`, flow-specific profile and reset overrides, schema and guide mounts, Maven and lock mounts, and service dependency. Remove the `&admin_backend_environment` anchor on `backend.environment` and the top-level `flow_check_lock` and `flow_check_maven` volume declarations. Keep the Admin application's environment settings and `postgres_data` volume.
- [ ] Remove the Stage 9 inventory mounts in that service. They bind `PLACEHOLDER_LIST.md`, `TEMPORARY_CODE_LIST.md`, `backend/Dockerfile` and `compose.yaml` for S9-T10 and S9-T11.
- [ ] In `compose.yaml`, replace the Stage 1 local bootstrap password with private configuration outside Git. Keep `LMS_BOOTSTRAP_ADMIN_USERNAME`, which has identified the super admin since Stage 4. Keep `LMS_BOOTSTRAP_ADMIN_RESET_ON_START` false and `LMS_SESSION_IDLE_TIMEOUT_MINUTES` at 15.

## Test mode and fixture tools

- [ ] Delete the Stage 0–9 package `backend/src/main/java/com/lms/backend/admin/temporarytest/`. This removes `AdminTemporaryTestToolController.java`, `AdminTemporaryTestToolService.java`, their profile, all routes and functions below, the request and response files, and every private fixture or verification helper.
- [ ] Delete `backend/src/main/resources/static/admin/admin-temporary-test-tools.html` and `backend/src/main/resources/static/admin/admin-temporary-test-tools.js`, including the Stage 0 startup mode request, Stage 1–9 cards and same-named handlers, input help and result lines.
- [ ] In `backend/src/main/resources/static/admin/admin-common.js`, remove the Stage 0 Testing group, Test tools entry and Test mode flag in `adminBuildPageFrame`, together with the Stage 1 session-field lookup for those controls. Keep ordinary Admin links and work counters.
- [ ] In `backend/src/main/java/com/lms/backend/shared/health/SharedHealthService.java` and `backend/src/main/java/com/lms/backend/shared/health/SharedCheckSystemHealthResponse.java`, remove the Stage 0 `admintest` lookup and `sharedCheckSystemHealthTestModeIsEnabled` field. Keep the health check, database count and server time.
- [ ] In `backend/src/main/java/com/lms/backend/shared/login/SharedLoginService.java` and `backend/src/main/java/com/lms/backend/shared/login/SharedViewSessionResponse.java`, remove the Stage 1 `admintest` lookup and the `sharedViewSessionTestModeIsEnabled` field. Keep the shared login and session guards.
- [ ] In `backend/src/main/java/com/lms/backend/admin/placeholder/AdminRefundTemporary.java`, remove the Stage 8 `adminTemporarySetProviderAnswer` hook and its in-memory override map when removing test tooling. Keep the normal provider fallback until Payment connects the real implementation.

The routes below are in `AdminTemporaryTestToolController.java`. The same functions in `AdminTemporaryTestToolService.java` implement them. The Java records are in the same temporary package.

| Stage | Route | Function | Temporary record file |
| --- | --- | --- | --- |
| 0 | `GET /api/admin/temporary-test/mode` | `adminTemporaryViewTestMode` | `AdminTemporaryViewTestModeResponse.java` |
| 1 | `POST /api/admin/temporary-test/test-user` | `adminTemporaryCreateTestUser` | `AdminTemporaryCreateTestUserRequest.java` |
| 1 | `POST /api/admin/temporary-test/end-lock` | `adminTemporaryEndUserLock` | `AdminTemporaryEndUserLockRequest.java` |
| 1 | `POST /api/admin/temporary-test/expire-temporary-password` | `adminTemporaryExpireTemporaryPassword` | `AdminTemporaryExpireTemporaryPasswordRequest.java` |
| 1 | `POST /api/admin/temporary-test/user-status` | `adminTemporarySetUserStatus` | `AdminTemporarySetUserStatusRequest.java` |
| 2 | `POST /api/admin/temporary-test/many-test-users` | `adminTemporaryCreateManyTestUser` | — |
| 2 | `POST /api/admin/temporary-test/worker-with-active-work` | `adminTemporaryCreateWorkerWithActiveWork` | — |
| 3 | `POST /api/admin/temporary-test/picking-scan-rows` | `adminTemporaryCreatePickingScanRow` | — |
| 3 | `POST /api/admin/temporary-test/old-log-rows` | `adminTemporaryCreateOldLogRow` | — |
| 3 | `POST /api/admin/temporary-test/run-log-cleanup` | `adminTemporaryRunLogCleanup` | — |
| 3 | `POST /api/admin/temporary-test/private-value-log-rows` | `adminTemporaryCreatePrivateValueLogRow` | — |
| 4 | `POST /api/admin/temporary-test/product-stock` | `adminTemporarySetProductStock` | `AdminTemporarySetProductStockRequest.java` |
| 5 | `POST /api/admin/temporary-test/sample-order-set` | `adminTemporaryCreateSampleOrderSet` | — |
| 6 | `POST /api/admin/temporary-test/shortage-report` | `adminTemporaryCreateShortageReport` | `AdminTemporaryCreateShortageReportRequest.java` |
| 6 | `POST /api/admin/temporary-test/shortage-resolved` | `adminTemporaryResolveShortageCase` | `AdminTemporaryResolveShortageCaseRequest.java` |
| 6 | `POST /api/admin/temporary-test/picking-order` | `adminTemporaryCreatePickingOrder` | `AdminTemporaryCreatePickingOrderRequest.java` |
| 7 | `POST /api/admin/temporary-test/failed-delivery-case` | `adminTemporaryCreateFailedDeliveryCase` | `AdminTemporaryCreateFailedDeliveryCaseRequest.java` |
| 7 | `POST /api/admin/temporary-test/held-package` | `adminTemporaryCreateHeldPackage` | `AdminTemporaryCreateHeldPackageRequest.java` |
| 8 | `POST /api/admin/temporary-test/refund-case` | `adminTemporaryCreateRefundCase` | `AdminTemporaryCreateRefundCaseRequest.java` |
| 8 | `POST /api/admin/temporary-test/provider-answer` | `adminTemporarySetProviderAnswer` | `AdminTemporarySetProviderAnswerRequest.java` |
| 9 | `POST /api/admin/temporary-test/privacy-check` | `adminTemporaryRunPrivacyCheck` | — |
| 9 | `POST /api/admin/temporary-test/remove-all-test-data` | `adminTemporaryRemoveAllTestData` | `AdminTemporaryRemoveAllTestDataRequest.java` |

Test-only helper functions in `AdminTemporaryTestToolService.java` go with the package:

| Stage | Helper functions and changes |
| --- | --- |
| 1 | `adminTemporaryFindTestUser`. |
| 2 | `adminTemporaryCreateTestUser` insert overload and `adminTemporaryCreateSampleOrder` fixture builder through `SHIPPED`. |
| 3 | `adminTemporaryCreateOldLogRow` fixture-writing overload and the profile-only call to retention cleanup. The ordinary scheduled retention stays. |
| 4 | Test-tool protections for the super admin in `adminTemporarySetUserStatus` and `adminTemporaryExpireTemporaryPassword`. The live protections stay. |
| 5 | `adminTemporaryCreateSampleOrder` additions, `adminTemporaryCreateSampleOrderRefund`, `adminTemporaryCreateSampleDeliveryAttempt` and `adminTemporaryCreateSampleRedelivery`. |
| 7 | `adminTemporaryCheckFailedDeliveryCaseInput`, the additional `adminTemporaryCreateSampleOrder` overload, the `adminTemporaryCreateFailedDeliveryCase` helper overload, and `adminTemporaryConfirmSampleReturn`. |
| 8 | `adminTemporaryCheckRefundCaseAge`. |
| 9 | `adminTemporaryCheckPrivacyAnswer`, the privacy marker list and the cleanup SQL in `adminTemporaryRemoveAllTestData`. |

## Flow checks

- [ ] Remove the Stage 4 `flow-check` build stage in `backend/Dockerfile` and its ordered commands for Stages 0–9. Keep the `rule-check` stage and the `*RuleCheckTest.java` files.
- [ ] Remove the files below from `backend/src/test/java/com/lms/backend/`. Each entry includes all its marked test methods and helpers.

| Added in stage | Cases from stage | Source file | Additional removal detail |
| --- | --- | --- | --- |
| 4 | 0 | `SharedSetupFlowCheckTest.java` | Includes the base class `SharedFlowCheck` and its fixture, schema, session and case-reporting helpers. |
| 4 | 1 | `SharedLoginFlowCheckTest.java` | — |
| 4 | 2 | `AdminUserFlowCheckTest.java` | — |
| 4 | 3 | `AdminLogFlowCheckTest.java` | — |
| 4 | 3 | `SharedChangePasswordFlowCheckTest.java` | — |
| 4 | 3 | `SharedRoleProtectionFlowCheckTest.java` | — |
| 4 | 4 | `AdminProductFlowCheckTest.java` | — |
| 4 | 4 | `AdminSuperAdminFlowCheckTest.java` | — |
| 5 | 5 | `AdminOrderFlowCheckTest.java` | — |
| 6 | 6 | `AdminWarehouseCaseFlowCheckTest.java` | Includes the two S6 regressions added in Stage 7. |
| 7 | 7 | `AdminDeliveryCaseFlowCheckTest.java` | — |
| 8 | 8 | `AdminRefundFlowCheckTest.java` | — |
| 9 | 9 | `AdminDashboardFlowCheckTest.java` | — |

## Integration stand-ins

- [ ] Replace the stand-ins in `backend/src/main/java/com/lms/backend/admin/placeholder/` as the named owners connect the real implementations. `AdminUnavailableStockTemporary.java` is from Stage 4, `AdminOrderCancellationTemporary.java` is from Stage 6, and `AdminRefundTemporary.java` is from Stage 6 with a Stage 8 extension. These stand-ins support normal Admin functions until the real equivalents are ready. The Warehouse input functions in `AdminShortageInputPlaceholder.java` stay as the integration entry points. [Placeholder register](PLACEHOLDER_LIST.md)
