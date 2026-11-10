package main

import (
	"net/http"
	"time"
)

// handleHealth 心跳接口，用于 Render 等免费服务的保活探测
func handleHealth(w http.ResponseWriter, r *http.Request) {
	status := "ok"
	if DB == nil {
		status = "db-not-init"
	} else if err := DB.Ping(); err != nil {
		status = "db-error"
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"ok":      true,
		"status":  status,
		"time":    time.Now().Format(time.RFC3339),
		"service": "lottery-manager",
		"version": "v1.3.11-share-card",
	})
}
