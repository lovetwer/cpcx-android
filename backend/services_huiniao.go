package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"
)

// ---------- 灰鸟API（唯一数据源） ----------
// http://api.huiniao.top/interface/home/lotteryHistory?type=ssq&page=1&limit=30
// type: ssq=双色球, dlt=大乐透
// 返回格式：{"code":1,"info":"成功","data":{"last":{...},"data":{"list":[...]}}}
// 注意：list 嵌套在 data.data.list 里
// 双色球: one~six=红球, seven=蓝球
// 大乐透: one~five=前区, six~seven=后区
// 灰鸟API无奖池信息，poolAmount=0

type huiniaoItem struct {
	Code     string `json:"code"`      // 期号
	Day      string `json:"day"`       // 开奖日期 "2026-08-20"
	One      string `json:"one"`
	Two      string `json:"two"`
	Three    string `json:"three"`
	Four     string `json:"four"`
	Five     string `json:"five"`
	Six      string `json:"six"`
	Seven    string `json:"seven"`
	OpenTime string `json:"open_time"` // "2026-08-20 21:15:00"
}

type huiniaoResp struct {
	Code int    `json:"code"` // 1=成功
	Info string `json:"info"`
	Data struct {
		Last huiniaoItem `json:"last"`
		Data struct {
			List []huiniaoItem `json:"list"`
		} `json:"data"`
	} `json:"data"`
}

// ---------- 通用 HTTP 请求 ----------

func httpGetJSON(url string) ([]byte, error) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("Accept", "application/json, text/plain, */*")

	client := &http.Client{Timeout: 20 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	if len(body) == 0 {
		return nil, fmt.Errorf("空响应")
	}
	first := body[0]
	if first != '{' && first != '[' {
		preview := string(body)
		if len(preview) > 200 {
			preview = preview[:200]
		}
		return nil, fmt.Errorf("非JSON响应(首字符'%c'): %s", first, preview)
	}

	return body, nil
}

// ---------- 统一拉奖入口 ----------

// PullLottery 多数据源抓取双色球/大乐透最近开奖并入库。
// 主源：双色球=500彩票网、大乐透=体彩官方（services_sources.go）；
// 主源请求失败时自动回退灰鸟API（本文件），避免单源故障导致一直"待开奖"。
func PullLottery() (bool, error) {
	fallbacks := map[string][]func() (bool, error){
		TypeSSQ: {pullSSQ500, pullSSQ},       // 500彩票网 → 灰鸟
		TypeDLT: {pullDLTSporttery, pullDLT500, pullDLT}, // 体彩官方 → 500彩票网 → 灰鸟
	}
	var lastErr error
	anyNew := false
	for _, t := range []string{TypeSSQ, TypeDLT} {
		isNew := false
		for _, pull := range fallbacks[t] {
			isNew, lastErr = pull()
			if lastErr == nil {
				break // 该源成功，不再回退
			}
			logErr("拉取 %s 失败，尝试下一数据源: %v", TypeName(t), lastErr)
		}
		if lastErr != nil {
			continue
		}
		if isNew {
			anyNew = true
			logInfo("拉取 %s 完成（有新数据）", TypeName(t))
		} else {
			logInfo("拉取 %s 完成（无新数据）", TypeName(t))
		}
	}
	return anyNew, lastErr
}

func pullOneType(t string) (bool, error) {
	switch t {
	case TypeSSQ:
		return pullSSQ()
	case TypeDLT:
		return pullDLT()
	default:
		return false, fmt.Errorf("未知彩种: %s", t)
	}
}

// pullSSQ 灰鸟API拉取双色球
func pullSSQ() (bool, error) {
	url := "http://api.huiniao.top/interface/home/lotteryHistory?type=ssq&page=1&limit=30"
	body, err := httpGetJSON(url)
	if err != nil {
		return false, fmt.Errorf("灰鸟API请求失败: %w", err)
	}

	var hr huiniaoResp
	if err := json.Unmarshal(body, &hr); err != nil {
		return false, fmt.Errorf("解析灰鸟API失败: %w", err)
	}
	if hr.Code != 1 {
		return false, fmt.Errorf("灰鸟API返回 code=%d info=%s", hr.Code, hr.Info)
	}

	list := hr.Data.Data.List
	if len(list) == 0 {
		return false, fmt.Errorf("灰鸟API返回数据为空")
	}

	anyNew := false
	for _, it := range list {
		if it.Code == "" || it.One == "" {
			continue
		}
		// 双色球: one~six=红球, seven=蓝球
		red := strings.Join([]string{it.One, it.Two, it.Three, it.Four, it.Five, it.Six}, ",")
		blue := it.Seven
		drawDate := it.Day
		// 灰鸟API无奖池，poolAmount=0
		isNew, err := upsertDrawWithPool(TypeSSQ, it.Code, normalizeBalls(red), normalizeBalls(blue), drawDate, 0, "", "")
		if err != nil {
			logErr("写入 %s 期号 %s 失败: %v", TypeSSQ, it.Code, err)
		}
		if isNew {
			anyNew = true
		}
	}
	logInfo("灰鸟API拉取双色球成功（%d 条，无奖池）", len(list))
	return anyNew, nil
}

// pullDLT 灰鸟API拉取大乐透
func pullDLT() (bool, error) {
	url := "http://api.huiniao.top/interface/home/lotteryHistory?type=dlt&page=1&limit=30"
	body, err := httpGetJSON(url)
	if err != nil {
		return false, fmt.Errorf("灰鸟API请求失败: %w", err)
	}

	var hr huiniaoResp
	if err := json.Unmarshal(body, &hr); err != nil {
		return false, fmt.Errorf("解析灰鸟API失败: %w", err)
	}
	if hr.Code != 1 {
		return false, fmt.Errorf("灰鸟API返回 code=%d info=%s", hr.Code, hr.Info)
	}

	list := hr.Data.Data.List
	if len(list) == 0 {
		return false, fmt.Errorf("灰鸟API返回数据为空")
	}

	anyNew := false
	for _, it := range list {
		if it.Code == "" || it.One == "" {
			continue
		}
		// 大乐透: one~five=前区(红球), six~seven=后区(蓝球)
		red := strings.Join([]string{it.One, it.Two, it.Three, it.Four, it.Five}, ",")
		blue := strings.Join([]string{it.Six, it.Seven}, ",")
		drawDate := it.Day
		isNew, err := upsertDrawWithPool(TypeDLT, it.Code, normalizeBalls(red), normalizeBalls(blue), drawDate, 0, "", "")
		if err != nil {
			logErr("写入 %s 期号 %s 失败: %v", TypeDLT, it.Code, err)
		}
		if isNew {
			anyNew = true
		}
	}
	logInfo("灰鸟API拉取大乐透成功（%d 条，无奖池）", len(list))
	return anyNew, nil
}

// parsePoolAmount 把 "628888733" 或 "772,986,652.76" 解析成整数（元）
func parsePoolAmount(s string) int64 {
	s = strings.ReplaceAll(s, ",", "")
	s = strings.TrimSpace(s)
	if s == "" {
		return 0
	}
	if idx := strings.Index(s, "."); idx > 0 {
		s = s[:idx]
	}
	n, err := strconv.ParseInt(s, 10, 64)
	if err != nil {
		return 0
	}
	return n
}

// parseFyjInfo 解析福运奖注数和奖金
func parseFyjInfo(fyjCount, fyjMoney string) (string, string) {
	fyjCount = strings.TrimSpace(fyjCount)
	fyjMoney = strings.TrimSpace(fyjMoney)
	if fyjCount == "" && fyjMoney == "" {
		return "", ""
	}
	return fyjCount, fyjMoney
}

// upsertDrawWithPool 插入或更新开奖记录（含奖池金额），返回 isNew=true 表示本次新增了数据
func upsertDrawWithPool(t, issue, red, blue, drawDate string, poolAmount int64, fyjCount, fyjMoney string) (isNew bool, err error) {
	res, err := DB.Exec(`INSERT INTO draw_results(type,issue,red_balls,blue_balls,draw_date,pool_amount,fyj_count,fyj_money)
		VALUES(?,?,?,?,?,?,?,?)
		ON DUPLICATE KEY UPDATE red_balls=VALUES(red_balls), blue_balls=VALUES(blue_balls), draw_date=VALUES(draw_date), pool_amount=VALUES(pool_amount), fyj_count=VALUES(fyj_count), fyj_money=VALUES(fyj_money)`,
		t, issue, red, blue, drawDate, poolAmount, fyjCount, fyjMoney)
	if err != nil {
		return false, err
	}
	n, _ := res.RowsAffected()
	return n > 0, nil
}

// upsertDraw 兼容旧调用（不含奖池金额）
func upsertDraw(t, issue, red, blue, drawDate string) (isNew bool, err error) {
	return upsertDrawWithPool(t, issue, red, blue, drawDate, 0, "", "")
}

// GetLatestDraw 取某彩种最近一期开奖
func GetLatestDraw(t string) (DrawResult, bool) {
	var d DrawResult
	err := DB.QueryRow("SELECT id,type,issue,red_balls,blue_balls,draw_date,pool_amount,fyj_count,fyj_money,created_at FROM draw_results WHERE type=? ORDER BY id DESC LIMIT 1",
		t).Scan(&d.ID, &d.Type, &d.Issue, &d.RedBalls, &d.BlueBalls, &d.DrawDate, &d.PoolAmount, &d.FyjCount, &d.FyjMoney, &d.CreatedAt)
	return d, err == nil
}

// GetDrawByIssue 按彩种+期号取开奖结果
func GetDrawByIssue(t, issue string) (DrawResult, bool) {
	var d DrawResult
	err := DB.QueryRow("SELECT id,type,issue,red_balls,blue_balls,draw_date,pool_amount,fyj_count,fyj_money,created_at FROM draw_results WHERE type=? AND issue=?",
		t, issue).Scan(&d.ID, &d.Type, &d.Issue, &d.RedBalls, &d.BlueBalls, &d.DrawDate, &d.PoolAmount, &d.FyjCount, &d.FyjMoney, &d.CreatedAt)
	return d, err == nil
}
