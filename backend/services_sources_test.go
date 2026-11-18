package main

import "testing"

// 样本取自 500 彩票网真实返回（2026-11-18 抓取），含 HTML 注释、表头行等干扰项
const sample500SSQHTML = `<table>
<tr class="t_tr1"><!--<td>2</td>--><td>26132</td><td class="t_cfont2">05</td><td class="t_cfont2">09</td><td class="t_cfont2">13</td><td class="t_cfont2">17</td><td class="t_cfont2">18</td><td class="t_cfont2">21</td><td class="t_cfont4">11</td><td class="t_cfont4">&nbsp;</td><td>1,215,899,380</td><td>11</td><td>6,848,394</td><td>192</td><td>132,372</td><td>402,998,740</td><td>2026-11-17</td></tr>
<tr class="t_tr1"><!--<td>2</td>--><td>26131</td><td class="t_cfont2">01</td><td class="t_cfont2">10</td><td class="t_cfont2">19</td><td class="t_cfont2">26</td><td class="t_cfont2">31</td><td class="t_cfont2">33</td><td class="t_cfont4">15</td><td class="t_cfont4">&nbsp;</td><td>1,214,985,444</td><td>4</td><td>10,000,000</td><td>97</td><td>335,881</td><td>438,881,432</td><td>2026-11-15</td></tr>
</table>`

const sample500DLTHTML = `<table>
<tr class="t_tr1"><td>1</td><td>2</td><td>3</td><td>4</td><td>5</td><td>注数</td><td>奖金(元)</td><td>注数</td><td>奖金(元)</td></tr>
<tr class="t_tr1"><td>26131</td><td>02</td><td>11</td><td>18</td><td>19</td><td>29</td><td>04</td><td>07</td><td>731,584,779</td><td>8</td><td>6,352,665</td><td>139</td><td>100,754</td><td>323,417,845</td><td>2026-11-16</td></tr>
</table>`

func TestParse500SSQ(t *testing.T) {
	rows := parse500Rows(TypeSSQ, sample500SSQHTML)
	if len(rows) != 2 {
		t.Fatalf("应解析出 2 行，实际 %d", len(rows))
	}
	r := rows[0]
	if r.Issue != "2026132" {
		t.Errorf("期号应为 2026132（自动补20前缀），实际 %s", r.Issue)
	}
	if r.Red != "05,09,13,17,18,21" || r.Blue != "11" {
		t.Errorf("球号解析错误: red=%s blue=%s", r.Red, r.Blue)
	}
	if r.DrawDate != "2026-11-17" {
		t.Errorf("日期解析错误: %s", r.DrawDate)
	}
	if r.Pool != 1215899380 {
		t.Errorf("奖池解析错误: %d", r.Pool)
	}
	if rows[1].Issue != "2026131" {
		t.Errorf("第二行期号错误: %s", rows[1].Issue)
	}
}

func TestParse500DLT(t *testing.T) {
	rows := parse500Rows(TypeDLT, sample500DLTHTML)
	if len(rows) != 1 {
		t.Fatalf("表头行应被过滤，只解析出 1 行，实际 %d", len(rows))
	}
	r := rows[0]
	if r.Issue != "26131" {
		t.Errorf("大乐透期号应保持 5 位（26131），实际 %s", r.Issue)
	}
	if r.Red != "02,11,18,19,29" || r.Blue != "04,07" {
		t.Errorf("球号解析错误: red=%s blue=%s", r.Red, r.Blue)
	}
	if r.DrawDate != "2026-11-16" {
		t.Errorf("日期解析错误: %s", r.DrawDate)
	}
	if r.Pool != 731584779 {
		t.Errorf("奖池解析错误: %d", r.Pool)
	}
}

func TestNormalizeIssue(t *testing.T) {
	cases := []struct{ t, in, want string }{
		{TypeSSQ, "26132", "2026132"},
		{TypeSSQ, "2026132", "2026132"}, // 已是 8 位不动
		{TypeDLT, "26131", "26131"},     // 大乐透保持 5 位
	}
	for _, c := range cases {
		if got := normalizeIssue(c.t, c.in); got != c.want {
			t.Errorf("normalizeIssue(%s,%s)=%s, want %s", c.t, c.in, got, c.want)
		}
	}
}
