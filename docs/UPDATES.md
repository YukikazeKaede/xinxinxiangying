# 应用内更新

## 清单格式

`update.json`（UTF-8）：

```json
{
  "versionCode": 15,
  "versionName": "0.6.9",
  "url": "https://example.com/xinxin_0.6.9.apk",
  "sha256": "大写或小写的 64 位十六进制",
  "size": 128330,
  "notes": "更新说明（可选）"
}
```

- `versionCode` 必须大于当前安装包才会提示。
- `url` 必须是 HTTPS，并指向实际 APK 文件名。
- `sha256` 必须与 APK 实际哈希一致；`size` 为字节数。
- 客户端在 `app/src/main/res/values/strings.xml` 的 `update_manifest_url` 读取清单地址。

## 发版流程

1. 修改 `AndroidManifest.xml` 的 `versionCode` / `versionName`；
2. 构建并验证 APK（同签名、可覆盖安装）；
3. 上传 APK 到 GitHub Release，文件名统一 `xinxin_<versionName>.apk`；
4. 用 `tools/make-update-json.ps1` 生成 `update.json`（读取版本、大小、SHA-256）；
5. 上传/覆盖 `update.json`，asset 名保持 `update.json`；
6. 把该 Release 设为 **latest**，让 `releases/latest/download/update.json` 指向它；
7. 在旧版本上实测“检查更新 → 下载 → SHA-256 → 系统安装器 → 覆盖安装”。

## 命名

- APK：`xinxin_<versionName>.apk`，例如 `xinxin_0.6.8.apk`。
- 清单 URL 末尾必须与 asset 文件名完全一致。GitHub 不支持重命名 asset，名字不对只能删除重传。
- 同一 Release 内不要放多个同名 APK。

## 安全边界

- 只接受 HTTPS 清单；下载后进行 SHA-256 校验。
- 不静默安装：始终交给系统安装器，用户需确认。
- `versionCode` 只能递增；覆盖安装要求签名一致。
- 更新清单可被托管方替换，因此发布账号和私钥必须妥善保护。
- 建议保留一个可回滚的旧版本 APK；服务端清单出错时用户仍可手动安装。
