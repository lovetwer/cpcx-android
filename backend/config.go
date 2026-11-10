package main

import (
	"os"
	"strconv"
)

// Config 保存运行所需的所有配置，全部来自环境变量。
type Config struct {
	Port          string
	DBHost        string
	DBPort        string
	DBUser        string
	DBPassword    string
	DBName        string
	JWTSecret     string
	// 邮件（Resend SMTP）
	MailHost     string
	MailPort     int
	MailUsername string
	MailPassword string
	MailFrom     string
	NotifyEmails string // 定时核对结果邮件的收件人（逗号分隔）
	PushURL       string
	PushToken     string
	AdminKey      string
	PullInterval  int // 分钟
	CheckInterval int // 分钟
	AgnesAPIKey   string
	AgnesAPIURL   string
	AgnesModel    string
	WebURL        string // 前端站点地址，用于分享落地页跳转和 OG 卡片图片
}

func getEnv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func getEnvInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

// Cfg 是全局配置，在 main 启动时赋值
var Cfg Config

func LoadConfig() Config {
	return Config{
		Port:          getEnv("PORT", "8080"),
		DBHost:        getEnv("DB_HOST", "127.0.0.1"),
		DBPort:        getEnv("DB_PORT", "3306"),
		DBUser:        getEnv("DB_USER", "root"),
		DBPassword:    getEnv("DB_PASSWORD", ""),
		DBName:        getEnv("DB_NAME", "lottery"),
		JWTSecret:     getEnv("JWT_SECRET", "dev-secret-change-me"),
		MailHost:      getEnv("MAIL_HOST", "smtp.resend.com"),
		MailPort:      getEnvInt("MAIL_PORT", 2465),
		MailUsername:  getEnv("MAIL_USERNAME", "resend"),
		MailPassword:  getEnv("MAIL_PASSWORD", ""),
		MailFrom:      getEnv("MAIL_FROM_ADDR", "prize@cpcxmail.800820882.xyz"),
		NotifyEmails:  getEnv("NOTIFY_EMAILS", ""),
		PushURL:       getEnv("PUSH_URL", ""),
		PushToken:     getEnv("PUSH_TOKEN", ""),
		AdminKey:      getEnv("ADMIN_KEY", ""),
		PullInterval:  getEnvInt("PULL_INTERVAL_MIN", 30),
		CheckInterval: getEnvInt("CHECK_INTERVAL_MIN", 15),
		AgnesAPIKey:   getEnv("AGNES_API_KEY", ""),
		AgnesAPIURL:   getEnv("AGNES_API_URL", "https://apihub.agnes-ai.com/v1/chat/completions"),
		AgnesModel:    getEnv("AGNES_MODEL", "agnes-2.5-flash"),
		WebURL:        getEnv("WEB_URL", "https://cpcx.800820882.xyz"),
	}
}
