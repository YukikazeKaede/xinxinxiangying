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

## 多源下载（0.8.2 起）

- 顶层以及每个 `artifacts.<abi>` 可增加可选的 `mirrors` HTTPS 地址数组；同架构各来源必须为字节完全一致的 APK，共用摘要与大小。
- 客户端选择设备架构后，仅使用该项的 `[url] + mirrors`，去重并忽略无效、非 HTTPS 或带凭据的镜像；不会把顶层 arm64 镜像混入其他架构。
- 主源失败、HTTP 非 200、连接超时（10 秒）、读取停滞（20 秒）、大小或 SHA-256 不符时尝试下一个源；传输满 15 秒平均低于 64 KiB/s 也切换，最后一个源允许慢速完成。
- 切换时重新从零下载 `.part`，界面显示当前源和进度；取消会终止整个任务。只有校验通过才改名并调用安装器，已有同版且摘要一致的包直接复用。
- `mirrors` 缺省时仍为单源；0.8.1 及更早版本忽略该字段，因此从旧版升级到 0.8.2 的这一次仍使用 GitHub，安装 0.8.2 后的后续更新才支持自动镜像。
- 更新清单仍在 GitHub，R2 仅存安装包；清单无法访问时镜像不能替代版本检查。`r2.dev` 用于开发测试，有限流；正式分发建议自定义域名并实测可达性。

0.8.2 的 R2 文件放在 `0.8.2/`，完整地址为 `https://pub-8d7524c5bc734122a0e7ffdf4b3b82c2.r2.dev/0.8.2/xinxin_0.8.2-<abi>.apk`。

## 四种架构独立发布

每个 Release 上传 `xinxin_<versionName>-arm64-v8a.apk`、`xinxin_<versionName>-armeabi-v7a.apk`、`xinxin_<versionName>-x86.apk`、`xinxin_<versionName>-x86_64.apk`。所有 APK 使用相同版本号与签名。

清单保留原有顶层 `url`、`sha256`、`size`，指向 arm64-v8a 包，供旧版 arm64 客户端继续更新。新增 `artifacts` 对象，以四种 ABI 名为键，每项各自包含 `url`、`sha256`、`size`；新客户端按设备支持顺序选择兼容架构，清单没有兼容项时停止，不下载其他架构的包。

生成前先将构建产物复制为上述四个发布文件名，然后执行：

```powershell
.\tools\make-update-json.ps1 -AllAbis `
  -Apk .\dist\xinxin_<versionName>-arm64-v8a.apk `
  -Url "https://github.com/YukikazeKaede/xinxinxiangying/releases/download/v<versionName>/xinxin_<versionName>-arm64-v8a.apk" `
  -Mirrors "https://pub-8d7524c5bc734122a0e7ffdf4b3b82c2.r2.dev/<versionName>/xinxin_<versionName>-arm64-v8a.apk" `
  -NotesFile .\notes.txt -OutFile .\update.json
```

脚本检查四个包版本一致、各自只含指定架构，并从实际文件生成摘要和字节数。单包模式不传 `-AllAbis`；未传 `-Apk` 时优先选择 arm64-v8a，避免误取最后生成的 x86_64 包。旧的单包清单格式继续可读。

`-Mirrors` 接受数组；`-AllAbis` 时传入 arm64-v8a 镜像文件地址，脚本按同一前缀生成其他三架构的镜像，并分别写入其条目。先上传四个 APK 至 R2 对应版本目录，公开下载并比对大小和 SHA-256，全部一致后才发布 GitHub 清单。

## 发版流程

1. 修改 `AndroidManifest.xml` 的 `versionCode` / `versionName`；
2. 构建并验证 APK（同签名、可覆盖安装）；
3. 上传 APK 到 GitHub Release，按上面的四种架构文件名命名；
4. 用 `tools/make-update-json.ps1` 生成 `update.json`（读取版本、大小、SHA-256）；
5. 上传/覆盖 `update.json`，asset 名保持 `update.json`；
6. 把该 Release 设为 **latest**，让 `releases/latest/download/update.json` 指向它；
7. 在旧版本上实测“检查更新 → 下载 → SHA-256 → 系统安装器 → 覆盖安装”。

## 命名

- APK：`xinxin_<versionName>-<abi>.apk`；历史单包发布仍保留原文件名。
- 清单 URL 末尾必须与 asset 文件名完全一致。GitHub 不支持重命名 asset，名字不对只能删除重传。
- 同一 Release 内不要放多个同名 APK。

## 安全边界

- 只接受 HTTPS 清单；下载后进行 SHA-256 校验。
- 不静默安装：始终交给系统安装器，用户需确认。
- `versionCode` 只能递增；覆盖安装要求签名一致。
- 更新清单可被托管方替换，因此发布账号和私钥必须妥善保护。
- 建议保留一个可回滚的旧版本 APK；服务端清单出错时用户仍可手动安装。
- 改低清单版本号不会让已升级客户端自动降级；如需撤回有问题的版本，应以更高 versionCode 发布修复包，Android 还要求相同签名。

验收：正常主源不请求镜像；主源 404、超时、持续低速及哈希错误均切换；全部失败显示源数量；取消不再请求镜像且清理 `.part`；验证旧清单、四架构选择及缓存复用。真机需另走一次系统安装确认与覆盖升级。
