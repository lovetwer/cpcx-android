package main

import (
	"database/sql/driver"
	"encoding/json"
	"net/http"
	"testing"
)

func handlerLotterySteps(typ, red, blue, bankerRed, bankerBlue string, bets, multiple int, drawn bool) []handlerSQLStep {
	steps := []handlerSQLStep{
		{query: "SELECT COUNT(*) FROM lotteries WHERE user_id=? AND type=? AND issue=? AND red_balls=? AND blue_balls=?", args: []driver.Value{int64(42), typ, "2026-08-20", red, blue}, row: []driver.Value{int64(0)}},
		{exec: true, query: "INSERT INTO lotteries(user_id,type,issue,red_balls,blue_balls,play_type,multiple,banker_red,banker_blue,bets,status) VALUES(?,?,?,?,?,?,?,?,?,?,?)", args: []driver.Value{int64(42), typ, "2026-08-20", red, blue, "single", int64(multiple), bankerRed, bankerBlue, int64(bets), StatusPending}},
		{query: "SELECT created_at FROM lotteries WHERE id=?", args: []driver.Value{int64(42)}, row: []driver.Value{handlerTestTime}},
		{query: "SELECT id,type,issue,red_balls,blue_balls,draw_date,pool_amount,fyj_count,fyj_money FROM draw_results", args: []driver.Value{typ, "2026-08-20", "2026-08-20"}},
	}
	if bankerRed != "" || bankerBlue != "" {
		steps[1].args[5] = "banker"
	}
	if drawn {
		drawRed, drawBlue := "01,02,03,04,05,06", "01"
		if typ == TypeDLT {
			drawRed, drawBlue = "01,02,03,04,05", "01,02"
		}
		steps[3].row = []driver.Value{int64(1), typ, "2026093", drawRed, drawBlue, "2026-08-20", int64(0), "", ""}
		steps = append(steps,
			handlerSQLStep{query: "SELECT prize_tier FROM lotteries WHERE id=?", args: []driver.Value{int64(42)}, row: []driver.Value{""}},
			handlerSQLStep{exec: true, query: "UPDATE lotteries SET status=?, prize_tier=? WHERE id=?", args: []driver.Value{StatusWin, "一等奖", int64(42)}},
			handlerSQLStep{query: handlerUserQuery, args: []driver.Value{int64(42)}, row: handlerUserRow("test-user")},
		)
	}
	return steps
}

func TestBatchLotterySingleMetadata(t *testing.T) {
	cases := []struct {
		typ, red, blue string
	}{
		{TypeSSQ, "01,02,03,04,05,06", "01"},
		{TypeDLT, "01,02,03,04,05", "01,02"},
	}
	for _, tc := range cases {
		t.Run(tc.typ, func(t *testing.T) {
			useHandlerSQL(t, handlerLotterySteps(tc.typ, tc.red, tc.blue, "", "", 1, 1, false)...)
			body, err := json.Marshal(batchReq{Items: []lotteryInput{{Type: tc.typ, Issue: "2026-08-20", RedBalls: tc.red, BlueBalls: tc.blue}}})
			if err != nil {
				t.Fatal(err)
			}
			w := runHandler(t, handleBatchLottery, handlerJSONRequest(http.MethodPost, "/api/lottery/batch", string(body)))
			checkHandlerStatus(t, w, http.StatusOK)
			var response struct {
				Inserted int      `json:"inserted"`
				Failed   int      `json:"failed"`
				Errors   []string `json:"errors"`
			}
			if err := json.Unmarshal(w.Body.Bytes(), &response); err != nil {
				t.Fatal(err)
			}
			if response.Inserted != 1 || response.Failed != 0 || len(response.Errors) != 0 {
				t.Errorf("batch response changed: %s", w.Body.String())
			}
		})
	}
}

func TestCreateLotteryNormalizesBankers(t *testing.T) {
	cases := []struct {
		name, typ, red, blue, bankerRed, bankerBlue, wantRed, wantBlue string
		bets                                                           int
	}{
		{"ssq_six_not_twenty_one_bets", TypeSSQ, "7 6 5 4 3 2 1", "1", "1", "", "01", "", 6},
		{"ssq_delimiters_and_sorting", TypeSSQ, "1,2,3,4,5,6,7", "1", " 3+1/2 ", "", "01,02,03", "", 4},
		{"dlt_blue_banker", TypeDLT, "1,2,3,4,5", "1,2,3", "", "1", "", "01", 2},
		{"dlt_both_bankers", TypeDLT, "1,2,3,4,5,6", "1,2,3", " 2;1 ", " 1 ", "01,02", "01", 8},
		{"already_normalized", TypeSSQ, "01,02,03,04,05,06,07", "01", "01", "", "01", "", 6},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			red, blue := normalizeBalls(tc.red), normalizeBalls(tc.blue)
			// The stub checks persisted bankers/bets and the immediate first-prize update.
			useHandlerSQL(t, handlerLotterySteps(tc.typ, red, blue, tc.wantRed, tc.wantBlue, tc.bets, 2, true)...)
			body, err := json.Marshal(createLotteryReq{
				Type: tc.typ, Issue: "2026-08-20", RedBalls: tc.red, BlueBalls: tc.blue,
				PlayType: "banker", Multiple: 2, BankerRed: tc.bankerRed, BankerBlue: tc.bankerBlue,
			})
			if err != nil {
				t.Fatal(err)
			}
			w := runHandler(t, handleCreateLottery, handlerJSONRequest(http.MethodPost, "/api/lottery", string(body)))
			checkHandlerStatus(t, w, http.StatusOK)
			var response struct {
				Lottery Lottery `json:"lottery"`
			}
			if err := json.Unmarshal(w.Body.Bytes(), &response); err != nil {
				t.Fatal(err)
			}
			lot := response.Lottery
			if lot.ID != 42 || lot.BankerRed != tc.wantRed || lot.BankerBlue != tc.wantBlue || lot.Bets != tc.bets || lot.Multiple != 2 || lot.PlayType != "banker" || lot.Status != StatusWin {
				t.Errorf("incorrect lottery response: %+v", lot)
			}
			if combos := enumerateTicketCombos(lot.Type, lot.RedBalls, lot.BlueBalls, lot.BankerRed, lot.BankerBlue); len(combos) != tc.bets {
				t.Errorf("saved ticket expands to %d bets, want %d", len(combos), tc.bets)
			}
		})
	}
}

func TestCreateLotteryRejectsInvalidBankers(t *testing.T) {
	for _, body := range []string{
		`{"type":"ssq","issue":"2026-08-20","red_balls":"1,2,3,4,5,6,7","blue_balls":"1","play_type":"banker","banker_red":"8"}`,
		`{"type":"dlt","issue":"2026-08-20","red_balls":"1,2,3,4,5","blue_balls":"1,2,3","play_type":"banker","banker_blue":"4"}`,
	} {
		t.Run(body, func(t *testing.T) {
			useHandlerSQL(t) // Invalid normalized bankers must never be saved.
			w := runHandler(t, handleCreateLottery, handlerJSONRequest(http.MethodPost, "/api/lottery", body))
			checkHandlerStatus(t, w, http.StatusBadRequest)
		})
	}
}
