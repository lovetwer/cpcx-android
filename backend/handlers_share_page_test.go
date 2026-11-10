package main

import (
	"database/sql"
	"database/sql/driver"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
)

func testShareRequest(code string) *http.Request {
	u := "/share"
	if code != "" {
		u += "?code=" + url.QueryEscape(code)
	}
	return httptest.NewRequest("GET", u, nil)
}

// checkSharePage 断言落地页返回 200 的 HTML（share 页面不是 JSON，不能用 checkHandlerStatus）。
func checkSharePage(t *testing.T, w *httptest.ResponseRecorder) {
	t.Helper()
	if w.Code != http.StatusOK {
		t.Errorf("HTTP status = %d, want 200; body: %s", w.Code, w.Body.String())
	}
	if got := w.Header().Get("Content-Type"); !strings.HasPrefix(got, "text/html") {
		t.Errorf("Content-Type = %q, want text/html", got)
	}
	if !strings.HasPrefix(w.Body.String(), "<!DOCTYPE html>") {
		t.Error("response is not an HTML document")
	}
}

func setTestWebURL(t *testing.T) {
	t.Helper()
	previous := Cfg.WebURL
	Cfg.WebURL = "https://cpcx.800820882.xyz"
	t.Cleanup(func() { Cfg.WebURL = previous })
}

func TestSharePageValidCode(t *testing.T) {
	setTestWebURL(t)
	useHandlerSQL(t,
		handlerSQLStep{
			query: "SELECT lottery_ids FROM shares WHERE code=?",
			args:  []driver.Value{"abcd1234"},
			row:   []driver.Value{"11,22"},
		},
		handlerSQLStep{
			query: "SELECT type, red_balls, blue_balls FROM lotteries WHERE id IN",
			args:  []driver.Value{"11", "22"},
			rows: [][]driver.Value{
				{TypeSSQ, "06,05,04,03,02,01", "09"},
				{TypeDLT, "05,03,01,02,04", "11,07"},
			},
		},
	)
	w := runHandler(t, handleSharePage, testShareRequest("abcd1234"))
	checkSharePage(t, w)
	body := w.Body.String()
	wantDesc := "共2注（双色球1注、大乐透1注），双色球 01,02,03,04,05,06+09 等"
	if !strings.Contains(body, `content="`+wantDesc+`"`) {
		t.Errorf("og:description missing %q in:\n%s", wantDesc, body)
	}
	if !strings.Contains(body, `location.replace("https://cpcx.800820882.xyz/#/share?code=abcd1234")`) {
		t.Error("missing JS redirect to SPA share route")
	}
	if !strings.Contains(body, `content="https://cpcx.800820882.xyz/og-image.png"`) {
		t.Error("og:image must point to white-background og-image.png")
	}
	if got := w.Header().Get("X-Robots-Tag"); got != "noindex" {
		t.Errorf("X-Robots-Tag = %q, want noindex", got)
	}
}

func TestSharePageSingleTicket(t *testing.T) {
	setTestWebURL(t)
	useHandlerSQL(t,
		handlerSQLStep{
			query: "SELECT lottery_ids FROM shares WHERE code=?",
			row:   []driver.Value{"7"},
		},
		handlerSQLStep{
			query: "SELECT type, red_balls, blue_balls FROM lotteries WHERE id IN",
			args:  []driver.Value{"7"},
			rows:  [][]driver.Value{{TypeDLT, "24, 12 35,2,1", "3, 5"}},
		},
	)
	w := runHandler(t, handleSharePage, testShareRequest("deadbeef"))
	checkSharePage(t, w)
	wantDesc := "共1注（大乐透1注），大乐透 01,02,12,24,35+03,05"
	if !strings.Contains(w.Body.String(), `content="`+wantDesc+`"`) {
		t.Errorf("og:description missing %q in:\n%s", wantDesc, w.Body.String())
	}
	// 单张票不应带 "等"
	if strings.Contains(w.Body.String(), "等\"") {
		t.Error("single-ticket summary should not end with 等")
	}
}

func TestSharePageUnknownCode(t *testing.T) {
	setTestWebURL(t)
	useHandlerSQL(t,
		handlerSQLStep{
			query: "SELECT lottery_ids FROM shares WHERE code=?",
			args:  []driver.Value{"00000000"},
			err:   sql.ErrNoRows,
		},
	)
	w := runHandler(t, handleSharePage, testShareRequest("00000000"))
	checkSharePage(t, w)
	if !strings.Contains(w.Body.String(), `content="分享不存在或已过期"`) {
		t.Errorf("unknown code should say 分享不存在或已过期:\n%s", w.Body.String())
	}
}

func TestSharePageInvalidCode(t *testing.T) {
	setTestWebURL(t)
	// 零个 SQL 步骤：非法分享码不得触库，否则 stub panic 令测试失败
	useHandlerSQL(t)
	w := runHandler(t, handleSharePage, testShareRequest("x'; drop--"))
	checkSharePage(t, w)
	body := w.Body.String()
	if !strings.Contains(body, `content="分享链接无效"`) {
		t.Errorf("invalid code should say 分享链接无效:\n%s", body)
	}
	if strings.Contains(body, "location.replace(\"https://cpcx.800820882.xyz/#/share?code=") {
		t.Error("invalid code must not redirect with code param")
	}
}

func TestSharePageEmptyCode(t *testing.T) {
	setTestWebURL(t)
	useHandlerSQL(t)
	w := runHandler(t, handleSharePage, testShareRequest(""))
	checkSharePage(t, w)
	body := w.Body.String()
	if !strings.Contains(body, `content="记录双色球、大乐透彩票，开奖自动核对、中奖即时通知。"`) {
		t.Errorf("empty code should use default description:\n%s", body)
	}
	if strings.Contains(body, "?code=") {
		t.Error("empty code must not produce a code redirect")
	}
}

func TestSharePageSummaryHelpers(t *testing.T) {
	if !isShareCode("AbCd1234") || isShareCode("") || isShareCode("xyz") || isShareCode(strings.Repeat("a", 33)) {
		t.Error("isShareCode hex validation wrong")
	}
	if got := parseLotteryIDs(" 1, x, 2 ,  ,3"); strings.Join(got, ",") != "1,2,3" {
		t.Errorf("parseLotteryIDs = %v", got)
	}
	if got := formatTicketPreview(TypeSSQ, "3 1 5", ""); got != "01,03,05" {
		t.Errorf("formatTicketPreview no-blue = %q", got)
	}
}
