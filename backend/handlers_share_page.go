package main

import (
	"database/sql"
	"fmt"
	"html"
	"io"
	"net/http"
	"strconv"
	"strings"
)

// handleSharePage 分享落地页：微信/QQ 的爬虫抓取链接时不执行 JS、也带不上 # 后面的参数，
// 所以分享码必须走服务端能看到的路径参数。这里按 code 动态生成 OG 卡片文字，
// 浏览器打开后用 JS 跳到 SPA 的 hash 路由（meta refresh 会被部分爬虫跟随，故意不用）。
func handleSharePage(w http.ResponseWriter, r *http.Request) {
	code := r.URL.Query().Get("code")
	desc := "记录双色球、大乐透彩票，开奖自动核对、中奖即时通知。"
	switch {
	case code == "":
		// 没带分享码：用默认文案，直接跳首页
		code = ""
	case isShareCode(code):
		desc = shareSummary(code)
	default:
		desc = "分享链接无效"
		code = ""
	}
	target := strings.TrimRight(Cfg.WebURL, "/") + "/#/share"
	if code != "" {
		target += "?code=" + code
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Robots-Tag", "noindex")
	_, _ = io.WriteString(w, buildShareHTML(desc, target))
}

// isShareCode 分享码是 createShare 生成的 8 位十六进制；这里放宽到 1-32 位十六进制，
// 既拦住注入字符，又不至于改长度就失效。
func isShareCode(code string) bool {
	if len(code) == 0 || len(code) > 32 {
		return false
	}
	for _, c := range code {
		isHex := (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
		if !isHex {
			return false
		}
	}
	return true
}

// shareSummary 查分享码对应的彩票，生成微信卡片描述，如
// "共3注（双色球2注、大乐透1注），双色球 03,09,15,21,28,33+07 等"。
func shareSummary(code string) string {
	var lotteryIDs string
	err := DB.QueryRow("SELECT lottery_ids FROM shares WHERE code=?", code).Scan(&lotteryIDs)
	if err != nil {
		if err == sql.ErrNoRows {
			return "分享不存在或已过期"
		}
		return "分享内容暂时无法查看"
	}
	ids := parseLotteryIDs(lotteryIDs)
	if len(ids) == 0 {
		return "分享内容为空"
	}
	placeholders := make([]string, len(ids))
	args := make([]interface{}, len(ids))
	for i, id := range ids {
		placeholders[i] = "?"
		args[i] = id
	}
	rows, err := DB.Query("SELECT type, red_balls, blue_balls FROM lotteries WHERE id IN ("+strings.Join(placeholders, ",")+")", args...)
	if err != nil {
		return "分享内容暂时无法查看"
	}
	defer rows.Close()
	typeCounts := map[string]int{}
	total := 0
	preview := ""     // 第一张票的 "双色球 03,09,...+07"
	previewType := "" // 预览票的彩种名
	for rows.Next() {
		var typ, red, blue string
		if err := rows.Scan(&typ, &red, &blue); err != nil {
			continue
		}
		total++
		typeCounts[typ]++
		if preview == "" {
			preview = formatTicketPreview(typ, red, blue)
			previewType = lotteryTypeName(typ)
		}
	}
	if total == 0 {
		return "分享内容为空"
	}
	parts := make([]string, 0, len(typeCounts))
	for _, typ := range []string{TypeSSQ, TypeDLT} {
		if n := typeCounts[typ]; n > 0 {
			parts = append(parts, fmt.Sprintf("%s%d注", lotteryTypeName(typ), n))
		}
	}
	if n := total - typeCounts[TypeSSQ] - typeCounts[TypeDLT]; n > 0 {
		parts = append(parts, fmt.Sprintf("其他%d注", n))
	}
	desc := fmt.Sprintf("共%d注（%s）", total, strings.Join(parts, "、"))
	if preview != "" {
		if total > 1 {
			desc += fmt.Sprintf("，%s %s 等", previewType, preview)
		} else {
			desc += fmt.Sprintf("，%s %s", previewType, preview)
		}
	}
	return desc
}

// parseLotteryIDs 把 "1,2,3" 拆成干净的数字 ID 字符串，过滤空串和非数字，最多取 50 个。
func parseLotteryIDs(s string) []string {
	parts := strings.Split(s, ",")
	ids := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		if _, err := strconv.ParseInt(p, 10, 64); err != nil {
			continue
		}
		ids = append(ids, p)
		if len(ids) >= 50 {
			break
		}
	}
	return ids
}

func lotteryTypeName(typ string) string {
	switch typ {
	case TypeSSQ:
		return "双色球"
	case TypeDLT:
		return "大乐透"
	default:
		return "其他"
	}
}

// formatTicketPreview 把红蓝球拼成 "03,09,15,21,28,33+07" 样式，前面不带彩种名。
func formatTicketPreview(typ, red, blue string) string {
	red, blue = normalizeBalls(red), normalizeBalls(blue)
	if red == "" {
		return ""
	}
	if blue == "" {
		return red
	}
	return red + "+" + blue
}

// buildShareHTML 生成带 OG 标签的最小落地页。desc 已由本文件内容构造，仍统一转义防意外。
func buildShareHTML(desc, target string) string {
	descEsc := html.EscapeString(desc)
	targetEsc := html.EscapeString(target)
	jsTarget := html.EscapeString(target) // hex/URL 字符，双保险
	image := strings.TrimRight(Cfg.WebURL, "/") + "/og-image.png"
	return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<title>大奖来了 · 分享</title>
<meta property="og:type" content="website" />
<meta property="og:site_name" content="大奖来了" />
<meta property="og:title" content="大奖来了 · 双色球大乐透" />
<meta property="og:description" content="` + descEsc + `" />
<meta property="og:image" content="` + html.EscapeString(image) + `" />
<meta property="og:image:type" content="image/png" />
<meta property="og:image:width" content="600" />
<meta property="og:image:height" content="600" />
<style>body{font-family:sans-serif;background:#1f6b4a;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;margin:0}</style>
</head>
<body>
<p id="msg">正在打开分享…</p>
<noscript><p>请 <a style="color:#ffd54f" href="` + targetEsc + `">点这里继续</a>。</p></noscript>
<script>
try { location.replace("` + jsTarget + `"); } catch (e) {}
</script>
</body>
</html>`
}
