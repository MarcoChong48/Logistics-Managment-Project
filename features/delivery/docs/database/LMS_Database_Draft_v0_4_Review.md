# LMS Database Draft v0.4 審閱

日期：2026 年 11 月 4 日（香港時間）

## 審閱範圍與結論

來源：

- [Database Draft v0.4](v0.4/LMS_Database_Draft_v0_4.md)：25 張資料表的用途和主要欄位，明確標示尚未獲團隊批准。
- [Overview ERD](v0.4/LMS_ERD_v0_4_Overview.png)。
- [Detailed ERD](v0.4/LMS_ERD_v0_4_Detailed.png)。
- [目前專案需求](../project/PROJECT_DETAILS.md)：以已確認的業務規則為比較基準；U 項目的建議方案仍未獲採納。

草稿已涵蓋大部分核心領域，可作下一版設計的基礎。主要問題是部分 ERD 線端畫反，以及若干已確認流程尚未有足夠明確的資料和一致性規則。這是文件／設計審閱；沒有 SQL、執行中的資料庫或應用程式可供測試，以下並非已執行的失敗測試。

附件只有兩張 ERD 和資料表說明。ERD 表達資料關係，未提供獨立的 workflow／activity／state 圖；本審閱只能對照現有需求檢查流程支援程度。

以下新增表名／欄名均是可討論的最小設計選項，不代表必須照搬或已經批准。

## 1. 可以保留的設計

- `User` 共用登入資料，`ClientProfile` 保存將來訂單可用的預設資料。
- `OrderItem.unitPrice` 保存下單單價，與 `Product.currentPrice` 分開。
- `OrderDeliveryDetail` 有版本號，`DeliveryAttempt` 指向實際使用的地址版本。
- Order、Payment、Refund 分開，避免把退款進度當物流狀態。
- `Package.orderID UNIQUE`、`PickingTask.orderID UNIQUE` 表達每張訂單最多一個包裹和一個執貨工作；早期訂單可以尚未建立它們。
- PickupList 和逐件 PickupListItem 分開；claim 清單與實際領件可以分別記錄。
- 配送嘗試、證明、保管事件、執貨指派、掃碼紀錄及審計有獨立歷史。

## 2. 確實需要修正的 ERD 標示

箭頭下表只表示「父記錄 → 子記錄」，不是操作流程。Crow's foot 應在代表多筆記錄的一端。

| 關係 | Overview | Detailed | 應表達的關係 |
|---|---|---|---|
| Order → OrderDeliveryDetail | 多端反轉 | 多端反轉 | 每個地址版本屬一張訂單；一張訂單可有多個版本 |
| Payment → Refund | 多端反轉 | 多端反轉 | 每筆退款記錄屬一筆 Payment；Payment 可以暫無退款或有多筆請求／嘗試記錄 |
| PickupList → PickupListItem | 多端反轉 | 多端反轉 | 每個 Item 屬一張 List；一張 List 有多個 Item |
| DeliveryAttempt → DeliveryProof | 多端反轉 | 多端反轉 | 每份 Proof 屬一次 Attempt；Attempt 可有零至多份 Proof，成功時至少一份 |
| Package → PackageCustodyEvent | 多端反轉 | 正確 | 每個 Event 屬一個 Package；Package 可有多個 Event |
| PickingTask → PickingAssignmentHistory | 多端反轉 | 正確 | 每筆 Assignment 屬一個 Task；Task 可有多次指派 |
| PickingTask → PickingScanHistory | 正確 | 多端反轉 | 每筆 Scan 屬一個 Task；Task 可有多筆 Scan |
| User → ClientProfile | 可選端反轉 | 可選端反轉 | 每個 Profile 必須屬一個 User；一般 User 可沒有 ClientProfile |

`DeliveryWorkerProfile` 的 User／Profile 方向正確；不用把所有 Profile 線一併倒轉。ClientProfile 的存在仍須與實際角色規則配合。

兩圖亦將部分可空 FK 畫成必須有父記錄。`AuditLog.actorUserID`、`SecurityLog.userID`、`Notification.orderID`、`StockReservation.paymentID` 均列為 NULL；從該子記錄看父記錄，應是 0..1。這與「一個 User 可以有很多 Logs」是不同端的限制。

Detailed 未畫出所有已列 FK，例如各 worker／actor、`Package.currentHandlerUserID`、`StockMovement.packageID`、`PackageCustodyEvent.pickupListID` 及 `PickingScanHistory.originalScanID` 自我關係。這些是圖示省略，不等於欄位沒有 FK；應補畫，或在圖上明示只展示主要關係。

## 3. 實作前應補清楚的資料與規則

### R01 錯誤掃碼可能無法保存

**來源：草稿第 23 行；需求第 8、12.2 節。**

`PickingScanHistory.orderItemID` 是必填 FK，但掃到不屬於訂單的商品，甚至不存在的 QR，可能根本沒有可填的 OrderItem。目前也沒有保存實際掃入值和拒絕原因的明確欄位。

例：訂單只有 SKU-A，工人掃入 SKU-B。不能虛構一筆 OrderItem，也不應把錯碼記成 A。

建議：拒絕掃碼時 matched order item 可空，保存 scanned identifier、可辨認時的 product、result 和 reason。若規則是一掃一件，寫明即可；若允許一次掃入多件，才需要明確數量。成功掃碼仍須匹配同一訂單內的正確項目。

### R02 執貨進度不應只依賴會到期刪除的歷史

**來源：第 21–23 行；需求第 8、12.2 節。**

目前沒有明確列出持久的逐項 picked quantity。若每次由 ScanHistory 計算，90 日清除歷史後，長期未完成工作可能失去已確認進度。

建議：保存不依賴過期詳細紀錄的當前逐項進度／已確認包裝數量；可放在工作明細或其他合適記錄，不指定一定新增哪張表。刪除掃碼歷史也要處理 reversal 自我 FK。

`originalScanID` 另須規定：只能反轉同一 task／item 的有效 accepted scan、只可反轉一次、不可令進度負數、保留原作者，而且 Confirm Packing 後不可走普通 reversal。必須明定目前有效 owner 的判斷方式，並防止同時出現兩位有效 owner。

### R03 付款紀錄未分清重試窗口與實際付款結果

**來源：第 14、19 行；需求第 7 節。**

Payment 表把一次 checkout/session 放在一筆記錄是可行方向，但 `paymentStatus / createdTime / endedTime` 的定義不足以分清：

- 某張卡被拒絕，但同一 checkout 仍可重試。
- 30 分鐘輸入／重試窗口已結束。
- Provider 的付款仍處理中或結果未知。
- LMS 首次驗證並接受成功付款的時間。

例：第 29 分鐘卡被拒絕，不能因此當整個 checkout 已失敗並釋放預留。又例如 provider 在訂單第 23:59 小時成功，但 LMS 到 24:01 才確認，依已確認需求要維持取消並退款；已及時接受成功的付款則不能被遲來的重複訊息改判過期。

建議：定義 checkout 是否仍開放重試、付款結果、窗口截止時間（可由開始時間計算）及持久的 LMS 首次接受時間。窗口截止受訂單原有 24 小時期限限制；不一定要因此拆成多張表。

### R04 並發及重複請求的限制尚未指定

**來源：第 13–15、19–20、25–28 行；需求第 5–10 節。**

普通 PK/FK 不會單獨保證一張訂單只有一個 active/unresolved checkout、取消與 Start Picking 不會同時成功，或重複領件請求不會再次扣庫存。

建議列明每個操作的保護條件及一次性效果：

- Pay 同時被按兩次仍只建立／恢復同一個有效 checkout。
- Provider reference、訊息或業務 operation identifier 有適當去重；只存 event ID 不足以處理不同訊息指向同一筆成功付款的情況。
- 預留／庫存／custody／list item outcome／order status 的同一次本地業務變更保持一致提交；外部付款服務不能假設與本地資料庫一起交易提交，須保存可恢復的處理結果。
- 退款 timeout 要先查清之前結果，不能直接再開一個外部退款。
- StockMovement 能連到某一次 pickup／return 業務事件；僅以 packageID 作一生一次去重，會錯誤阻止合法重送領件。

資料庫選定後再確定約束與交易方式。以 PostgreSQL 官方文件為例，FK 保證引用存在，UNIQUE 保證指定值組合唯一；跨列／跨表的其他規則仍須合適設計，不能全塞進普通 CHECK。[約束說明](https://www.postgresql.org/docs/current/ddl-constraints.html)及[列鎖定說明](https://www.postgresql.org/docs/current/explicit-locking.html)是技術參考，不代表選定 PostgreSQL。

### R05 Reservation 的領件、退回及再領件語意不完整

**來源：第 13–15 行；需求第 6.1、7.2、7.4 節。**

`StockReservation.status / releasedAt` 未說明「checkout 確認未付款結束而釋放」、「訂單取消釋放」、「領件消耗配置」的分別，也未明定完整回倉後如何恢復該訂單的配置。

建議：定義 held／released／consumed 等語意和對應時間／原因，以及退回重新配置的歷史表達。可以用多筆 allocation／事件或明確 ledger；不要反覆覆寫同一筆 reservation 而抹去中間歷史。

若 `Inventory.reservedStock` 是儲存總數，應指定它與有效配置明細的對帳規則及一致更新方式。不要直接加永遠 `reservedStock <= physicalStock` 的限制：真實盤點短缺可能低於原有承諾，必須保留真實盤點結果並進入已確認的缺貨處理。異常可售量公式仍屬 U-06。

### R06 回倉必須保存實收數量及狀況

**來源：第 15、27 行；需求第 6.3、8、9.3 節。**

`PackageCustodyEvent.details` 是泛用內容，草稿沒有定義如何逐項保存 Warehouse 確認收到的數量／狀況。僅有「returned」事件不能證明全部商品完好到齊。

例：訂單三隻杯，只收到兩隻，其中一隻損壞。不能直接按原 OrderItem 加回三隻正常可售貨。

建議：保存包裹、回倉／領件週期、收貨工人及時間，以及逐項實收數量與狀況。ReturnReceipt＋lines 是一種設計，結構化事件也可討論。回倉可以發生在客戶配送嘗試之前，因此不能強制每次 return 都有 DeliveryAttempt。

實際點收／檢查已是需求；損壞、不可售庫存與短缺的詳細計算及處置仍是 U-06，不能在此自行決定。

### R07 缺貨待處理與 Admin 決定未有明確保存方式

**來源：第 13、21、27、32 行；需求第 6.3、8.2、11.1 節。**

草稿未明確表達 Warehouse 報告哪張訂單／哪項商品短缺、哪些工作必須暫停、Admin 選擇等補貨或全單取消，以及決定者和時間。一般 AuditLog 可以記錄結果，但未定義成一個可跟進的待處理案件。

建議：加入結構化 issue／decision，或指定現有欄位／事件如何等價表達上述資料及 hold。Admin 決定業務後果，Warehouse 才做實物盤點／補貨調整。包裝後怎樣授權換同款貨及重新驗證仍屬 U-07；不用擅自新增替代商品或部分配送。

### R08 指派責任與實際保管位置需要分清

**來源：第 22、24–28 行；需求第 3、8.1、9、10.1 節。**

`Package.currentHandlerUserID = NULL` 可以被不同人理解成「在倉」、「已送達」或「未知」。`DeliveryAttempt.deliveryWorkerID` 則只能代表該次配送的人，不能完整記錄嘗試之前的責任重派。

例：Admin 將責任改給工人 B，但包裹仍在 A 車上。只改 currentHandler 會虛構交接。

建議：定義 actual custody/location 與 assigned responsibility 各自的來源；保存實際領件／確認回倉及配送責任重派歷史。指派紀錄需能追溯 Admin 授權者；明確型別的 audit 關聯也可供應這項資料。日常 driver-to-driver 交接仍不在已確認範圍，U-10 的例外處理繼續待定。

### R09 重送批准未綁定地址版本及本次回倉

**來源：第 18、28、30 行；需求第 4.5、9.1、10.1 節。**

`DeliveryAttempt.deliveryDetailID` 能保存最終使用的地址，是優點；但 ClientInstruction 尚未表達客戶／Admin 確認的是哪個版本，以及哪一次回倉後的新指示。

例一：客戶申請地址 v2 重送，批准時地址變為 v3；若 Start Delivery 才取最新版本，就可能用未經批准的地址。例二：第二次配送失敗後，系統誤用第一次的 approved instruction 再放行。

建議：把 request／approval、已鎖定地址版本和相關 confirmed return／retry cycle 連起來；新 pickup／attempt 要能追溯其合法依據。可用明確 cycle ID 或可靠事件關聯，不指定一定新增 cycle 表。批准時原子確認當前有效指示、實際回倉及沒有 pending cancellation。

人工代錄亦應保存來源、實際 recorder、記錄時間及驗證參照。clientUserID 表示指示屬誰，不等於實際由誰按鈕提交。身份核實方法、撤回／轉換 pending request 細則仍屬 U-08。

### R10 不同 FK 同時存在，仍可能連錯同一訂單

**來源：第 14、23、27–30 行。**

應明列以下一致性規則；各自的單欄 FK 只保證記錄存在，不保證它們屬於同一筆業務：

- DeliveryAttempt 的 Package 與 DeliveryDetail 必須屬同一 Order。
- PickingScan 的 PickingTask 與匹配的 OrderItem 必須屬同一 Order。
- Reservation 的 Payment（如有）與 OrderItem 必須屬同一 Order。
- CustodyEvent 如引用 PickupList，該 Package 必須是該 List 的 Item。
- ClientInstruction 的 clientUserID 必須是該 Order 的客戶。
- 各 worker／Admin action 必須符合當前權限，並保留歷史作者；FK 指向 User 不等於已通過權限檢查。

選型後以適合的複合引用／約束或受交易保護的服務檢查落實。不是每一條都能或應以單一 CHECK 完成。[官方 FK／約束說明](https://www.postgresql.org/docs/current/ddl-constraints.html)

### R11 臨時密碼及 session 生命週期未交代

**來源：第 9、33 行；需求第 11.3、12.1 節。**

User 的 passwordHash、accountStatus、lockUntil 已有基本方向，但尚未明確支援24小時到期、單次使用的臨時密碼、首次登入強制改密碼，以及成功改密碼後使全部 session 失效。

建議：在 User metadata／reset record 及 session 機制中定義 expiry、consumed、must-change 和撤銷依據。未必要另加 session 表，也不可儲存可讀原密碼／臨時密碼。人工交付仍按已確認方式；具體身份核實待 U-08。

### R12 資料型別、狀態及保留政策仍須補成規格

**來源：全部主要欄位，特別第 16–20、28–33 行；需求第 5、12、15 節。**

這是 Main fields 草稿，沒有逐欄型別不算畫錯；但實作版仍應補：

- 金額精度、數量型別與合法範圍；不能接受負的訂購量，盤點零數則必須允許。HKD 可作全系統固定規則，不一定另加多幣別功能。
- 每種 status 的合法值、條件與轉換；Delivery success 至少一份有效 Proof，Other failure 必須 note。不能以欄位非空代替整個流程檢查。
- `Order.totalAmount` 與下單明細單價／數量如何保持一致；重複 SKU 要合計驗庫存，可選合併明細，不一定強制唯一 order/product。
- 是否用 SKU 作商品 QR 識別，或另設識別值。缺獨立 productQRCode 欄位未必是錯；要與實際 QR 規格一致。
- 一致時間基準、NULL、長度、必要索引及刪除／更新行為。產品／用戶停用時保留歷史，避免普通刪除連帶刪走訂單。
- Logs 只保存獲允許的內容；oldValue/newValue/details 不可直接寫入地址、電話或密碼後再給 Admin 查閱。
- Audit 3 年、Security/Scan 90 日、Attempt/Proof 1 年的清理與關聯處理。沒有 expiresAt 欄位不代表不能清理；重要的是流程及保留內容明確。
- 客戶完整追蹤頁如何取得允許展示的時間線；Admin-only 原始 audit 不可直接向客戶開放。到期刪除後保留甚麼摘要仍屬 U-09。

## 4. 不應因此新增的要求

- `PickupListItem` 的複合 PK `(pickupListID, packageID)` 沒有阻止回倉後使用新清單再領件，不必只因重送而改成同一包裹一生只准一筆或一生只准一次領件。
- 多筆 Refund 記錄可以是請求／重試歷史，不表示已批准部分退款；仍須防止重複成功退款，未知結果先核實。
- 不一定每個角色都需要一張 Profile 表；DeliveryWorkerProfile 的車牌／設備欄位本來仍屬提案。
- 不要求微服務、額外角色、多倉、多包裹、供應商採購、完整購物車或 GPS。
- U-06/U-07/U-08/U-09/U-10/U-11/U-12/U-15 的未決政策仍待討論；不能把草稿保留問題當成已批准的自動取消、重試上限、損壞扣款或 proof 存取權。

## 5. 下一版 workflow 應交代的檢查點

下列是依已確認需求整理的設計／測試案例，尚未執行：

| 案例 | 應觀察的結果 |
|---|---|
| 正常付款→執貨→領件 | checkout 前才預留；掃碼只改進度；accepted pickup 才同時扣 physical/reserved |
| 同時搶最後一件貨 | 只有足夠庫存的一方成功預留，不超賣 |
| 同一 checkout 卡被拒絕再試 | 仍在窗口內保留預留，不把單次 decline 當整個 checkout 結束 |
| 取消與 Start Picking 同時發生 | 依狀態檢查只允許一條互斥結果成立 |
| 第24小時後首次確認成功付款 | CANCELLED 不復活，啟動一次全額退款 |
| 已及時接受成功付款的訊息重送 | 不再次履行，不誤取消或退款 |
| 錯／未知 QR、過量掃碼、重複 reversal | 拒絕原因留存；已接受進度正確，不變負數 |
| 同一 package pickup 請求重送 | 不重複扣庫存；本次領件歷史只產生一次效果 |
| 配送失敗後未回倉 | 不增加倉內存量，不能批准直接再送 |
| 實收少件／損壞回倉 | 保存真實數量和狀況；缺貨處理，不能虛增可售庫存 |
| 途中未訪客先回倉 | 有實際 return receipt，不虛構 failed customer attempt |
| 回倉、客戶新指示、Admin 批准、再領件 | 綁定本次回倉與已鎖地址；新領件週期可合法成功 |
| pending cancellation 與重送批准同時發生 | 原子檢查當前有效指示；pending cancellation 阻止重送批准 |
| 成功配送未附 proof | 不允許完成；有效 signature 或 photo 任一即可 |
| 改密碼／臨時密碼到期／歷史清理 | 原有 session 按需求失效；到期 credential 不可用；已確認進度不因清理消失 |

正常完整回倉的對帳例子：初始 physical=100、reserved=0；預留3件後100/3；領件後97/0；完整完好、仍配置給原單的回倉後100/3；再領件後97/0。最後兩次合法領件必須分開記錄，不能被 package 一生一次的去重規則擋住。

## 6. 配合目前分工的建議

已確認是五 Coding／四 Testing，四角色各由一人負責前端和 Java 後端，Database 另由一人負責；新功能開 Git branch，每個小功能完成即交 Testing，最後整合。

建議在四個角色各自開發之前，共同確認 Order 狀態、識別碼、付款／配置／庫存交易和傳回結果。Database 負責人做哪些約束／migration、誰擁有共用服務及誰負責合併，仍待分配。

可保留最後正式整合，但建議每完成一條跨角色流程先接起來測一次，例如 Client 已付款的同一張單真的出現在 Warehouse queue。單獨畫面各自通過測試，不足以證明大家使用同一份訂單及庫存。這是建議的提前整合檢查，尚未變成團隊決定。

## 7. 建議修訂次序

1. 先修 ERD 的反向線端、NULL 標示及兩圖不一致。
2. 補 R01、R02 的掃碼與進度，R06 的實收紀錄，R08/R09 的 custody／指示／地址關聯。
3. 共同定義 R03–R05、R10 的狀態、並發及一次性效果，列清楚哪位 Coding owner 負責。
4. 補 R07、R11、R12 的操作與欄位規格；未決政策繼續保留待定。
5. 用第5節案例交 Testing 審閱預期結果，再隨實作逐項執行並保存真實結果。

原附件保持原樣。這份審閱沒有改圖、批准資料表、選定資料庫，亦沒有完成任何應用程式測試。
