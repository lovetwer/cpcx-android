# 双色球大乐透彩票管家

基于 **Go + MySQL** 的后端，配套 **网页端（Vue 3 + Vite，构建后由 Go embed 打包进单文件）** 与 **原生安卓端（Kotlin）**。
核心目标：用户录入彩票 → 系统定时拉取官方开奖 → 自动核对中奖 → 中奖后自动邮件+推送通知。

---

## 功能模块对照

| # | 模块 | 实现 |
|---|------|------|
| 1 | 用户模块 | 注册 / 登录 / **按设备号一键登录**（无账号自动建号）。Token 为 HMAC 签名，无第三方依赖 |
| 2 | 彩票管理 | 单张录入、批量录入、查询（彩种/状态/期号）、删除、改状态（标记「已兑奖」）。字段：红球/蓝球、期号、投注类型(ssq/dlt) |
| 3 | 自动拉奖 | 定时任务从多数据源抓取开奖并入库：双色球=**500彩票网**、大乐透=**体彩官方(sporttery.cn)**（均含奖池），主源请求失败自动回退灰鸟API `api.huiniao.top`。拉奖窗口：每天 21:20~23:30 每 5 分钟一次，周六不拉 |
| 4 | 自动核对 | 定时任务比对用户彩票与开奖号，标注「未开奖 / 未中奖 / 已中奖」（默认每 15 分钟） |
| 5 | 图片识别 | 上传截图 → 调**硅基流动(SiliconFlow) 托管的 PaddlePaddle/PaddleOCR-VL-1.5**（OpenAI 兼容 `/v1/chat/completions`）识别号码并自动录入（路由同时挂载 `/api/lottery/recognize` 与 `/lottery/ai-generate`） |
| 6 | 通知模块 | 中奖后通过 **Resend** 发邮件 + 调通用 **推送 Webhook** 通知用户 |
| 7 | 保活模块 | 提供 `GET /health` 心跳，供 Render 免费版保活探测 |

---

## 目录结构

```
cpcxnew/
├── backend/                # Go 后端（同时托管网页前端）
│   ├── main.go             # 路由装配 + 静态资源 + CORS + 启动
│   ├── config.go / db.go / models.go / util.go / auth.go / router.go / context.go
│   ├── handlers_*.go       # 各业务 handler
│   ├── services_*.go       # 拉奖 / 核对 / 通知 / OCR
│   ├── scheduler.go         # 定时任务
│   ├── static/dist/         # 网页前端构建产物（Vue 3 编译，Go embed 打包）
│   ├── Dockerfile
│   └── .env.example
├── web/                   # 网页前端源码（Vue 3 + Vite）
│   ├── src/               # 组件 / 视图 / API 层 / 状态管理 / 样式
│   ├── package.json
│   └── vite.config.js     # build 输出到 backend/static/dist，dev 代理 /api 到 8080
├── android/                # 原生安卓（Kotlin + Retrofit + ViewBinding）
├── docker-compose.yml      # 本地 MySQL（可选）
└── README.md
```

---

## 本地运行（后端 + 网页）

### 1. 准备 MySQL
```bash
# 方式 A：docker
docker-compose up -d mysql
# 方式 B：本机装好 MySQL，建库
mysql -uroot -p -e "CREATE DATABASE lottery CHARACTER SET utf8mb4;"
```

### 2. 配置环境变量
```bash
cd backend
cp .env.example .env
# 编辑 .env 填 DB_PASSWORD、OCR_BASE_URL、RESEND_API_KEY 等
```

### 3. 启动
```bash
go run .            # 或 go build 后运行 ./lottery-manager
# 打开 http://localhost:8080
```
首次启动会自动建表（`users` / `lotteries` / `draw_results`），并延迟 5 秒先拉一次开奖。

### 3.1 网页前端开发（Vue 3 + Vite）
前端源码在 `web/`，生产构建会输出到 `backend/static/dist` 并被 Go 一起打包成单文件。
```bash
cd web
npm install
npm run dev        # 本地开发，默认 http://localhost:5173，/api 自动代理到 8080 后端
npm run build      # 构建到 backend/static/dist（改完前端后需重新 go build 才会进二进制）
```
> 路由用 hash 模式（`#/lottery`），Go 静态服务器无需做 SPA 回退，部署最稳。

### 3.2 安卓端
见 `android/`（Kotlin + Retrofit + ViewBinding）。改 `LotteryApp.kt` 里的 `BASE_URL` 为你的后端地址后用 Android Studio 打开编译。

---

## 部署到 Render（免费版）

1. 新建 Web Service，连仓库，Build 用仓库里的 `backend/Dockerfile`（或 Render 的 Go 运行时，根目录指向 `backend`）。
2. 在 Environment 里填好所有变量（同 `.env.example`，`DB_HOST` 填你的 MySQL 地址）。
3. 启动后访问 `https://<你的服务>.onrender.com/`。
4. **保活**：用 UptimeRobot /  cron 每 14 分钟访问一次 `https://<服务>/health`，避免免费实例休眠。
   ```bash
   curl -s https://<你的服务>.onrender.com/health
   ```

---

## 原生安卓端

用 **Android Studio** 打开 `android/` 目录（会自动生成 Gradle Wrapper）。

- 改 `android/app/src/main/java/com/example/lottery/LotteryApp.kt` 里的 `BASE_URL` 为你的后端地址。
- 核心流程：启动时用 `Settings.Secure.ANDROID_ID` 作为设备号 → `POST /api/login/device` 一键登录 → 拉取列表 / 录入 / 图片识别。
- 依赖：Retrofit2 + Gson + OkHttp + Coroutines + Material 组件（见 `app/build.gradle`）。

> 安卓端无法在此环境编译（需 Android SDK），代码为标准写法，在 Android Studio 中可直接编译运行。

---

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/register` | 注册 |
| POST | `/api/login` | 账号密码登录 |
| POST | `/api/login/device` | 设备号一键登录 |
| GET | `/api/me` | 当前用户 |
| POST | `/api/lottery` | 单张录入（需登录） |
| POST | `/api/lottery/batch` | 批量录入 `{items:[...]}` |
| GET | `/api/lottery` | 查询（?type=&status=&issue=） |
| DELETE | `/api/lottery/{id}` | 删除 |
| PUT | `/api/lottery/{id}/status` | 改状态 `{status:"已兑奖"}` |
| POST | `/api/lottery/recognize` 或 `/lottery/ai-generate` | 上传图片识别录入 |
| GET | `/api/draw?type=ssq` | 官方开奖结果 |
| POST | `/api/admin/pull` | 手动拉奖（需 ADMIN_KEY） |
| POST | `/api/admin/check` | 手动核对（需 ADMIN_KEY） |
| GET | `/health` | 心跳保活 |

所有受保护接口在请求头带 `Authorization: Bearer <token>`。

---

## 重要说明

- **拉奖接口**：已按真实返回结构实现（双色球 `one~six` 为红球、`seven` 为蓝球；大乐透 `one~five` 前区、`six,seven` 后区）。
- **图片识别（硅基流动 PaddleOCR-VL-1.5）**：模型走 OpenAI 兼容接口，我们把图片以 base64 data URL 放进 `messages[].content` 并引导模型输出纯 JSON 数组；`backend/services_ocr.go` 的 `parseOCRText` 先抽 JSON（兼容 ```json 围栏），再用中文 key / 正则兜底，最后 `normalizeBalls` 规范号码。模型名用 `OCR_MODEL` 覆盖，地址用 `OCR_BASE_URL` 覆盖（默认已指向硅基流动）。已用真实 Key 连通验证（鉴权 + 模型名均通过）。
- **推送接口**：`PUSH_URL` 为通用 Webhook，POST JSON `{title, body, device_id, username}`，可对接 OneSignal / 自建网关。
- **中奖判定规则**：双色球（6+1）——任一蓝球命中或红球≥4 即中奖；大乐透（5+2）——前区≥4、或前区3且后区≥1、或后区全中即中奖。已通过单元测试（`go test`）。
- 密码使用 `sha256 + 随机盐` 存储，纯标准库实现。
