# Delivery v4.0 小組合併指南

採用 `feature/delivery-v4-integration`（標籤 `v4.0`）與其他組員合併。本版本按 Admin Stage 6 `01fba08` 接上共用登入和 PostgreSQL，使用 `delivery-full` 啟用自動分派、清單確認、逐件提貨、簽收、失敗及退件申報。Delivery 的示範 API、Test tools 及測試資料表放在另一個示範分支。

## 本機啟動

在含有 `compose.yaml` 的資料夾啟動 Docker Desktop。從 `.env.example` 建立本機 `.env`，設定 `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD`。執行下列指令後，從 [共用登入](http://127.0.0.1:8081/) 登入。Admin 透過 **Users** 建立外送員帳戶。外送員在首次更改密碼後進入 Delivery。

```powershell
docker compose -p lms-delivery-v4-integration -f compose.yaml -f compose.delivery-integration.yaml up --build -d
docker compose -p lms-delivery-v4-integration -f compose.yaml -f compose.delivery-integration.yaml ps
```

`compose.delivery-integration.yaml` 會在後端啟動前，對本機共用 PostgreSQL 套用 Delivery 遷移，並掛載私人簽收檔案資料卷。這份設定以 Admin 基底 Compose 啟動，適用於本機合併驗證。 [Compose 設定](../compose.delivery-integration.yaml)

## 今晚合併時核對的事項

1. 以組員共同採用的整合分支合併本版本。Admin、Client、Warehouse 和 Database 的正式改動依各自的實際分支接入。
2. GitHub 上的 Client 分支已使用 `04` 至 `06`，本版本使用 `07_delivery_changes.sql`。請 Database 負責人按共用 schema 套用基礎及各角色已採用的遷移，再套用 Delivery 遷移。使用已有資料時，由 Database 負責人確認既有遷移紀錄；本機 Compose 會保留已有的 PostgreSQL 資料卷。
3. 檢查各角色仍使用共用登入、相同的訂單／庫存／包裹資料，以及各自的首頁。合併後的共享 Compose 應保留 `delivery-full` 及私人簽收資料卷。
4. 由 Warehouse 將合資格包裹交給現有 Java 建單服務。正式 Warehouse 實收、Client 指示及 Admin 審批接上後，再驗收重新派送和退款交接。Client 和 Admin 應對齊通知寫入及不可售庫存計算，避免同一事件重複發送或破損庫存仍可購買。

Admin 原有 Java、頁面、`compose.yaml` 和 `01`／`03` 遷移是基底。Delivery 新增自己的模組、Compose 設定和遷移。在既有的共用程式中，只修改 Delivery 登入首頁，並更新相關測試。

- [交接清單和驗收次序](DELIVERY_TEAM_HANDOFF_ZH_HK.md)
- [Delivery 資料庫遷移](../database/07_delivery_changes.sql)

## 合併前驗證

已在獨立 Docker PostgreSQL 驗證本分支的正式登入、自動分派、逐件提貨、Admin 讀取同一筆 `SHIPPED` 訂單，以及派送失敗後申報退件。Delivery 示範接口均未註冊。

以 Client `fac82e6` 進行三方合併預覽時，`backend/pom.xml`、共用訂單／商品／庫存程式、登入安全設定、共用網頁及兩份原有 README 出現重疊。合併時請共同保留兩邊所需的依賴、角色權限、交易和頁面行為。整個小組合併後的程式、正式 Supabase 和實體相機仍需驗收。
