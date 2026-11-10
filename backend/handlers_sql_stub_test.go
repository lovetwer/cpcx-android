package main

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
	"time"
)

// A strict, in-memory SQL script: no real driver, connection, or external writes.
// Unexpected calls panic so an unbounded INSERT retry fails instead of hanging.
// Tests replacing DB or recognizeLottery must not run in parallel.
type handlerSQLStep struct {
	exec   bool
	query  string
	args   []driver.Value
	row    []driver.Value   // single-row convenience; ignored when rows is set
	rows   [][]driver.Value // multi-row result for IN queries; empty slice = zero matches
	err    error
	result driver.Result
	check  func([]driver.NamedValue)
}

type handlerSQLStub struct {
	t     *testing.T
	steps []handlerSQLStep
	next  int
}

func useHandlerSQL(t *testing.T, steps ...handlerSQLStep) {
	t.Helper()
	stub := &handlerSQLStub{t: t, steps: steps}
	db := sql.OpenDB(stub)
	previous := DB
	DB = db
	t.Cleanup(func() {
		DB = previous
		if err := db.Close(); err != nil {
			t.Errorf("close stub DB: %v", err)
		}
		if stub.next != len(stub.steps) {
			t.Errorf("SQL calls = %d, want %d", stub.next, len(stub.steps))
		}
	})
}

func (s *handlerSQLStub) Connect(context.Context) (driver.Conn, error) {
	return &handlerSQLConn{stub: s}, nil
}
func (s *handlerSQLStub) Driver() driver.Driver { return handlerSQLDriver{} }

type handlerSQLDriver struct{}

func (handlerSQLDriver) Open(string) (driver.Conn, error) {
	return nil, fmt.Errorf("stub requires its in-memory connector")
}

type handlerSQLConn struct{ stub *handlerSQLStub }

func (c *handlerSQLConn) Prepare(string) (driver.Stmt, error) {
	return nil, fmt.Errorf("unexpected Prepare")
}
func (c *handlerSQLConn) Close() error { return nil }
func (c *handlerSQLConn) Begin() (driver.Tx, error) {
	return nil, fmt.Errorf("unexpected Begin")
}
func (c *handlerSQLConn) take(exec bool, query string, args []driver.NamedValue) handlerSQLStep {
	s := c.stub
	if s.next >= len(s.steps) {
		panic(fmt.Sprintf("unexpected SQL call %d: %s", s.next+1, query))
	}
	step := s.steps[s.next]
	s.next++
	if step.exec != exec || !strings.HasPrefix(query, step.query) {
		panic(fmt.Sprintf("SQL call %d = %q (exec=%v), want %q (exec=%v)", s.next, query, exec, step.query, step.exec))
	}
	if step.args != nil {
		values := make([]driver.Value, len(args))
		for i, arg := range args {
			values[i] = arg.Value
		}
		if !reflect.DeepEqual(values, step.args) {
			s.t.Errorf("SQL args for %q = %#v, want %#v", query, values, step.args)
		}
	}
	if step.check != nil {
		step.check(args)
	}
	return step
}
func (c *handlerSQLConn) QueryContext(_ context.Context, query string, args []driver.NamedValue) (driver.Rows, error) {
	step := c.take(false, query, args)
	if step.err != nil {
		return nil, step.err
	}
	batch := step.rows
	if batch == nil && step.row != nil {
		batch = [][]driver.Value{step.row}
	}
	return &handlerSQLRows{rows: batch}, nil
}
func (c *handlerSQLConn) ExecContext(_ context.Context, query string, args []driver.NamedValue) (driver.Result, error) {
	step := c.take(true, query, args)
	if step.err != nil {
		return nil, step.err
	}
	if step.result != nil {
		return step.result, nil
	}
	return handlerSQLResult{}, nil
}

type handlerSQLRows struct {
	rows [][]driver.Value
	idx  int
}

func (r *handlerSQLRows) Columns() []string {
	width := 0
	if len(r.rows) > 0 {
		width = len(r.rows[0])
	}
	cols := make([]string, width)
	for i := range cols {
		cols[i] = fmt.Sprintf("column_%d", i)
	}
	return cols
}
func (r *handlerSQLRows) Close() error { return nil }
func (r *handlerSQLRows) Next(dest []driver.Value) error {
	if r.idx >= len(r.rows) {
		return io.EOF
	}
	copy(dest, r.rows[r.idx])
	r.idx++
	return nil
}

type handlerSQLResult struct{ idErr error }

func (r handlerSQLResult) LastInsertId() (int64, error) { return 42, r.idErr }
func (r handlerSQLResult) RowsAffected() (int64, error) { return 1, nil }

var handlerTestTime = time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC)

func handlerUserRow(username string) []driver.Value {
	return []driver.Value{int64(42), username, "test-device", "test@example.invalid", handlerTestTime}
}

func handlerJSONRequest(method, path, body string) *http.Request {
	r := httptest.NewRequest(method, path, strings.NewReader(body))
	r.Header.Set("Content-Type", "application/json")
	return r.WithContext(withUserID(r, 42))
}

func runHandler(t *testing.T, handler http.HandlerFunc, r *http.Request) (w *httptest.ResponseRecorder) {
	t.Helper()
	w = httptest.NewRecorder()
	defer func() {
		if p := recover(); p != nil {
			t.Errorf("handler panicked: %v", p)
		}
	}()
	handler(w, r)
	return w
}

func checkHandlerStatus(t *testing.T, w *httptest.ResponseRecorder, status int) {
	t.Helper()
	if w.Code != status {
		t.Errorf("HTTP status = %d, want %d; body: %s", w.Code, status, w.Body.String())
	}
	var body struct {
		OK  bool   `json:"ok"`
		Msg string `json:"msg"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON response: %v", err)
	}
	if body.OK != (status == http.StatusOK) {
		t.Errorf("response ok = %v for expected status %d", body.OK, status)
	}
	if status != http.StatusOK && body.Msg == "" {
		t.Error("error response missing compatible msg field")
	}
}
