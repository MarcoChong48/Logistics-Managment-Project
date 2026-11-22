# Delivery v4.0 — 小組合併版

今晚合併請採用 `feature/delivery-v4-integration`（`v4.0`）。完整示範版在 `feature/delivery-v4-demo`（`v4.0-demo`），使用獨立的啟動設定及測試資料。

本分支以 Admin Stage 6 `01fba08` 為基準，保留共用登入、訂單及庫存，並啟用 Delivery 實際提貨、簽收、失敗及退件申報功能。Warehouse 實收、Client 指示、Admin 正式審批及付款退款由各自模組接駁。

- [小組合併及啟動步驟](docs/DELIVERY_INTEGRATION_ZH_HK.md)
- [各角色交接事項及驗收](docs/DELIVERY_TEAM_HANDOFF_ZH_HK.md)
- [Delivery 資料庫遷移](database/07_delivery_changes.sql)
