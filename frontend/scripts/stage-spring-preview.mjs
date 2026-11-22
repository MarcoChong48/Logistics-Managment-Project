import { copyFile, cp, mkdir, readdir, unlink, writeFile } from "node:fs/promises";

const dist = new URL("../dist/", import.meta.url);
const staged = new URL("../.spring-preview/", import.meta.url);
const admin = new URL("admin/", staged);
const shared = new URL("shared/", staged);
const adminNames = [
  "dashboard", "warehouse-cases", "delivery-cases", "refunds", "users",
  "products", "orders", "order-detail", "logs", "temporary-test-tools",
];

await Promise.all([mkdir(new URL("redesign/", admin), { recursive: true }), mkdir(shared, { recursive: true })]);
await cp(dist, new URL("redesign/", shared), { recursive: true });
const index = new URL("index.html", dist);
const legacyEntry = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>LMS Admin</title>
  <script src="/shared/redesign/legacy-admin-entry.js" defer></script>
</head>
<body>
  <a href="/admin/redesign/index.html#/admin/dashboard">Open LMS Admin</a>
</body>
</html>
`;

await Promise.all([
  copyFile(index, new URL("redesign/index.html", admin)),
  ...["login", "change-password", "welcome"].map(
    (name) => copyFile(index, new URL(`shared-${name}.html`, shared)),
  ),
  ...adminNames.map(
    (name) => writeFile(new URL(`admin-${name}.html`, admin), legacyEntry),
  ),
]);

const distAssetFiles = new Set(await readdir(new URL("assets/", dist)));
const stagedAssets = new URL("redesign/assets/", shared);
const stagedAssetFiles = await readdir(stagedAssets, { withFileTypes: true });
await Promise.all(stagedAssetFiles
  .filter((file) => file.isFile() && !distAssetFiles.has(file.name))
  .map((file) => unlink(new URL(encodeURIComponent(file.name), stagedAssets))));

console.log("Staged the React Admin and shared pages for the Spring preview.");
