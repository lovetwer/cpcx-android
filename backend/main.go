package main

import (
	"log"
	"net/http"
	"time"

	"github.com/joho/godotenv"
)

func main() {
	// 强制设置时区为北京时间（Render 默认 UTC，会导致定时任务时间错乱）
	loc, _ := time.LoadLocation("Asia/Shanghai")
	time.Local = loc

	// 加载 .env（不存在也不报错，方便纯环境变量部署）
	_ = godotenv.Load()

	Cfg = LoadConfig()

	if err := InitDB(Cfg); err != nil {
		log.Fatalf("❌ 数据库连接失败: %v", err)
	}
	startScheduler(Cfg)

	router := NewRouter()

	// 用户模块
	router.Handle("POST", "/api/register", handleRegister)
	router.Handle("POST", "/api/login", handleLogin)
	router.Handle("POST", "/api/login/device", handleDeviceLogin)
	router.Handle("GET", "/api/me", authMiddleware(handleMe))
	router.Handle("PUT", "/api/me", authMiddleware(handleUpdateMe))
	router.Handle("DELETE", "/api/me", authMiddleware(handleDeleteMe))

	// 彩票管理模块
	router.Handle("POST", "/api/lottery", authMiddleware(handleCreateLottery))
	router.Handle("POST", "/api/lottery/batch", authMiddleware(handleBatchLottery))
	router.Handle("GET", "/api/lottery", authMiddleware(handleListLottery))
	router.Handle("DELETE", "/api/lottery/{id}", authMiddleware(handleDeleteLottery))
	router.Handle("PUT", "/api/lottery/{id}/status", authMiddleware(handleUpdateStatus))

	// 图片识别模块（同时暴露 /lottery/ai-generate）
	router.Handle("POST", "/api/lottery/recognize", authMiddleware(handleRecognize))
	router.Handle("POST", "/lottery/ai-generate", authMiddleware(handleRecognize))

	// AI 预测模块
	router.Handle("POST", "/api/ai/predict", authMiddleware(handleAIPredict))

	// 自动拉奖 / 开奖查询
	router.Handle("GET", "/api/draw", handleListDraw)
	router.Handle("POST", "/api/admin/pull", adminMiddleware(handlePull))
	router.Handle("POST", "/api/admin/check", adminMiddleware(handleCheck))
	router.Handle("POST", "/api/admin/notify", adminMiddleware(handleAdminNotify))
	router.Handle("POST", "/api/admin/test-mail", adminMiddleware(handleAdminTestMail))
	router.Handle("POST", "/api/admin/preview-mail", adminMiddleware(handleAdminPreviewMail))

	// 分享模块
	router.Handle("POST", "/api/share", authMiddleware(handleCreateShare))
	router.Handle("GET", "/api/share", handleGetShare) // 公开访问，无需登录
	router.Handle("GET", "/share", handleSharePage)    // 分享落地页：给微信/QQ 爬虫生成动态卡片，再 JS 跳 SPA

	// 保活心跳
	router.Handle("GET", "/health", handleHealth)

	// 跨域（前后端分离：允许前端域名访问 API）
	cors := func(h http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Access-Control-Allow-Origin", "*")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, x-admin-key")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			if r.Method == "OPTIONS" {
				w.WriteHeader(http.StatusNoContent)
				return
			}
			h.ServeHTTP(w, r)
		})
	}

	addr := ":" + Cfg.Port
	log.Printf("🚀 大奖来了后端已启动: http://localhost%s", addr)
	log.Fatal(http.ListenAndServe(addr, cors(router)))
}
