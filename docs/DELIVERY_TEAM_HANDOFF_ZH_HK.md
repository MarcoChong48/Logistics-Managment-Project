# Delivery 跨組員整合交接

**適用對象**　Warehouse、Client、Admin、Database、付款及測試組員，以及協助各角色開發的 AI。

**對照基準**　v4.0 小組合併版（`feature/delivery-v4-integration`）以 Admin Stage 6 的 `01fba08` 為基準。完整示範版另見 `feature/delivery-v4-demo`。GitHub 上的 `v3.2` 是先前的獨立 Delivery App。接手時請以你手上的分支及 commit 核對本文件提到的功能，並在回覆中列明已有實作及需要調整的接口。

## 1. Delivery 已交付的改動

- Delivery 已放入同一個 Java 21／Spring Boot 4.1.2 程式。前端使用 HTML、CSS 和原生 JavaScript。外送員沿用共用登入、角色權限、改密碼及閒置倒數。
- 修改既有程式的一行，將 `DELIVERY_WORKER` 的登入首頁由 `/shared/shared-welcome.html` 改為 `/delivery/index.html`，並更新兩個共用測試。Admin 專屬 Java 和網頁檔案的直接修改為零。 [共用首頁規則](../backend/src/main/java/com/lms/backend/shared/login/SharedStartPageRule.java) · [首頁規則測試](../backend/src/test/java/com/lms/backend/SharedStartPageRuleCheckTest.java) · [角色流程測試](../backend/src/test/java/com/lms/backend/SharedRoleProtectionFlowCheckTest.java)
- v4.0 小組合併版可執行自動分派、清單確認、逐件提貨、簽收、派送失敗及申報退件。Admin 與 Delivery 共用訂單、庫存及包裹資料。 [啟動及功能說明](DELIVERY_INTEGRATION_ZH_HK.md)
- 小組合併版透過 Admin 原有的不可售庫存介面提供破損退件數量，供 Admin 計算可售庫存。 [庫存接駁](../backend/src/main/java/com/lms/backend/delivery/support/DeliveryUnavailableStockReader.java)
- Warehouse 收貨、Client 退件指示和 Admin 批准由正式模組接駁。獨立示範分支提供這些交接的模擬測試。示範取消會建立 `REQUESTED` 退款紀錄，代表已提出退款申請。

### 現有 Delivery 接口

| 操作 | 現有接口與用途 |
| --- | --- |
| 讀取工作區 | `GET /api/delivery/workspace` 讀取登入外送員的清單、任務、派送紀錄和通知。 |
| 確認清單 | `POST /api/delivery/pickup-lists/claim` 接收 `qrCode`。外送員確認已分配給自己的清單。 |
| 逐件提貨 | `POST /api/delivery/pickup-lists/{listId}/collect` 接收 `packageQrCode`，成功後記錄 `PICKED_UP`，並將該訂單設為 `SHIPPED`。 |
| 申報退件 | `POST /api/delivery/packages/{id}/return` 接收 `reason`，建立待 Warehouse 收貨紀錄。實際收貨由 Warehouse 的正式功能接續。 |

表內寫入接口適用於啟用 `delivery-full` 的版本，並須傳入 UUID `requestId`。各角色頁面呼叫本角色的 API，跨角色交接由後端服務處理。操作人由共用登入取得，再核對該人對指定訂單或包裹的權限。 [提貨接口](../backend/src/main/java/com/lms/backend/delivery/pickup/DeliveryPickupController.java) · [退件申報接口](../backend/src/main/java/com/lms/backend/delivery/returns/DeliveryReturnController.java)

## 2. 需要各組員完成的工作

### Warehouse

1. **包裝完成和建清單。** 提供已包裝訂單的 `orderId`、`packageId`、包裹 QR 原始字串、商品和數量，以及使訂單進入 `READY_FOR_PICKUP` 的程式位置。Warehouse 選擇同區的合資格包裹，由 Delivery 自動分派外送員。請提供地區欄位或分組規則、每張清單的容量規則，以及一張可實際掃描的包裹和清單標籤。若地區亦限制哪些外送員可以接單，請與 Admin 一併定義。
2. **正式退件收貨。** 以 `returnId`、`packageId` 及原提貨事件識別這次交接。逐項記錄實收數量、破損或缺失數量、原因、收貨人及時間。收貨確認後才更新保管、庫存及 `RETURN_RECEIVED` 事件。示範收貨採用整單數量及整件完好／破損，正式接口需要涵蓋部分退回或混合狀況。
3. **例外處理。** 提供無法提貨的處理方式、逾時清單的在庫確認、破損貨品隔離及重新驗貨後解除暫停的規則。請界定 Warehouse 確認和 Admin 批准各自負責的步驟，並保留已提走包裹原有的保管紀錄。

**接駁方法**　Warehouse 後端可呼叫現有的 `DeliveryPickupService.createList(warehouseUserId, packageIds, null)`。現有方法回傳清單 ID，標籤使用已保存的 `pickup_list.list_qr_code`。Warehouse 可建立自己的建單及標籤頁面。目前分派依啟用外送員未結束的清單數量排序，同分按使用者 ID 排序。 [清單及分派服務](../backend/src/main/java/com/lms/backend/delivery/pickup/DeliveryPickupService.java)

### Client

1. **訂單及版本化地址。** 提供正式下單及追蹤頁的程式位置、訂單與客戶 ID、收件人、電話、地址和地址版本。每次適用於現有訂單的有效更改建立新的 `order_delivery_detail` 紀錄。舊派送紀錄保留當時使用的版本。
2. **提交及更新退件指示。** 由已登入的訂單擁有人提交 `REDELIVER` 或 `CANCEL`，並連到該訂單的 `RETURN_RECEIVED` 事件。重新派送同時提供該訂單的 `delivery_detail_id`。請確認何時可修改地址、替換或撤回待審指示，以及 Admin 已批准後如何處理後續要求。現有示範以 `SUPERSEDED` 結束舊的待審指示，正式狀態名稱須與 Admin、Database 對齊。
3. **追蹤及通知。** 確認客戶看到的狀態、失敗原因、通知及簽收證明。若客戶可看證明，Client 後端需驗證訂單擁有人，再透過受保護的後端取用。現有 Delivery 的證明讀取適用於正處理該派送的外送員。

**接駁方法**　Client 後端將指示寫入共用 `client_instruction`，以 `PENDING` 交給 Admin。追蹤頁讀取同一筆訂單的共用資料。共用指示欄位可區分 `TRACKING_PAGE` 及 `ADMIN_MANUAL`，如採用人工客服代錄，請先對齊核實方式及來源紀錄。 [共用表](../database/01_create_tables.sql) · [Admin 補充欄位](../database/03_admin_changes.sql)

### Admin

1. **正式審批。** 提供 Delivery cases 的分支、審批方法及可用狀態。審批應核對最新的一筆已收退件、仍有效的 Client 指示、相應地址版本及包裹狀況，並保存操作人、時間、決定及原因。待處理的取消指示會阻止批准重新派送。
2. **改派及例外處理。** 確認外送員停用、清單逾時、重複失敗或客戶未回覆時，由誰跟進、取得在庫確認及批准解除。改派寫入工作責任歷史，實物保管人由確認退回及下一次實際提貨決定。
3. **取消與退款。** 與付款負責人確認共用取消及退款接口、批准條件、退款金額和回寫責任。現有 Admin 取消與退款接口可供評估，以便短缺取消及退件取消使用同一套服務。

**接駁方法**　Admin 後端在同一交易核對當前指示及退件，批准重新派送時保存已核准的地址版本，將訂單設為 `READY_FOR_REDELIVERY`，再建立新的清單。取消則更新訂單、釋放適用的預留及記錄退款請求。可沿用 Stage 6 的共用登入、帳戶管理及訂單時間線。 [取消接口](../backend/src/main/java/com/lms/backend/admin/placeholder/AdminOrderCancellationPlaceholder.java) · [退款接口](../backend/src/main/java/com/lms/backend/admin/placeholder/AdminRefundPlaceholder.java)

### Database

1. 提供最終 schema、遷移次序、各表的負責人、主外鍵及共用狀態字典。確認現行「一張訂單一個包裹」是否維持，以及退件與再次提貨如何保留各次事件。
2. 審閱本機 `07_delivery_changes.sql` 的分派欄位、退件明細、不可售庫存、防重複提交及派送摘要。現有本機次序為 `01_create_tables.sql` → `03_admin_changes.sql` → `07_delivery_changes.sql`。
3. 統一實物、預留及不可售庫存的作用與寫入規則。確認 Supabase 測試資料庫的 schema、連線方式、遷移負責人及測試資料。連線憑證由環境負責人透過小組的私密設定方式提供。

**接駁方法**　各角色後端使用同一套 PostgreSQL 資料。提貨、實收和取消各由一個交易完成其相關更新，並同步保存庫存變動和操作紀錄。現有 Delivery 寫入使用 UUID `requestId` 防止重複作用；請讓正式交接採用可重試的共用規則。 [Delivery 遷移](../database/07_delivery_changes.sql) · [寫入防重複處理](../backend/src/main/java/com/lms/backend/delivery/support/DeliveryCommands.java)

### 付款、通知與部署

- **付款及退款負責人**　提供付款及退款的狀態對照、可靠的付款確認、重複或遲到結果的處理方式及測試資料。現有提貨檢查要求付款為 `ACCEPTED`、有 `accepted_at`，並且每項貨品的 `HELD` 預留量與該付款和訂購數量相符。建議由付款模組核實結果及回寫同一筆 `refund`。追蹤頁按實際退款結果顯示進度。
- **通知負責人**　與 Client、Warehouse、Admin 確認提貨、派送結果、退件、待回覆及審批分別通知誰。建議沿用共用 `notification` 表及各角色的已讀狀態。現有本機的 30 分鐘清單逾時、兩次派送失敗及退件後第 3／7 日是審查提示。實際通知、跟進及結案權限由小組確認。
- **部署負責人**　確認測試與正式設定、簽收證明的私人持久儲存、備份和清理。小組合併版使用私人 Docker 資料卷。正式設定需停用示範功能，並保留按身分、訂單及派送階段驗證的取用規則。

## 3. 接駁次序與驗收

1. **先完成正常派送。** Client 下單及確認付款 → Warehouse 包裝與成單 → Delivery 清單確認與逐件提貨 → 簽收。驗收兩名啟用外送員在無未結束清單時能收到三張清單的 2／1 分派。驗收確認清單時庫存和保管人維持原值，逐件提貨後 Admin 可以讀取同一筆已更新的訂單。
2. **再完成重新派送。** Delivery 記錄派送失敗並申報退件 → Warehouse 確認實收 → Client 提交指示及地址版本 → Admin 批准 → 新清單及再次實際提貨。`AWAITING_WAREHOUSE` 是退件紀錄狀態，`AWAITING_CLIENT_INSTRUCTION` 和 `AWAITING_ADMIN_APPROVAL` 是 Delivery 顯示的跟進狀態。共用訂單依業務進度使用 `DELIVERY_FAILED`、`READY_FOR_REDELIVERY` 等訂單狀態。
3. **最後驗收例外。** 包括破損或缺件、客戶撤回指示、取消及退款待處理、錯誤 QR、錯誤角色、重複提交及逾時清單。驗收破損商品保留實收紀錄並排除於可售庫存，退款待處理時訂單維持取消，重複提交只造成一次庫存或退款變更。

測試組請提供一套可重做的合成訂單、測試角色、已核實付款和可列印的 QR，並回報預期結果及實測結果。實體相機、正式 Supabase 環境及由各角色實際執行的交接仍待驗收。 [本機啟動及合併步驟](DELIVERY_INTEGRATION_ZH_HK.md)

## 4. 請各組員按此格式回覆

```text
負責角色與功能：
分支、commit 及主要檔案：
已完成／尚待處理的步驟：
可呼叫的 Java 方法或 API、登入角色：
輸入及輸出範例、涉及的資料表和狀態：
可重做的測試資料及預期結果：
需要 Delivery 或其他組員確認的事項：
```

如使用 AI 協助，請一併提供這份文件、你的模組程式及共用 SQL。請 AI 先找出已存在的接口和資料表，再逐項回覆上述欄位，將現有實作、建議改動及仍需小組決定的事項標明。 [小組程式位置、共用登入與角色接口](../TEAM_GUIDE.md)
