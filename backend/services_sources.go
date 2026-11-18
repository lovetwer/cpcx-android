package main

// ---------- 多数据源拉奖（灰鸟之外的源，主源优先 + 自动回退） ----------
//
// 主源：
//   - 双色球：500彩票网 datachart.500.com（HTML 表格，正则解析）
//     https://datachart.500.com/ssq/history/newinc/history.php?limit=30
//   - 大乐透：体彩官方 webapi.sporttery.cn（JSON，含奖池金额）
//     https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry?gameNo=85&pageSize=30
//
// 回退：灰鸟API（services_huiniao.go 中原实现，仅当主源请求失败时使用）
//
// 注意：票面 lotteries.issue 存的是开奖日期（yyyy-mm-dd），核对时按
// DATE(draw_date)=票面期号 匹配，所以只要 draw_date 正确即可对上；
// draw_results.issue 仅用于去重，双色球统一为 8 位年份前缀格式（2026132），
// 500 返回的 5 位格式（26132）会自动补 "20" 前缀。

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"
	"time"
)

var (
	re500Row  = regexp.MustCompile(`<tr class="t_tr1">(.*?)</tr>`)
	re500TD   = regexp.MustCompile(`<td[^>]*>(.*?)</td>`)
	reHTMLTag = regexp.MustCompile(`<[^>]+>`)
	reDate      = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}$`)
	reBallExact = regexp.MustCompile(`^\d{2}$`)
)

// httpGetText 通用文本请求（JSON/HTML 都能用），带浏览器 UA 与可选 Referer。
func httpGetText(url, referer string) (string, error) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("Accept", "text/html,application/json,*/*")
	if referer != "" {
		req.Header.Set("Referer", referer)
	}

	client := &http.Client{Timeout: 20 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	if len(body) == 0 {
		return "", fmt.Errorf("空响应")
	}
	return string(body), nil
}

// normalizeIssue 统一期号格式与库内既有数据一致：
// 双色球库内为 8 位（2026132），500 返回 5 位（26132），自动补 "20" 前缀；大乐透保持 5 位不变。
func normalizeIssue(t, issue string) string {
	issue = strings.TrimSpace(issue)
	if t == TypeSSQ && len(issue) == 5 && !strings.HasPrefix(issue, "20") {
		return "20" + issue
	}
	return issue
}

// ---------- 500彩票网（双色球 + 大乐透通用解析） ----------

// pullSSQ500 / pullDLT500：500彩票网作为两个彩种的备用入口
func pullSSQ500() (bool, error) { return pull500(TypeSSQ) }
func pullDLT500() (bool, error) { return pull500(TypeDLT) }

// drawRow 解析出的一期开奖
type drawRow struct {
	Issue    string
	Red      string
	Blue     string
	DrawDate string
	Pool     int64
}

// parse500Rows 把 500 彩票网历史开奖表格 HTML 解析成结构化行（不含入库，便于测试）。
func parse500Rows(t, html string) []drawRow {
	html = regexp.MustCompile(`<!--.*?-->`).ReplaceAllString(html, "")
	var out []drawRow
	for _, m := range re500Row.FindAllStringSubmatch(html, -1) {
		tds := re500TD.FindAllStringSubmatch(m[1], -1)
		cols := make([]string, 0, len(tds))
		for _, td := range tds {
			cols = append(cols, strings.TrimSpace(reHTMLTag.ReplaceAllString(td[1], "")))
		}
		if len(cols) < 12 {
			continue
		}
		drawDate := cols[len(cols)-1]
		if !reDate.MatchString(drawDate) { // 表头行 / 异常行
			continue
		}
		issue := normalizeIssue(t, cols[0])
		pool := parsePoolAmount(cols[len(cols)-7])

		var red, blue []string
		if t == TypeSSQ { // 第2~8列：6红+1蓝
			red, blue = cols[1:7], cols[7:8]
		} else { // 大乐透：第2~6列前区 + 第7~8列后区
			red, blue = cols[1:6], cols[6:8]
		}
		if issue == "" || len(red) == 0 {
			continue
		}
		ok := true
		for _, b := range append(append([]string{}, red...), blue...) {
			if !reBallExact.MatchString(b) {
				ok = false
				break
			}
		}
		if !ok {
			continue
		}
		out = append(out, drawRow{
			Issue:    issue,
			Red:      strings.Join(red, ","),
			Blue:     strings.Join(blue, ","),
			DrawDate: drawDate,
			Pool:     pool,
		})
	}
	return out
}

// pull500 解析 500 彩票网历史开奖表格页并入库。
func pull500(t string) (bool, error) {
	url := "https://datachart.500.com/" + t + "/history/newinc/history.php?limit=30"
	html, err := httpGetText(url, "https://datachart.500.com/")
	if err != nil {
		return false, fmt.Errorf("500彩票网请求失败: %w", err)
	}
	rows := parse500Rows(t, html)
	if len(rows) == 0 {
		return false, fmt.Errorf("500彩票网返回数据解析为空")
	}
	var anyNew bool
	for _, r := range rows {
		isNew, err := upsertDrawWithPool(t, r.Issue, r.Red, r.Blue, r.DrawDate, r.Pool, "", "")
		if err != nil {
			logErr("写入 %s 期号 %s 失败: %v", t, r.Issue, err)
			continue
		}
		if isNew {
			anyNew = true
		}
	}
	logInfo("500彩票网拉取 %s 成功（解析 %d 条，含奖池）", TypeName(t), len(rows))
	return anyNew, nil
}

// ---------- 体彩官方（大乐透） ----------

type sportteryResp struct {
	Success bool `json:"success"`
	Value   struct {
		List []struct {
			LotteryDrawNum    string `json:"lotteryDrawNum"`    // 期号 "26131"
			LotteryDrawResult string `json:"lotteryDrawResult"` // "02 11 18 19 29 04 07"
			LotteryDrawTime   string `json:"lotteryDrawTime"`   // "2026-11-16"
			PoolBalance       string `json:"poolBalanceAfterdraw"`
		} `json:"list"`
	} `json:"value"`
}

// pullDLTSporttery 体彩官方接口拉取大乐透（JSON，需带 Referer 否则被反爬拦截）。
func pullDLTSporttery() (bool, error) {
	url := "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry?gameNo=85&provinceId=0&pageSize=30&isVerify=1"
	body, err := httpGetText(url, "https://static.sporttery.cn/")
	if err != nil {
		return false, fmt.Errorf("体彩官方请求失败: %w", err)
	}
	var sr sportteryResp
	if err := json.Unmarshal([]byte(body), &sr); err != nil {
		return false, fmt.Errorf("解析体彩官方响应失败: %w", err)
	}
	if !sr.Success || len(sr.Value.List) == 0 {
		return false, fmt.Errorf("体彩官方返回数据为空或失败")
	}

	var anyNew bool
	for _, it := range sr.Value.List {
		balls := strings.Fields(it.LotteryDrawResult)
		if len(balls) < 7 || it.LotteryDrawNum == "" || !reDate.MatchString(it.LotteryDrawTime) {
			continue
		}
		red := strings.Join(balls[:5], ",")
		blue := strings.Join(balls[5:7], ",")
		isNew, err := upsertDrawWithPool(TypeDLT, it.LotteryDrawNum, red, blue, it.LotteryDrawTime, parsePoolAmount(it.PoolBalance), "", "")
		if err != nil {
			logErr("写入 %s 期号 %s 失败: %v", TypeDLT, it.LotteryDrawNum, err)
			continue
		}
		if isNew {
			anyNew = true
		}
	}
	logInfo("体彩官方拉取大乐透成功（%d 条，含奖池）", len(sr.Value.List))
	return anyNew, nil
}
