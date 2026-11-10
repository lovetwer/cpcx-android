package main

import (
	"database/sql"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/go-sql-driver/mysql"
)

type registerReq struct {
	Username string `json:"username"`
	Password string `json:"password"`
	Email    string `json:"email"`
	DeviceID string `json:"device_id"`
}

type loginReq struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type deviceLoginReq struct {
	DeviceID    string `json:"device_id"`
	Email       string `json:"email"`
	DeviceModel string `json:"device_model"`
}

type authResp struct {
	OK    bool   `json:"ok"`
	Token string `json:"token"`
	User  *User  `json:"user"`
}

// handleRegister 注册
func handleRegister(w http.ResponseWriter, r *http.Request) {
	var req registerReq
	if err := readJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, "请求格式错误")
		return
	}
	if req.Username == "" || req.Password == "" {
		writeError(w, http.StatusBadRequest, "用户名和密码不能为空")
		return
	}
	var cnt int
	if err := DB.QueryRow("SELECT COUNT(*) FROM users WHERE username=?", req.Username).Scan(&cnt); err != nil {
		writeError(w, http.StatusInternalServerError, "数据库错误")
		return
	}
	if cnt > 0 {
		writeError(w, http.StatusConflict, "用户名已存在")
		return
	}
	pw, err := hashPassword(req.Password)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "密码处理失败")
		return
	}
	res, err := DB.Exec("INSERT INTO users(username,password,device_id,email) VALUES(?,?,?,?)",
		req.Username, pw, req.DeviceID, req.Email)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "注册失败")
		return
	}
	id, _ := res.LastInsertId()
	u, _ := getUserByID(id)
	writeJSON(w, http.StatusOK, authResp{OK: true, Token: generateToken(id), User: u})
}

// handleLogin 账号密码登录
func handleLogin(w http.ResponseWriter, r *http.Request) {
	var req loginReq
	if err := readJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, "请求格式错误")
		return
	}
	u, err := getUserByUsername(req.Username)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "用户名或密码错误")
		return
	}
	if !checkPassword(req.Password, u.Password) {
		writeError(w, http.StatusUnauthorized, "用户名或密码错误")
		return
	}
	writeJSON(w, http.StatusOK, authResp{OK: true, Token: generateToken(u.ID), User: u})
}

// handleDeviceLogin 按设备号一键登录（无则自动建号）
// 用户名格式：机型_时间戳（如 "XYD-W10_1739485210"），确保全局唯一不重复
func handleDeviceLogin(w http.ResponseWriter, r *http.Request) {
	var req deviceLoginReq
	if err := readJSON(r, &req); err != nil || req.DeviceID == "" {
		writeError(w, http.StatusBadRequest, "设备号不能为空")
		return
	}
	u, err := getUserByDevice(req.DeviceID)
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		writeError(w, http.StatusInternalServerError, "数据库错误")
		return
	}
	if errors.Is(err, sql.ErrNoRows) {
		// 只有设备尚无用户时才建号，数据库故障不能当作用户不存在。
		username := fmt.Sprintf("%s_%d", req.DeviceModel, time.Now().Unix())
		pw, err := hashPassword(randHex(12))
		if err != nil {
			writeError(w, http.StatusInternalServerError, "密码处理失败")
			return
		}
		var res sql.Result
		const maxUsernameRetries = 3
		for attempt := 0; attempt <= maxUsernameRetries; attempt++ {
			if attempt > 0 {
				// 序号保证即使时钟精度不足，每次重试也使用不同的用户名。
				username = fmt.Sprintf("%s_%d_%d", req.DeviceModel, time.Now().UnixNano(), attempt)
			}
			res, err = DB.Exec("INSERT INTO users(username,password,device_id,email) VALUES(?,?,?,?)",
				username, pw, req.DeviceID, req.Email)
			if err == nil {
				break
			}
			if !isUsernameCollision(err) || attempt == maxUsernameRetries {
				writeError(w, http.StatusInternalServerError, "注册失败")
				return
			}
		}
		id, err := res.LastInsertId()
		if err != nil {
			writeError(w, http.StatusInternalServerError, "注册失败")
			return
		}
		var ok bool
		u, ok = getUserByID(id)
		if !ok || u == nil {
			writeError(w, http.StatusInternalServerError, "用户信息查询失败")
			return
		}
	}
	writeJSON(w, http.StatusOK, authResp{OK: true, Token: generateToken(u.ID), User: u})
}

// isUsernameCollision 只重试 users 的用户名唯一索引，不掩盖其他数据库错误。
func isUsernameCollision(err error) bool {
	var mysqlErr *mysql.MySQLError
	if !errors.As(err, &mysqlErr) || mysqlErr.Number != 1062 {
		return false
	}
	const marker = " for key "
	pos := strings.LastIndex(mysqlErr.Message, marker)
	if pos < 0 {
		return false
	}
	key := strings.Trim(mysqlErr.Message[pos+len(marker):], "'`")
	// MySQL 8 的索引名可带表名前缀，如 users.uniq_username。
	if pos := strings.LastIndex(key, "."); pos >= 0 {
		key = key[pos+1:]
	}
	return key == "uniq_username"
}

// handleMe 返回当前用户
func handleMe(w http.ResponseWriter, r *http.Request) {
	u, ok := getUserByID(currentUserID(r))
	if !ok {
		writeError(w, http.StatusUnauthorized, "用户不存在")
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{"ok": true, "user": u})
}

// ---------- 查询辅助 ----------

// handleUpdateMe 更新当前用户资料（邮箱 / 昵称 / 密码）
func handleUpdateMe(w http.ResponseWriter, r *http.Request) {
	uid := currentUserID(r)
	curUser, ok := getUserByID(uid)
	if !ok || curUser == nil {
		writeError(w, http.StatusUnauthorized, "用户不存在")
		return
	}
	var body struct {
		Email       string `json:"email"`
		Username    string `json:"username"`
		Password    string `json:"password"`
		OldPassword string `json:"old_password"`
	}
	if err := readJSON(r, &body); err != nil {
		writeError(w, http.StatusBadRequest, "请求格式错误")
		return
	}
	var sets []string
	var args []interface{}
	if body.Email != "" {
		sets = append(sets, "email=?")
		args = append(args, body.Email)
	}
	if body.Username != "" && body.Username != curUser.Username {
		var cnt int
		if err := DB.QueryRow("SELECT COUNT(*) FROM users WHERE username=? AND id<>?", body.Username, uid).Scan(&cnt); err == nil && cnt > 0 {
			writeError(w, http.StatusConflict, "用户名已存在")
			return
		}
		sets = append(sets, "username=?")
		args = append(args, body.Username)
	}
	if body.Password != "" {
		if len(body.Password) < 6 {
			writeError(w, http.StatusBadRequest, "密码至少6位")
			return
		}
		// 复用当前用户的昵称，查询包含密码哈希的用户信息
		fullUser, err := getUserByUsername(curUser.Username)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "用户信息查询失败")
			return
		}
		// 设备号一键登录的账户（password 为空）可跳过原密码校验；
		// 普通账户修改密码时必须校验原密码
		if fullUser.Password != "" {
			if body.OldPassword == "" {
				writeError(w, http.StatusBadRequest, "请输入原密码")
				return
			}
			if !checkPassword(body.OldPassword, fullUser.Password) {
				writeError(w, http.StatusBadRequest, "原密码错误")
				return
			}
		}
		pw, err := hashPassword(body.Password)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "密码处理失败")
			return
		}
		sets = append(sets, "password=?")
		args = append(args, pw)
	}
	if len(sets) == 0 {
		writeError(w, http.StatusBadRequest, "没有可更新的字段")
		return
	}
	args = append(args, uid)
	if _, err := DB.Exec("UPDATE users SET "+strings.Join(sets, ",")+" WHERE id=?", args...); err != nil {
		writeError(w, http.StatusInternalServerError, "更新失败")
		return
	}
	u, _ := getUserByID(uid)
	writeJSON(w, http.StatusOK, map[string]interface{}{"ok": true, "user": u})
}

// handleDeleteMe 注销账号：删除用户及其所有关联数据（彩票、分享）
func handleDeleteMe(w http.ResponseWriter, r *http.Request) {
	uid := currentUserID(r)
	// 删除用户的彩票记录
	if _, err := DB.Exec("DELETE FROM lotteries WHERE user_id=?", uid); err != nil {
		writeError(w, http.StatusInternalServerError, "注销失败：无法删除彩票数据")
		return
	}
	// 删除用户的分享记录
	if _, err := DB.Exec("DELETE FROM shares WHERE user_id=?", uid); err != nil {
		writeError(w, http.StatusInternalServerError, "注销失败：无法删除分享数据")
		return
	}
	// 删除用户本体
	if _, err := DB.Exec("DELETE FROM users WHERE id=?", uid); err != nil {
		writeError(w, http.StatusInternalServerError, "注销失败：无法删除用户")
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{"ok": true, "msg": "账号已注销"})
}

// authUser 取当前登录用户（用于对比昵称）
func authUser(r *http.Request) *User {
	u, _ := getUserByID(currentUserID(r))
	return u
}

func getUserByID(id int64) (*User, bool) {
	u := &User{}
	err := DB.QueryRow("SELECT id,username,device_id,email,created_at FROM users WHERE id=?",
		id).Scan(&u.ID, &u.Username, &u.DeviceID, &u.Email, &u.CreatedAt)
	if err != nil {
		return nil, false
	}
	return u, true
}

func getUserByUsername(name string) (*User, error) {
	u := &User{}
	err := DB.QueryRow("SELECT id,username,password,device_id,email,created_at FROM users WHERE username=?",
		name).Scan(&u.ID, &u.Username, &u.Password, &u.DeviceID, &u.Email, &u.CreatedAt)
	return u, err
}

func getUserByDevice(device string) (*User, error) {
	u := &User{}
	err := DB.QueryRow("SELECT id,username,device_id,email,created_at FROM users WHERE device_id=?",
		device).Scan(&u.ID, &u.Username, &u.DeviceID, &u.Email, &u.CreatedAt)
	return u, err
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}
