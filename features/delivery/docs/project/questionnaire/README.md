# 建立 LMS Google Form

目前已完成16節、38題的問卷及自動建立檔案；尚未在Google帳戶建立線上表單。

## 預覽

先閱讀 [LMS_Undecided_Questionnaire.md](LMS_Undecided_Questionnaire.md)。每題都是A、B及可自行填寫的Others；不會把建議當成已批准需求。

## 在自己的Google帳戶建立

1. 打開 [Google Apps Script新專案](https://script.google.com/home/start)，選擇新增專案。
2. 用 [Create_LMS_Google_Form.gs](Create_LMS_Google_Form.gs) 的完整內容取代編輯器內原有內容並儲存。選取 `createLmsQuestionnaire` 執行，依Google畫面授權。
3. 完成後在「執行記錄」打開 `editUrl`，即可在Google Forms檢查38題及各題「其他」文字欄。草稿尚未發佈；檢查後按Google Forms的發佈按鈕設定填答者，再複製填答連結給組員。

只需執行一次。同一Apps Script專案再次執行會傳回原表單的編輯連結，不會另建重複表單或改寫已修改的題目。如果之前建立中斷，程式會停止並提示先檢查草稿，不會刪除現有內容。

Others／其他使用Google Forms原生自由輸入功能；實際標籤語言由Google介面決定，不會只是不能打字的普通選項。這不是JSON直接匯入：JSON是完整題目來源，真正建立表單使用上述檔案。

## 規格與狀態

- 繁體中文，涵蓋U-01至U-16；題目次序固定，設進度列。
- 每題必答，兩個固定選項A/B，加原生Others文字欄；未定可在Others說明。
- 不要求姓名／電郵，不自動公開回覆摘要，也不自動寄送或分享。
- 建立為未發佈草稿；本地語法及模擬檢查不等於已在Google帳戶執行。
- 學校帳戶若限制Apps Script或Forms，需遵照帳戶可用功能；沒有在此確認學校管理設定。

## 官方參考

- [FormApp建立表單及發佈狀態](https://developers.google.com/apps-script/reference/forms/form-app)
- [單選題與原生其他文字欄](https://developers.google.com/apps-script/reference/forms/multiple-choice-item)
- [表單設定](https://developers.google.com/apps-script/reference/forms/form)
