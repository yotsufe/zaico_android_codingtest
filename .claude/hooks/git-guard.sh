#!/bin/bash
# PreToolUse(Bash) フック。git push / gh pr create のときだけ内容を検査する。
#
#   1. force push を拒否する
#   2. まだ push していないコミットが触ったファイルに秘密情報が無いか調べる
#   3. gh pr create / gh pr edit に渡す本文に秘密情報が無いか調べる
#
# 標準入力に {"tool_input":{"command":"..."}} が渡る。
# 拒否するときだけ permissionDecision: deny を出力し、それ以外は何も出力しない。
#
# ■ 限界（これは最後の砦であって、完全な防壁ではない）
#   シェルコマンドを正規表現で読むだけなので、変数展開（g=push; git $g）や
#   多段のインタプリタ経由は原理的に検知できない。
#   手順書（.claude/skills/pr-create/SKILL.md）側の検証を省略しないこと。

set -uo pipefail

CMD=$(jq -r '.tool_input.command // ""' 2>/dev/null) || exit 0
[ -z "$CMD" ] && exit 0

# git も gh も含まないコマンドは即座に対象外（大半はここで抜ける）
case "$CMD" in *git*|*gh*) ;; *) exit 0 ;; esac

ROOT="${CLAUDE_PROJECT_DIR:-.}"
cd "$ROOT" 2>/dev/null || exit 0

deny() {
  jq -cn --arg r "$1" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: $r
    }
  }'
  exit 0
}

# 断片の先頭から、実行の前置きになる語を剥がして本体のコマンド行を返す。
strip_prefix() {
  printf '%s' "$1" | sed -E '
    s/^[[:space:]]+//
    s/^([A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*[[:space:]]+)+//
    s/^(sudo|env|time|nohup|xargs|eval|command)[[:space:]]+//
    s/^(bash|sh|zsh)[[:space:]]+-[a-z]*c[[:space:]]+//
    s/^[[:space:]]+//
  '
}

# 断片が git <サブコマンド> か判定する。/usr/bin/git や \git も拾う。
GITOPT='([[:space:]]+(-C[[:space:]]+[^[:space:]]+|--git-dir([=[:space:]])[^[:space:]]+|--work-tree([=[:space:]])[^[:space:]]+|-c[[:space:]]+[^[:space:]]+|-[^[:space:]]+))*'
is_git_sub() {
  printf '%s' "$1" | /usr/bin/grep -qE "^\\\\?([^[:space:]]*/)?git$GITOPT[[:space:]]+$2([[:space:]]|\$)"
}

PUSHING=0
GH_PR=0
BODY_FILE=""

# コマンドを ; && || | 改行 で区切り、断片ごとに先頭の語を見る。
# 先頭語を見るので echo "git push --force" のような文字列としての言及や、
# rm -f x && git push の -f を force と誤認しない。
while IFS= read -r seg || [ -n "$seg" ]; do
  # 判定のためだけに引用符を外す。git push '--force' のような書き方を拾うため。
  bare=$(strip_prefix "$seg" | tr -d "\"'")
  [ -z "$bare" ] && continue

  if is_git_sub "$bare" push; then
    PUSHING=1
    # --- 1. force push の禁止 ---
    # 判定は push の断片の中だけで行う。-fu のような結合形も拾う。
    if printf '%s' "$bare" | /usr/bin/grep -qE '(^|[[:space:]])(-[A-Za-z]*f[A-Za-z]*|--force([-=][A-Za-z-]*)?)([=[:space:]]|$)'; then
      deny "force push は禁止されています。人間がレビューしている最中に履歴が変わると、前回からの差分が追えなくなり、レビューコメントの紐付きも切れます。push 後の修正は追加コミットで行い、履歴の見た目はマージ時の squash で担保してください。どうしても必要なら、この判断をユーザーに確認してください。"
    fi
    # refspec の先頭の + も強制更新になる
    if printf '%s' "$bare" | /usr/bin/grep -qE '(^|[[:space:]])\+[A-Za-z0-9_./~^-]+(:[A-Za-z0-9_./~^-]+)?([[:space:]]|$)'; then
      deny "refspec の先頭に + を付けた push は強制更新になります。force push は禁止されています。通常の refspec を使ってください。"
    fi
  fi

  if printf '%s' "$bare" | /usr/bin/grep -qE '^([^[:space:]]*/)?gh[[:space:]]+pr[[:space:]]+(create|edit)([[:space:]]|$)'; then
    GH_PR=1
    BODY_FILE=$(printf '%s' "$bare" | sed -nE 's/.*--body-file[=[:space:]]+([^[:space:]]+).*/\1/p')
  fi
done <<EOF
$(printf '%s' "$CMD" | tr '\n;|&' '\n\n\n\n')
EOF

[ "$PUSHING" = "1" ] || [ "$GH_PR" = "1" ] || exit 0

# --- 2. 秘密情報のパターン ---------------------------------------------
# 値は「数字を 1 つ以上含む 12 文字以上」に限定する。数字を要求しないと
# passwordInputField / YOUR_API_KEY_HERE のような識別子やプレースホルダに
# 誤検知する（実測で確認済み）。
V='[A-Za-z0-9_/+=.-]'
VAL="($V{5,}[0-9]$V{5,}|[0-9]$V{11,}|$V{11,}[0-9])"
# キー名は接尾辞と閉じ引用符を許す（api_key_prod= や "api_key": のため）
K='(api[_-]?key|apikey|secret|password|passwd|token)'
KEY="$K"'[A-Za-z0-9_.-]*"?'
XML='name="[^"]*'"$K"'[^"]*"[^>]*>'"$VAL"'<'
# 値そのものが秘密と分かる形式（キー名に依存しない）
WELLKNOWN='(gh[pousr]_[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{12,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30,}|eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,})'
PATTERNS="(Bearer[[:space:]]+$VAL|$KEY[[:space:]]*[:=][[:space:]]*\"?'?$VAL|$XML|$WELLKNOWN|-----BEGIN[[:space:]]+[A-Z ]*PRIVATE KEY-----)"

# プロジェクト固有の実値は .claude/hooks/secret-patterns.txt に 1 行 1 正規表現で書く。
# 不正な正規表現を黙って連結すると git grep ごと失敗し、組み込みパターンを含む
# 検査全体が無言で無効になるため、1 行ずつ検証してから採用する。
EXTRA="$ROOT/.claude/hooks/secret-patterns.txt"
if [ -f "$EXTRA" ]; then
  # 最終行に改行が無くても読み落とさないよう || [ -n "$line" ] を付ける
  while IFS= read -r line || [ -n "$line" ]; do
    [ -z "$line" ] && continue
    case "$line" in \#*) continue ;; esac
    # grep は「一致なし」で 1、「正規表現が不正」で 2 以上を返す。
    # if 文で囲むと $? が if の結果になってしまうので、直後に受ける。
    printf 'x' | /usr/bin/grep -E "$line" >/dev/null 2>&1
    rc=$?
    if [ "$rc" -gt 1 ]; then
      deny "secret-patterns.txt の次の行が正規表現として不正です:
  $line
このまま進めると秘密情報スキャン全体が無言で無効になります。行を修正してください。"
    fi
    PATTERNS="$PATTERNS|($line)"
  done < "$EXTRA"
fi

report() {
  deny "秘密情報らしき文字列が含まれています。push と PR 作成は外部公開なので取り消せません。該当箇所を確認し、除去方法をユーザーに相談してください（独断で履歴を書き換えないこと）。検出箇所:
$1"
}

# --- 3. PR 本文の検査 ---------------------------------------------------
if [ "$GH_PR" = "1" ]; then
  if printf '%s' "$CMD" | /usr/bin/grep -qiE "$PATTERNS"; then
    report "gh コマンドに渡された PR 本文"
  fi
  if [ -n "$BODY_FILE" ] && [ -f "$BODY_FILE" ]; then
    /usr/bin/grep -qiE "$PATTERNS" "$BODY_FILE" && report "$BODY_FILE"
  fi
fi

[ "$PUSHING" = "1" ] || exit 0

# --- 4. push されるコミットの検査 ---------------------------------------
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

# まだどのリモートにも無いコミット＝これから push される分。
# main..HEAD だと main 上での push を検査できず、push 対象 ref ともずれる。
REVS=$(git rev-list HEAD --not --remotes 2>/dev/null)
[ -z "$REVS" ] && exit 0

# それらのコミットが触ったファイルだけを見る。ツリー全体を見ると、
# main に既存の 1 行が原因で以後すべての push が恒久的に拒否される。
FILES=$(git show --pretty=format: --name-only $REVS 2>/dev/null | sort -u | /usr/bin/grep -v '^$')
[ -z "$FILES" ] && exit 0

HITS=$(git grep -l -i -E "$PATTERNS" HEAD -- $FILES 2>/dev/null | head -20)
[ -n "$HITS" ] && report "$HITS"

exit 0
