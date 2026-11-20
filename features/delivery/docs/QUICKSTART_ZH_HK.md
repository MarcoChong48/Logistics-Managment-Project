# Delivery v3.1 組員使用指南

## 從 GitHub 下載並啟動

1. 開啟小組 GitHub 的 `v3.1` 版本，選擇 **Code → Download ZIP**，然後完整解壓縮。[Delivery v3.1](https://github.com/MarcoChong48/Logistics-Managment-Project/tree/v3.1)
2. 啟動 **Docker Desktop**，確認使用 Linux 容器模式。在解壓後的 `features/delivery` 資料夾開啟 PowerShell。該資料夾內應有 `compose.yaml`。
3. 執行以下指令。首次啟動時，Docker 會下載所需檔案，並建立本機 PostgreSQL 資料庫及示範資料。

```powershell
docker compose up --build -d
docker compose ps
```

4. 確認 `db` 顯示 `healthy`，且 `backend` 正在運行，再開啟系統。示範帳戶為 `ken` 或 `mei`，兩者的初始密碼均為 `Delivery123!`。[開啟本機 Delivery](http://127.0.0.1:8080)

## 測試派送流程

1. 在 **Pickup** 輸入 `PL-1001`，按下 **Claim list** 認領清單。接著輸入 `PKG-1001`，按下 **Collect package** 確認收件。
2. 在 **My deliveries** 開啟該包裹，按下 **Start delivery**，再進入 **Record outcome**。選擇 **Delivered**，儲存並選取簽名或相片證明，然後按下 **Confirm delivered**。
3. 在 **Attempt history** 查看派送結果及簽收證明摘要。**Notifications** 可查看通知，頁面頂部可查看登入倒數及更改密碼。

## 測試退件及重新派送

使用另一件示範包裹，在 **Record outcome** 選擇 **Failed**，填寫原因並提交，再到 **Return handoffs** 申報退件。

在 **Test tools** 依序執行 **Receive intact → Request redelivery → Approve redelivery**，再返回 **Return handoffs**，確認狀態顯示為 **Ready for redelivery**。這三項操作用於模擬 Warehouse、Client 及 Admin 的交接。

## 停止系統

在同一個資料夾執行 `docker compose down`。測試記錄及簽名檔會保留，下次啟動後可繼續使用。
