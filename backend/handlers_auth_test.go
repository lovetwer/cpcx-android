package main

import (
	"database/sql"
	"database/sql/driver"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/go-sql-driver/mysql"
)

const handlerDeviceQuery = "SELECT id,username,device_id,email,created_at FROM users WHERE device_id=?"
const handlerUserQuery = "SELECT id,username,device_id,email,created_at FROM users WHERE id=?"
const handlerUserInsert = "INSERT INTO users(username,password,device_id,email) VALUES(?,?,?,?)"

func TestDeviceLoginDatabaseFailures(t *testing.T) {
	outage := errors.New("database unavailable")
	missing := handlerSQLStep{query: handlerDeviceQuery, args: []driver.Value{"test-device"}}
	insertFailure := func(err error) handlerSQLStep {
		return handlerSQLStep{exec: true, query: handlerUserInsert, err: err}
	}
	collision := &mysql.MySQLError{Number: 1062, Message: "Duplicate entry 'name' for key 'uniq_username'"}
	cases := []struct {
		name  string
		steps []handlerSQLStep
	}{
		{"select_failure_no_insert", []handlerSQLStep{{query: handlerDeviceQuery, err: outage}}},
		{"wrapped_select_failure_no_insert", []handlerSQLStep{{query: handlerDeviceQuery, err: fmt.Errorf("lookup: %w", outage)}}},
		{"insert_failure_no_retry", []handlerSQLStep{missing, insertFailure(outage)}},
		{"mysql_data_error_no_retry", []handlerSQLStep{missing, insertFailure(&mysql.MySQLError{Number: 1406, Message: "Data too long for column 'username'"})}},
		{"mysql_permission_error_no_retry", []handlerSQLStep{missing, insertFailure(&mysql.MySQLError{Number: 1142, Message: "INSERT command denied"})}},
		{"non_username_duplicate_no_retry", []handlerSQLStep{missing, insertFailure(&mysql.MySQLError{Number: 1062, Message: "Duplicate entry 'test-device' for key 'uniq_device'"})}},
		{"primary_key_duplicate_no_retry", []handlerSQLStep{missing, insertFailure(&mysql.MySQLError{Number: 1062, Message: "Duplicate entry '42' for key 'PRIMARY'"})}},
		{"error_text_is_not_mysql_collision", []handlerSQLStep{missing, insertFailure(errors.New(collision.Error()))}},
		{"collision_then_outage_stops", []handlerSQLStep{missing, insertFailure(collision), insertFailure(outage)}},
		// Three retries after the first attempt: a persistent collision must stop at four INSERTs.
		{"username_collision_retry_limit", []handlerSQLStep{missing, insertFailure(collision), insertFailure(collision), insertFailure(collision), insertFailure(collision)}},
		{"last_insert_id_failure", []handlerSQLStep{missing, {exec: true, query: handlerUserInsert, result: handlerSQLResult{idErr: outage}}}},
		{"created_user_missing", []handlerSQLStep{missing, {exec: true, query: handlerUserInsert}, {query: handlerUserQuery, args: []driver.Value{int64(42)}}}},
		{"created_user_lookup_failure", []handlerSQLStep{missing, {exec: true, query: handlerUserInsert}, {query: handlerUserQuery, err: outage}}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			useHandlerSQL(t, tc.steps...)
			start := time.Now()
			w := runHandler(t, handleDeviceLogin, handlerJSONRequest(http.MethodPost, "/api/login/device", `{"device_id":"test-device","device_model":"TestPhone"}`))
			if elapsed := time.Since(start); elapsed > time.Second {
				t.Errorf("database failure took %s, want a prompt response within 1s", elapsed)
			}
			checkHandlerStatus(t, w, http.StatusInternalServerError)
			var body map[string]interface{}
			if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
				t.Fatal(err)
			}
			if _, ok := body["token"]; ok {
				t.Error("database failure must not issue a token")
			}
		})
	}
}

func TestDeviceLoginSuccess(t *testing.T) {
	cases := []struct {
		name       string
		existing   bool
		wrapped    bool
		collisions int
	}{
		{name: "existing_user", existing: true},
		{name: "create_missing_user"},
		{name: "wrapped_no_rows", wrapped: true},
		{name: "username_collision_then_success", collisions: 1},
		{name: "success_on_third_retry", collisions: 3},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			username := "TestPhone_existing"
			first := handlerSQLStep{query: handlerDeviceQuery, args: []driver.Value{"test-device"}}
			if tc.existing {
				first.row = handlerUserRow(username)
			} else if tc.wrapped {
				first.err = fmt.Errorf("lookup: %w", sql.ErrNoRows)
			}
			steps := []handlerSQLStep{first}
			if !tc.existing {
				var previousName, passwordHash string
				checkInsert := func(args []driver.NamedValue) {
					if len(args) != 4 {
						t.Fatalf("INSERT args = %d, want 4", len(args))
					}
					username = args[0].Value.(string)
					if !strings.HasPrefix(username, "TestPhone_") || username == previousName {
						t.Errorf("invalid or reused username: %q", username)
					}
					previousName = username
					hash := args[1].Value.(string)
					if len(hash) != 97 || hash[32] != ':' {
						t.Errorf("invalid password hash: %q", hash)
					}
					if passwordHash != "" && passwordHash != hash {
						t.Error("password hash changed between retries")
					}
					passwordHash = hash
					if args[2].Value != "test-device" || args[3].Value != "test@example.invalid" {
						t.Errorf("device/email not preserved: %v", args)
					}
				}
				for i := 0; i < tc.collisions; i++ {
					key := "uniq_username"
					if i%2 == 0 {
						key = "users.uniq_username"
					}
					err := fmt.Errorf("insert: %w", &mysql.MySQLError{Number: 1062, Message: "Duplicate entry 'name' for key '" + key + "'"})
					steps = append(steps, handlerSQLStep{exec: true, query: handlerUserInsert, err: err, check: checkInsert})
				}
				row := handlerUserRow("")
				steps = append(steps,
					handlerSQLStep{exec: true, query: handlerUserInsert, check: checkInsert},
					handlerSQLStep{query: handlerUserQuery, args: []driver.Value{int64(42)}, row: row, check: func([]driver.NamedValue) { row[1] = username }},
				)
			}
			useHandlerSQL(t, steps...)
			w := runHandler(t, handleDeviceLogin, handlerJSONRequest(http.MethodPost, "/api/login/device", `{"device_id":"test-device","device_model":"TestPhone","email":"test@example.invalid"}`))
			checkHandlerStatus(t, w, http.StatusOK)
			var body authResp
			if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
				t.Fatal(err)
			}
			if body.User == nil || body.User.ID != 42 || body.User.Username != username {
				t.Fatalf("unexpected user: %+v", body.User)
			}
			if id, ok := parseToken(body.Token); !ok || id != 42 {
				t.Errorf("token does not authenticate returned user: %q", body.Token)
			}
		})
	}
}

func TestUpdateMeMissingUser(t *testing.T) {
	for _, body := range []string{`{"username":"new-name"}`, `{"email":"new@example.invalid"}`, `{"password":"new-password"}`, `invalid-json`} {
		t.Run(body, func(t *testing.T) {
			useHandlerSQL(t, handlerSQLStep{query: handlerUserQuery, args: []driver.Value{int64(42)}})
			w := runHandler(t, handleUpdateMe, handlerJSONRequest(http.MethodPut, "/api/me", body))
			checkHandlerStatus(t, w, http.StatusUnauthorized)
		})
	}
}

func TestUpdateMeReusesCurrentUser(t *testing.T) {
	useHandlerSQL(t,
		handlerSQLStep{query: handlerUserQuery, args: []driver.Value{int64(42)}, row: handlerUserRow("old-name")},
		handlerSQLStep{query: "SELECT COUNT(*) FROM users WHERE username=? AND id<>?", args: []driver.Value{"new-name", int64(42)}, row: []driver.Value{int64(0)}},
		handlerSQLStep{query: "SELECT id,username,password,device_id,email,created_at FROM users WHERE username=?", args: []driver.Value{"old-name"}, row: []driver.Value{int64(42), "old-name", "", "test-device", "test@example.invalid", handlerTestTime}},
		handlerSQLStep{exec: true, query: "UPDATE users SET username=?,password=? WHERE id=?", check: func(args []driver.NamedValue) {
			if len(args) != 3 || args[0].Value != "new-name" || args[2].Value != int64(42) || !checkPassword("new-password", args[1].Value.(string)) {
				t.Errorf("unexpected update args: %v", args)
			}
		}},
		handlerSQLStep{query: handlerUserQuery, args: []driver.Value{int64(42)}, row: handlerUserRow("new-name")},
	)
	w := runHandler(t, handleUpdateMe, handlerJSONRequest(http.MethodPut, "/api/me", `{"username":"new-name","password":"new-password"}`))
	checkHandlerStatus(t, w, http.StatusOK)
	var body struct {
		User *User `json:"user"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if body.User == nil || body.User.Username != "new-name" {
		t.Errorf("updated user missing from response: %+v", body.User)
	}
}
