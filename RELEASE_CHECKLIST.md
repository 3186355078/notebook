# WorkLog AI 0.4.2 内部试用发布检查清单

## GitHub 与 Git

- [x] PR #5 已完成 Markdown Parser、原生 Compose Renderer、安全边界和 Summary 原文数据流审查。
- [x] PR #5 已由 Draft 转为 Ready，并以普通 Merge Commit `b01163e4d7c2d1f0f494b95bca8d4be7fe1379b6` 合入 `main`。
- [x] PR Base 为 `main`，Head 为 `feature/v0.4.2-summary-markdown-render`；远程 checks 未配置，未将其表述为 CI 通过。
- [x] 未 force push，也未删除远程功能分支。
- [x] `v0.1.0-internal` 至 `v0.4.1-internal` 均保持原指向。
- [x] `.codex/` 保持本地未跟踪状态，未提交、未删除。

## 版本与数据协议

- [x] versionName：0.4.2；versionCode：7；Release applicationId：`com.worklogai.app`。
- [x] Room database version：2；backupFormatVersion：2。
- [x] Schema 1 SHA-256：`4BF57A358800911B18E10DF66C105D4F513AC7DDC16F89E7CF9F00CE59E32E4C`。
- [x] Schema 2 SHA-256：`944C04F92F7633FCFCA2DB407588B850B981AC7B2E4541A0498505330D40862D`。
- [x] Schema 1/2 字节未变化；仅保留 Migration 1→2，无新 Migration 或 destructive migration。
- [x] Summary 继续保存 `originalContent`/`editedContent` Markdown 原文；Renderer 不进入数据库或 Backup。

## 自动化测试

- [x] JVM/Robolectric：320/320，45 suites，failures/errors/skipped 均为 0。
- [x] HONOR Android 16 Connected Android Test：89/89，failures/errors/skipped 均为 0。
- [x] Markdown/Summary 定向：24/24；横屏 NavigationRail 专项：1/1。
- [x] Debug Lint：0 errors、35 warnings；Release Lint：0 errors、35 warnings。
- [x] 本轮无新增阻断 Accessibility warning；Detekt、ktlint 通过。
- [x] 受控 HTTP 集成测试使用仓库内本地服务和 ADB reverse 真实执行，测试后已清理。

## 0.4.1 → 0.4.2 升级与 Summary 兼容

- [x] 正式 0.4.1/code 6 APK 的 SHA-256 与证书已核对，并使用同一签名覆盖安装 0.4.2/code 7。
- [x] 覆盖安装未清除应用数据，Room 未执行新 Migration，API Key 仅验证“已配置”状态。
- [x] 标准 Markdown、普通文本、editedContent、stale、20KB+ 长内容和畸形 Markdown 均无需迁移即可直接打开。
- [x] 阅读态优先使用 `editedContent ?: originalContent` 的既有规则，未重新生成或改写旧 Summary。
- [x] Restore Original 后立即重新渲染；Copy 与 Export 继续输出 Markdown 原文。
- [x] Backup v2 保存并恢复原始 Markdown 字段，不包含 HTML、渲染树、AnnotatedString 或 API Key。

## Markdown 阅读与安全

- [x] H1–H4、段落、粗体、斜体、无序/有序/嵌套列表、Task List、引用、分隔线、行内代码和 fenced code 均以原生 Compose 渲染。
- [x] 阅读态不显示 Markdown 控制符；编辑态保留完整 Markdown，并支持编辑/预览切换。
- [x] HTML 与 JavaScript 不执行；Links 只显示文本且不可点击；Remote Image 不请求网络，仅显示安全替代文本。
- [x] 不使用 WebView，不新增 Manifest 权限，不自动加载远程资源。
- [x] TalkBack 不朗读 Heading 控制符；Task List 不声明为可交互 Todo。
- [x] 52KB Markdown 已检查首次打开、滚动、横屏、1.5× 字体及前后台切换，无 FATAL/ANR/OOM；该结果为体验检查而非严格 benchmark。

## 签名与 Release

- [x] Release Keystore 位于仓库外，alias 为 `worklog-ai-release`，算法 RSA 4096，且有安全备份。
- [x] 证书 SHA-256：`15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801`。
- [x] `debuggable=false`、R8/minification 与资源压缩启用，系统备份和明文网络保持禁用。
- [x] APK v2/v3 签名有效且不是 Debug 证书；AAB `jarsigner` 验证有效。
- [x] 当前环境没有独立 bundletool CLI，bundletool validate 未执行且不宣称通过。
- [x] APK/AAB 未发现 dev/debug/test 入口、测试 Key、Keystore、密码、私有路径、测试数据库或测试备份。
- [x] 最终 HEAD 的 APK/AAB SHA-256 和 R8 mapping 归档在 Git 忽略目录 `release-artifacts/0.4.2/`。
- [x] GitHub Release 未创建，APK/AAB 未上传 GitHub，APK/AAB/mapping 未进入 Git。

## 兼容性风险接受

> Android 10～13 第二台真机和模拟器专项未执行，不宣称通过，作为 0.4.2 内部试用风险接受。

- 完整真机矩阵在 HONOR PPG-AN00、Android 16 / API 36 执行；设备序列号不记录。
- Markdown Table、Footnote、可点击 Link、远程图片、Code Block Copy 和复杂语法高亮暂不支持。
- Dynamic Color 受壁纸影响；其他 OEM SAF Provider 和长期后台行为继续在内部试用期观察。
- 备份默认未加密；`LocalClipboardManager` 上游弃用警告仍存在。
