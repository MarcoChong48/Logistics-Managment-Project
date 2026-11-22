# Delivery v4.0 完整示範版

示範版分支為 `feature/delivery-v4-demo`，標籤為 `v4.0-demo`。今晚合併使用 `feature/delivery-v4-integration`（`v4.0`）。

## 本機啟動

啟動 Docker Desktop。從 `.env.example` 建立本機 `.env`，設定 `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD`，並由包含 `compose.yaml` 的資料夾執行下列指令。完成後開啟 [本機示範站](http://127.0.0.1:8082/)。

```powershell
docker compose -p lms-delivery-v4-demo -f compose.yaml -f compose.delivery-integration.yaml -f compose.delivery-demo.yaml up --build -d
```

示範版使用獨立的 Compose 專案和本機 PostgreSQL 資料卷。以 Admin 初始帳戶登入及更改密碼後，在 **Test tools → Create test user** 建立測試帳戶。外送員更改臨時密碼後會進入 Delivery。標記的示範資料才可使用模擬交接。退款示範只記錄已提出請求的 `REQUESTED` 狀態。

## 示範流程

測試資料由共用 Admin 帳戶建立。使用 **Test tools → Create test user** 建立一名 Warehouse Worker、一名 Client 和兩名 Delivery Worker，記下 Warehouse Worker 及 Client 的使用者名稱。

在 Admin 頁面按 `F12`，開啟 **Console**，將下列範例中的兩個使用者名稱換成你建立的帳戶後執行。此操作使用目前的 Admin 登入，會建立三張示範清單及對應的共用訂單、庫存和包裹。

```javascript
async function findTestUserID(username) {
  const query = new URLSearchParams({ adminSearchUserKeyword: username });
  const users = await sharedSendRequest("GET", "/api/admin/users?" + query);
  const account = users.adminSearchUserRowList.find(user => user.username === username);
  if (!account) throw new Error("Test account not found: " + username);
  return String(account.userID);
}

const preview = await sharedSendRequest("POST", "/api/admin/delivery-preview/fixtures", {
  warehouseUserId: await findTestUserID("test_warehouse_example"),
  clientUserId: await findTestUserID("test_client_example"),
  requestId: crypto.randomUUID()
});
console.table(preview.lists);
```

回傳欄位 `assignedTo` 代表獲派外送員的 User ID。在兩名外送員均啟用、兩人均無未結束清單時，三張新清單會以兩張及一張分派。使用另一個瀏覽器工作階段登入獲派外送員帳戶，在 **Pickup** 手動輸入 `listQRCode` 確認清單，再輸入 `packageQRCode` 收貨。亦可使用相機掃描含有相同值的 QR 碼。

到 **My deliveries** 開始派送，並在 **Record outcome** 儲存證明或記錄失敗。失敗後可從 **Return handoffs** 申報退件。

使用申報退件的外送員帳戶，在 **Test tools** 依序記錄 Warehouse 收貨、Client 指示及 Admin 批准。這些操作只作用於示範訂單。實際交接由各角色的正式功能負責。遷移供本機審閱及測試使用。[Delivery 資料庫遷移](../database/07_delivery_changes.sql)

批准重新派送後，系統會建立及分派一張新清單。使用獲派帳戶登入 Delivery，在 Console 執行下列程式，按 `order` 找到該訂單的新清單碼，再到 **Pickup** 確認及收貨。

```javascript
const current = await sharedSendRequest("GET", "/api/delivery/workspace");
console.table(current.pickupLists.filter(list => list.status === "OPEN").flatMap(list =>
  list.packages.map(pkg => ({
    order: pkg.orderReference,
    listQRCode: list.qrCode,
    packageQRCode: pkg.qrCode
  }))
));
```

## 本機驗證

- 共用登入、首次改密碼、Delivery 首頁及角色限制已通過測試。150 項既有規則測試全部通過。
- 完整版以合成資料驗證清單自動分派、提貨防重複提交、私有簽名、派送結果、退件、等候批准、重新派送、重複失敗提示及待處理退款。Admin 可以讀取同一筆已更新的訂單。
- 已確認破損貨品計入不可售庫存，過期簽收證明會清理並保留派送摘要，遷移也可在已有資料的情況下重跑。
- 測試使用 Docker PostgreSQL 和等值 QR 字串。實體相機及正式 Supabase 環境尚待整合測試。
