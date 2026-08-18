# 彩票管家 · 原生安卓版（网页移动端 1:1 还原）

把 `web/`（Vue3 移动端）四个核心页面原样移植为原生 Kotlin 安卓 App：
**录入 / 开奖 / 购彩 / 我的**，外加登录页。后端接口、字段、中奖算法与网页完全一致。

> 本环境没有 Android SDK，无法在此 `gradle build`。请在 Android Studio 打开 `android/` 目录编译运行。
> 下面的「自检结论」是逐文件人工核对结果，覆盖 Kotlin 语法、资源引用、API 字段对齐。

---

## 一、环境要求

| 项 | 版本 |
|----|------|
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24 |
| compileSdk / targetSdk | 34 |
| minSdk | 26（Android 8.0，自适应图标，无需 PNG 启动图标） |
| JDK | 17 |
| 网络 | App 需联网访问后端（`LotteryApp.BASE_URL`） |

## 二、在 Android Studio 打开与运行

1. Android Studio → Open → 选 `D:\Project\cpcxnew\android` 目录。
2. 等待 Gradle Sync 完成（首次会下载依赖）。
3. **改后端地址**：打开 `app/src/main/java/com/example/lottery/LotteryApp.kt`
   ```kotlin
   const val BASE_URL = "https://cpcxapi.800820882.xyz"
   ```
   本地调试可改成电脑局域网 IP，例如 `http://192.168.1.10:8080`（后端需允许该来源）。
4. 连上真机或开模拟器，点 Run。登录方式有三：注册、账号登录、设备一键登录（用 `ANDROID_ID`）。

## 三、页面与网页 1:1 对应

| 网页 | 安卓 | 还原要点 |
|------|------|----------|
| `LoginView.vue` | `LoginActivity` | 登录/注册/设备登录 三 Tab；设备号用 `Settings.Secure.ANDROID_ID`；401 清登录态跳登录 |
| `AddView.vue` | `AddFragment` | 彩种分段、玩法分段（单式/复式/胆拖）、期号 Spinner（未开奖+已开奖）/手动输入兜底、选球器、实时预览、图片 OCR 单/多结果、批量录入、倍数步进、保存后跳「购彩」 |
| `DrawView.vue` | `DrawFragment` | 双色球/大乐透分段切换；开奖列表行（彩种标签 + 期号 + 日期 + 红蓝球） |
| `BuyView.vue` | `BuyFragment` | 头部统计（共/待开奖/已中奖）、右上角最新开奖轮播（7s 自动切换 + 圆点）、彩种/状态筛选、彩票卡片（彩种/期号/玩法/倍数标签、命中球、等级徽标、待开奖斜纹虚线、中奖等级底色）、长按多选→分享/删除 |
| `ProfileView.vue` | `ProfileFragment` | 头像首字母、设备号、用户名/邮箱/注册时间、编辑保存、统计三项、退出登录 |
| 通用组件 | `BallsView` `BallPickerView` `ToastUtil` | 红蓝渐变球 + 命中绿描边✓、7 列选球网格、底部居中 Toast |

### 中奖算法
`util/Match.kt` 从 `web/src/utils/match.js` 原样移植：双色球/大乐透 2026 规则、等级判定、
复式/胆拖组合数展开（`enumerateCombos`/`ticketBets`）、等级配色（`tierStyle` / `tierNumStyle`）。
网页的 `prize_tier` 老数据可能存数字（"5"/"5等奖"），安卓端已用 `tierNum` 兼容映射。

## 四、自检结论（人工核对，未编译）

- ✅ `MainActivity` 引用的 `BuyFragment` / `ProfileFragment` 均已创建，工程可解析。
- ✅ 所有 `@layout`/`@drawable`/`@color`/`@string`/`@dimen` 引用均在 `res/` 中存在。
- ✅ ViewBinding 字段与 XML id 一一对应（Buy/Profile/Ticket/Add/Draw/Login/Main）。
- ✅ API 字段名与 `backend/models.go`、`web/src/api` 一致（`Lottery`/`DrawResult`/`User`）。
- ✅ 修复项：`emptyMap()` → `emptyMap<String,String>()`（否则 `Map<String,String>` 不匹配）；
  去掉 `fragment_buy.xml` 中无效属性 `android:gap`、`android:alignParentBottom`；
  修正 `BuyFragment` 大乐透文案笔误、轮播圆点着色方式。
- ✅ 新增 drawable：`bg_check_on/off`、`bg_ticket_pending`、`bg_badge_pending/muted`、`bg_dot_active`、`bg_line_top`。

## 五、与网页的已知简化（视觉近似，行为一致）

- **动画**：网页的「待开奖呼吸点」「中奖扫光」「卡片入场 stagger」在原生端做了静态等价处理，未逐帧复刻。
- **待开奖斜纹**：用「虚线边框 + 浅灰底 + 左侧色条」表达（对角线 repeating-gradient 在 XML 较难等价，省略斜纹填充）。
- **中奖等级底色**：用等级色 tint（`TierStyle.bg/fg`）+ 顶部色条 + 左侧色条表达，未做 180° 渐变。
- **分享**：网页用 `navigator.share`/剪贴板；安卓端生成分享码后**复制到剪贴板 + 系统分享面板**（`CREATE_SHARE` 接口一致），链接形如 `BASE_URL/#/share?code=XXX`。
- **分享页渲染**：网页 `/share` 是公网页（通过链接打开），不在 App 底部 Tab 内，故未做 App 内 ShareFragment。

## 六、文件地图（新增/还原）

```
android/app/src/main/
├─ java/com/example/lottery/
│  ├─ LotteryApp.kt              # BASE_URL / Retrofit / 鉴权拦截器
│  ├─ LoginActivity.kt
│  ├─ MainActivity.kt            # 4 Tab 切换 + 401 跳登录
│  ├─ data/                      # AuthStore / Api / ApiService / model(User,Lottery,DrawResult,ApiModels)
│  ├─ util/                      # Match(中奖算法) / Views / ImageUtil
│  └─ ui/
│     ├─ fragment/               # Add / Draw / Buy / Profile
│     └─ widget/                 # BallsView / BallPickerView / ToastUtil
└─ res/                          # layout / drawable / values(colors 与 styles.css 令牌一一对应)
```

## 七、建议下一步

1. Android Studio 打开后先 `Build → Make Project`，按报错微调（本环境无法编译，可能有个别需对齐点）。
2. 用真机连后端跑一遍四个页面，对照网页移动端截图核对视觉。
3. 如需分享页在 App 内展示，可补一个 `ShareActivity`（读取 `/api/share?code=` 渲染只读列表）。
