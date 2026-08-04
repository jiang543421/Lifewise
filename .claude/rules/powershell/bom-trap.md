# PowerShell 5.1 Encoding Trap (BOM / CRLF / GetBytes)

> Project-level rule for Windows dev environment (auto-loaded for this repo).
> 全局方法论沉淀：`~/.claude/memory/feedback-powershell-encoding-trap.md`。
> 触发来源：plan-03 review notes 抢救 (2026-08-04) 同一会话内 4 次复发。

## 为什么需要

PowerShell 5.1（Windows PowerShell；PSCore 7+ 不受影响）在「string/file → 字节流」路径上默认加 UTF-8 BOM 或 UTF-16 LE，会污染：

- git commit message subject（首字符变 U+FEFF）
- `git show | Out-File` 抢救的二进制内容
- 喂给 native exe（git / mvn / docker）的 stdin
- `Get-Content` 读取的 binary / unknown-encoding 文件

## 4 个 BOM 污染 API

| # | API | 行为 | 触发场景 |
|---|---|---|---|
| 1 | `Out-File -Encoding utf8` | 写 UTF-8 **with BOM** | `git show \| Out-File` 抢救文件 → 首 3 字节被污染 |
| 2 | `cmd /c "> file"` redirect | 走 OEM/ANSI codepage | PS 5.1 调用 cmd redirect，二进制被转码 |
| 3 | `$msg \| native-exe`（pipe to stdin） | 字符串 → UTF-8 **with BOM** | `$msg \| git commit -F -` → subject 首位 U+FEFF |
| 4 | `Get-Content` 默认编码 | 系统默认（Windows PowerShell cmdlet = UTF-16 LE；file = Windows-1252） | 读 ASCII / UTF-8 文件静默 mojibake |

## 正确做法（byte-level API 绕过 PS 编码层）

### 写入（避免 BOM 污染）

| 场景 | 错的写法 | 对的写法 |
|---|---|---|
| 文件落盘无 BOM | `Out-File -Encoding utf8` | `[System.IO.File]::WriteAllBytes($path, $bytes)` |
| 字符串 → 文件（UTF-8 无 BOM） | `Out-File -Encoding utf8NoBOM`（记不住存在） | `[System.IO.File]::WriteAllBytes($path, [System.Text.Encoding]::UTF8.GetBytes($str))` |
| 喂 message 给 git | `$msg \| git commit -F -` | 写 temp 文件 + `git commit -F $tempfile` |

### 读取（避免 mojibake）

| 场景 | 错的写法 | 对的写法 |
|---|---|---|
| 读 binary / raw bytes | `Get-Content $path` | `[System.IO.File]::ReadAllBytes($path)` |
| 读 git blob 字节级 | `git show <ref>:<path> \| Out-File` | `.NET Process` + `BaseStream.CopyTo(MemoryStream)` |

### 行数（PS 5.1 `Measure-Object -Line` 不可靠）

- CRLF 文件上 `Measure-Object -Line` 少算 ~30%
- 用 byte-level LF 计数：`($bytes \| Where-Object { $_ -eq 0x0A }).Count`
- 或 `cmd /c "wc -l <path>"`

## Git commit message 写入（标准模板）

```powershell
$msg = @'
docs(scope): subject line

body line 1
body line 2
'@

# 错：$msg | git commit -F -   → BOM 污染 subject
# 对：
$temp = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllBytes($temp, [System.Text.Encoding]::UTF8.GetBytes($msg))
git commit -F $temp
Remove-Item $temp
```

或更简洁（直接给 git 文件路径）：

```powershell
[System.IO.File]::WriteAllBytes('msg.txt', [System.Text.Encoding]::UTF8.GetBytes($msg))
git commit -F 'msg.txt'
```

**关键**：`[System.Text.Encoding]::UTF8.GetBytes()` 默认**不**加 BOM。  
如需 BOM 显式：`$bytes = [System.Text.Encoding]::UTF8.GetPreamble() + [System.Text.Encoding]::UTF8.GetBytes($str)`。

## Git commit message 验证（落地后必跑）

```powershell
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "git"; $psi.Arguments = "log -1 --format=`"%s`""
$psi.RedirectStandardOutput = $true; $psi.UseShellExecute = $false
$p = [System.Diagnostics.Process]::Start($psi)
$ms = New-Object System.IO.MemoryStream
$p.StandardOutput.BaseStream.CopyTo($ms); $p.WaitForExit()
[BitConverter]::ToString($ms.ToArray()[0..4])
# 期望首字节 = subject 首字符 ASCII，非 EF BB BF
```

## Git 特殊符号的 PS 5.1 解析坑

| 输入 | 坑 | 解决 |
|---|---|---|
| `HEAD^{tree}` | PS 触发 `-EncodedCommand`（base64 错误编码路径） | 用 `git log -1 --format=%T HEAD` 替代 |
| `--format=%H %s` | `%s` 被 PS 解析异常 | 包 backtick：`"log -1 --format=`"%s`"` |
| 文件路径含空格 | PS 自动加引号但重新解析 | `& "C:\Program Files\..."` 或单引号包裹 |

## 触发条件总结（何时回头看本规则）

- 涉及 `Out-File` / `Set-Content` / `>` redirect 写文件
- 涉及 `Get-Content` / `gc` 读 binary / unknown-encoding 文件
- 涉及把字符串通过 pipe 喂给 native exe（git / mvn / docker 等）
- 涉及 `Measure-Object -Line` 数行
- `git log` / `git rev-parse` 后 subject 含不可见字符（U+FEFF）
- `git diff` 后文件首 3 字节是 `EF BB BF`

## 相关

- 全局 memory：`feedback-powershell-encoding-trap.md`（跨项目通用方法论）
- 全局 memory：`feedback-commit-message-verify.md`（commit message 落地前验证具体断言）
- 本次触发 commit：`996b13e docs(review): rescue plan-03-expense review notes from backup ref`（首字节 `EF BB BF` → `64-6F-63-73`）
- Lifewise CLAUDE.md §8.1：项目级记忆规则（context 加载顺序）