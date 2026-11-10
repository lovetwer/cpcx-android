package main

import (
	"bytes"
	"database/sql/driver"
	"encoding/json"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestRecognizeSingleMetadata(t *testing.T) {
	cases := []struct {
		typ, red, blue string
	}{
		{TypeSSQ, "1,2,3,4,5,6", "1"},
		{TypeDLT, "1,2,3,4,5", "1,2"},
	}
	for _, tc := range cases {
		for _, dryRun := range []bool{false, true} {
			name := tc.typ + "/save"
			if dryRun {
				name = tc.typ + "/dry_run"
			}
			t.Run(name, func(t *testing.T) {
				image := []byte{0xff, 0xd8, 0xff}
				previous := recognizeLottery
				calls := 0
				recognizeLottery = func(buf []byte, filename string) ([]OCRResult, error) {
					calls++
					if !bytes.Equal(buf, image) || filename != "upload.jpg" {
						t.Errorf("unexpected OCR input: %v, %q", buf, filename)
					}
					return []OCRResult{{Type: tc.typ, Issue: "2026093", RedBalls: tc.red, BlueBalls: tc.blue}}, nil
				}
				t.Cleanup(func() {
					recognizeLottery = previous
					if calls != 1 {
						t.Errorf("OCR calls = %d, want 1", calls)
					}
				})
				steps := []handlerSQLStep{{query: "SELECT draw_date FROM draw_results WHERE type=? AND issue=? ORDER BY id DESC LIMIT 1", args: []driver.Value{tc.typ, "2026093"}, row: []driver.Value{"2026-08-20"}}}
				red, blue := normalizeBalls(tc.red), normalizeBalls(tc.blue)
				if !dryRun {
					steps = append(steps, handlerLotterySteps(tc.typ, red, blue, "", "", 1, 1, false)...)
				}
				useHandlerSQL(t, steps...)

				// Tiny multipart input stays in memory: no temporary image or OCR log files.
				var body bytes.Buffer
				form := multipart.NewWriter(&body)
				part, err := form.CreateFormFile("image", "ticket.jpg")
				if err != nil {
					t.Fatal(err)
				}
				if _, err := part.Write(image); err != nil {
					t.Fatal(err)
				}
				if dryRun {
					if err := form.WriteField("dry_run", "1"); err != nil {
						t.Fatal(err)
					}
				}
				if err := form.Close(); err != nil {
					t.Fatal(err)
				}
				r := httptest.NewRequest(http.MethodPost, "/api/lottery/recognize", &body)
				r.Header.Set("Content-Type", form.FormDataContentType())
				w := runHandler(t, handleRecognize, r.WithContext(withUserID(r, 42)))
				checkHandlerStatus(t, w, http.StatusOK)
				var response struct {
					DryRun   bool                     `json:"dry_run"`
					Inserted []map[string]interface{} `json:"inserted"`
					Parsed   []map[string]interface{} `json:"parsed"`
					Skipped  []map[string]interface{} `json:"skipped"`
					Message  string                   `json:"message"`
				}
				if err := json.Unmarshal(w.Body.Bytes(), &response); err != nil {
					t.Fatal(err)
				}
				if response.DryRun != dryRun || len(response.Skipped) != 0 {
					t.Errorf("unexpected OCR response: %s", w.Body.String())
				}
				items := response.Inserted
				if dryRun {
					items = response.Parsed
					if len(response.Inserted) != 0 || response.Message != "识别完成，请在录入页确认" {
						t.Errorf("dry-run response changed: %s", w.Body.String())
					}
				} else if len(response.Parsed) != 0 || response.Message != "识别完成，已自动录入" {
					t.Errorf("save response changed: %s", w.Body.String())
				}
				if len(items) != 1 {
					t.Fatalf("expected one OCR ticket: %s", w.Body.String())
				}
				item := items[0]
				if item["type"] != tc.typ || item["issue"] != "2026-08-20" || item["red_balls"] != red || item["blue_balls"] != blue {
					t.Errorf("incorrect OCR ticket: %v", item)
				}
				if !dryRun && (item["id"] != float64(42) || item["status"] != StatusPending) {
					t.Errorf("incorrect saved OCR ticket: %v", item)
				}
			})
		}
	}
}
