package main

import (
	"fmt"
	"net/http"
	"strings"
)

type lotteryInput struct {
	Type      string `json:"type"`
	Issue     string `json:"issue"`
	RedBalls  string `json:"red_balls"`
	BlueBalls string `json:"blue_balls"`
}

type createLotteryReq struct {
	Type       string `json:"type"`
	Issue      string `json:"issue"`
	RedBalls   string `json:"red_balls"`
	BlueBalls  string `json:"blue_balls"`
	PlayType   string `json:"play_type"`
	Multiple   int    `json:"multiple"`
	BankerRed  string `json:"banker_red"`
	BankerBlue string `json:"banker_blue"`
}

type batchReq struct {
	Items []lotteryInput `json:"items"`
}

// handleCreateLottery 单张录入
func handleCreateLottery(w http.ResponseWriter, r *http.Request) {
	var req createLotteryReq
	if err := readJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, "请求格式错误")
		return
	}
	issue := strings.TrimSpace(req.Issue)
	if issue == "" {
		// 期号未填：回退到该彩种最近一期已开奖的开奖日期，避免空期号直接被拒
		if latest, ok := latestDrawDate(req.Type); ok {
			issue = latest
		} else {
			writeError(w, http.StatusBadRequest, "期号不能为空")
			return
		}
	}
	playType := req.PlayType
	if playType == "" {
		playType = "single"
	}
	bankerRed, bankerBlue := normalizeBalls(req.BankerRed), normalizeBalls(req.BankerBlue)
	red, blue, ok := validateTicket(req.Type, playType, req.RedBalls, req.BlueBalls, bankerRed, bankerBlue)
	if !ok {
		writeError(w, http.StatusBadRequest, "号码不符合玩法规则：双色球6红+1蓝(复式红≤20/蓝≤16)，大乐透5前+2后(复式前≤12/后≤12)")
		return
	}
	mult := req.Multiple
	if mult < 1 {
		mult = 1
	}
	bets := ticketBets(req.Type, red, blue, bankerRed, bankerBlue)
	lot := Lottery{
		UserID: currentUserID(r), Type: req.Type, Issue: issue, RedBalls: red, BlueBalls: blue,
		PlayType: playType, Multiple: mult, BankerRed: bankerRed, BankerBlue: bankerBlue,
		Bets: bets, Status: StatusPending,
	}
	if err := insertLottery(&lot); err != nil {
		writeError(w, http.StatusConflict, err.Error())
		return
	}
	// 录入后立即尝试核对（若该期已开奖）
	checkOne(&lot)
	writeJSON(w, http.StatusOK, map[string]interface{}{"ok": true, "lottery": lot})
}

// handleBatchLottery 批量录入
func handleBatchLottery(w http.ResponseWriter, r *http.Request) {
	var req batchReq
	if err := readJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, "请求格式错误")
		return
	}
	if len(req.Items) == 0 {
		writeError(w, http.StatusBadRequest, "列表为空")
		return
	}
	uid := currentUserID(r)
	var okCount, failCount int
	var fails []string
	for _, it := range req.Items {
		issue := strings.TrimSpace(it.Issue)
		if issue == "" {
			// OCR 未识别到期号：回退到该彩种最近一期已开奖的开奖日期，保证可入库且能参与核对
			if latest, ok := latestDrawDate(it.Type); ok {
				issue = latest
			} else {
				failCount++
				fails = append(fails, "期号缺失且无历史开奖可回退")
				continue
			}
		}
		red, blue, ok := validateBalls(it.Type, it.RedBalls, it.BlueBalls)
		if !ok {
			failCount++
			fails = append(fails, "期号"+issue+" 号码格式错误")
			continue
		}
		lot := Lottery{
			UserID: uid, Type: it.Type, Issue: issue, RedBalls: red, BlueBalls: blue,
			PlayType: "single", Multiple: 1, Bets: 1, Status: StatusPending,
		}
		if err := insertLottery(&lot); err != nil {
			failCount++
			fails = append(fails, "期号"+it.Issue+" "+err.Error())
			continue
		}
		// 录入后立即尝试核对（若该期已开奖）
		checkOne(&lot)
		okCount++
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"ok": true, "inserted": okCount, "failed": failCount, "errors": fails,
	})
}

// handleListLottery 查询（当前用户）
func handleListLottery(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	uid := currentUserID(r)
	var conds []string
	var args []interface{}
	conds = append(conds, "user_id=?")
	args = append(args, uid)
	if t := q.Get("type"); t != "" {
		conds = append(conds, "type=?")
		args = append(args, t)
	}
	if s := q.Get("status"); s != "" {
		conds = append(conds, "status=?")
		args = append(args, s)
	}
	if issue := q.Get("issue"); issue != "" {
		conds = append(conds, "issue LIKE ?")
		args = append(args, "%"+issue+"%")
	}
	if kw := q.Get("keyword"); kw != "" {
		conds = append(conds, "(red_balls LIKE ? OR blue_balls LIKE ?)")
		args = append(args, "%"+kw+"%", "%"+kw+"%")
	}
	where := strings.Join(conds, " AND ")
	// 排序：issue 为 yyyy-mm-dd 日期格式的按日期倒序；非日期格式（期号）按 created_at 倒序
	orderSQL := " CASE WHEN issue LIKE '____-__-__' THEN issue ELSE '0000-00-00' END DESC, created_at DESC, id DESC"
	rows, err := DB.Query("SELECT id,user_id,type,issue,red_balls,blue_balls,play_type,multiple,banker_red,banker_blue,bets,status,prize_tier,created_at FROM lotteries WHERE "+where+" ORDER BY "+orderSQL+" LIMIT 500", args...)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "查询失败")
		return
	}
	defer rows.Close()
	var list []Lottery
	for rows.Next() {
		var l Lottery
		if err := rows.Scan(&l.ID, &l.UserID, &l.Type, &l.Issue, &l.RedBalls, &l.BlueBalls, &l.PlayType, &l.Multiple, &l.BankerRed, &l.BankerBlue, &l.Bets, &l.Status, &l.PrizeTier, &l.CreatedAt); err != nil {
			continue
		}
		list = append(list, l)
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{"ok": true, "list": list})
}

// handleDeleteLottery 删除（仅本人）
func handleDeleteLottery(w http.ResponseWriter, r *http.Request) {
	id := atoiOrZero(Param(r, "id"))
	uid := currentUserID(r)
	res, err := DB.Exec("DELETE FROM lotteries WHERE id=? AND user_id=?", id, uid)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "删除失败")
		return
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		writeError(w, http.StatusNotFound, "未找到该彩票或无权限")
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{"ok": true})
}

// handleUpdateStatus 修改状态（如已兑奖）
func handleUpdateStatus(w http.ResponseWriter, r *http.Request) {
	id := atoiOrZero(Param(r, "id"))
	var body struct {
		Status string `json:"status"`
	}
	if err := readJSON(r, &body); err != nil || body.Status == "" {
		writeError(w, http.StatusBadRequest, "状态不能为空")
		return
	}
	uid := currentUserID(r)
	res, err := DB.Exec("UPDATE lotteries SET status=? WHERE id=? AND user_id=?", body.Status, id, uid)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "更新失败")
		return
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		writeError(w, http.StatusNotFound, "未找到该彩票或无权限")
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{"ok": true})
}

// ---------- DB 辅助 ----------

func insertLottery(l *Lottery) error {
	// 查重：同一用户 + 同一彩种 + 同一期号 + 同一号码组合，不允许重复保存
	var existing int64
	DB.QueryRow("SELECT COUNT(*) FROM lotteries WHERE user_id=? AND type=? AND issue=? AND red_balls=? AND blue_balls=?",
		l.UserID, l.Type, l.Issue, l.RedBalls, l.BlueBalls).Scan(&existing)
	if existing > 0 {
		return fmt.Errorf("该期彩票已保存过，请勿重复录入")
	}
	res, err := DB.Exec("INSERT INTO lotteries(user_id,type,issue,red_balls,blue_balls,play_type,multiple,banker_red,banker_blue,bets,status) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
		l.UserID, l.Type, l.Issue, l.RedBalls, l.BlueBalls, l.PlayType, l.Multiple, l.BankerRed, l.BankerBlue, l.Bets, l.Status)
	if err != nil {
		return err
	}
	l.ID, _ = res.LastInsertId()
	// 回填数据库生成的创建时间，避免录入接口返回零值
	_ = DB.QueryRow("SELECT created_at FROM lotteries WHERE id=?", l.ID).Scan(&l.CreatedAt)
	return nil
}

// latestDrawDate 返回该彩种最近一期已开奖的开奖日期（draw_results.draw_date）。
// 系统约定 lottery.issue 统一存开奖日期（yyyy-mm-dd），OCR 未识别到期号时用它回退，
// 保证票据入库和核对都按日期走，不再混进“期号数字”。
func latestDrawDate(t string) (string, bool) {
	var date string
	err := DB.QueryRow("SELECT draw_date FROM draw_results WHERE type=? ORDER BY id DESC LIMIT 1", t).Scan(&date)
	if err != nil || strings.TrimSpace(date) == "" {
		return "", false
	}
	return strings.TrimSpace(date), true
}

func atoiOrZero(s string) int64 {
	n, err := atoi(s)
	if err != nil {
		return 0
	}
	return n
}
