---
name: pr-create
description: 実装が終わった変更を、レビューしやすい単位にコミット分割してブランチに載せ、PR を作成する。ユーザーが「PR にして」「ブランチを切ってPRまで」と言ったときに使う。push と PR 作成の前には必ずユーザーの確認を取る。
---

# コミット分割して PR を作成する

## 全体の流れ

```
実装
 → peer-review（別エージェントによるレビュー）
 → 指摘の反映
 → ブランチを切ってコミット分割        ← ここまでは確認なしで進めてよい
 → ユーザーがざっくり確認              ← 必ず止まる
 → push → PR 作成
```

コミットはこの Skill が呼ばれたときに限って行う。**`push` と PR 作成はさらに別**で、ユーザーの「良さそう」を待ってから実行する。

## 1. 事前チェック

```bash
git branch --show-current
git status --short
git remote -v
gh auth status
```

作業ツリーに変更がなければ何もすることがない。その旨を伝えて終了する。

## 2. peer-review を先に通す

まだレビューしていなければ `peer-review` Skill を実行する。指摘が出たらユーザーに判断を仰ぎ、反映してからコミットに進む。

## 3. ブランチを切る

`main` から切る。名前は変更内容に合わせる（`fix/` `feat/` `chore/` `refactor/`）。

```bash
git checkout main -q && git checkout -b <branch> -q
```

**主題の異なる変更を同じブランチに混ぜない。** 別の話題が混ざっていることに気づいたら、その時点でユーザーに分けるかどうかを確認する。

## 4. コミットを分割する

### 方針

**性質ごとに分ける。1 コミットが 1 つのことだけをする。**

- `chore:` 設定ファイル、`.gitignore` などのノイズ。**先に片付ける**
- `fix:` / `feat:` 本題
- 独立して読めるもの（例: 秘密情報の管理方法の変更）は別コミットにする

**各コミットは単体でビルドが通ること。** これを守るため、途中の状態を作り直す必要がある場合がある。

### 中間状態の作り方

最終状態をスクラッチパッドに退避し、中間状態を書いてコミットし、最後に復元する。

```bash
S=<scratchpad>/final && mkdir -p $S
cp <最終状態のファイル群> $S/

# 中間状態を書く → ビルド確認 → コミット
./gradlew :app:assembleDebug
git add -A <paths> && git commit -F - <<'EOF'
...
EOF

# 最終状態に戻す → ビルド確認 → コミット
cp $S/* <元のパス>
```

**ビルド確認の落とし穴:**

- `./gradlew ... -q 2>&1 | tail` は `tail` の終了コードを拾うため、**失敗しても成功に見える**。`BUILD SUCCESSFUL` の文字列で判定すること
- 生成コード（`BuildConfig` など）の有無が変わる中間状態では、インクリメンタルビルドの残骸で 1 回目が失敗することがある。その場合は再実行して確認する

### コミットメッセージ

日本語。1 行目は `<type>: <何をしたか>`。本文は以下の構成にする。

```
## 問題      何が起きていたか（ログ・エラーメッセージを引用する）
## 原因      なぜそうなったか（実際に叩いた API のレスポンスなど、根拠を示す）
## 対応      何をしたか。判断を伴うものは理由も書く
## 確認      どう検証したか
```

小さな変更（`.gitignore` など）は `## 問題` などを省いて数行でよい。

末尾に必ず付ける:

```
Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
Claude-Session: <セッション URL>
```

## 5. push 前の検証

**全部通ってから**ユーザーに確認を依頼する。

```bash
# 1. 秘密情報がブランチの全コミットに含まれないか
#    ※ `| head` は終了コードを潰すので、出力の中身で判定すること
OUT=$(git grep -l "<secret>" $(git rev-list main..HEAD) -- 2>/dev/null)
[ -n "$OUT" ] && echo "検出" || echo "なし"

# 2. 最終状態でビルドが通るか
./gradlew :app:assembleDebug

# 3. テストが通るか（テストがある場合）
./gradlew :app:testDebugUnitTest

# 4. 作業ツリーがクリーンか
git status --short

# 5. コミット構成の確認
git log --reverse --format="%n● %h %s" --name-status main..HEAD
git diff --stat main..HEAD
```

`grep -r` は**この環境では `.gitignore` を尊重する**ため、無視ファイル（`local.properties`、`build/` など）を走査しない。秘密情報の確認は必ず git ベースで行う。

## 6. ユーザーに確認を依頼する

**ここで止まる。** 以下を提示する。

- コミット構成（ハッシュ・件名・変更ファイル数）と、**そう分けた理由**
- 差分統計
- 検証結果
- PR のベースブランチ（通常 `main`）と Draft かどうかの確認

push 前なので `--amend` や `rebase` で自由に直せることを添える。

## 7. push と PR 作成

```bash
git push -u origin <branch>
gh pr create --base main --head <branch> --title "<件名>" --body-file <scratchpad>/pr_body.md
gh pr view <N> --json number,title,state,baseRefName,headRefName,isDraft,additions,deletions,changedFiles,commits
```

### PR 本文の構成

**人間がレビューしやすいこと**を最優先にする。

```markdown
## 概要
何を解決したか。1〜2 文で言い切る。
コミットの一覧を表にして、どれが本題かを示す。

## 何が起きていたか
エラーログをそのまま引用する。

## 原因
根拠を示す。API が絡むなら実際のリクエストとレスポンスを載せる。

## 対応
変更前後を表で比較する。判断を伴うものは理由を書く。
> 引用ブロックで補足を入れると読みやすい。

## 動作確認
セットアップ手順と、チェックボックスで確認項目。

## レビュー観点
**自分が判断で決めた箇所を明示する。** 他の選択肢があったものは特に。
レビュアーが異論を出しやすくなる。

## スコープ外
やっていないことと、その理由。次にやることへの言及。
```

末尾に付ける:

```
🤖 Generated with [Claude Code](https://claude.com/claude-code)

<セッション URL>
```

## 注意

- **自分の変更と無関係なファイルを巻き込まない。** 差分がノイズで埋まるとレビューが機能しなくなる
- 実機での確認が必要な変更は、`adb` で E2E を通してから PR にする

  ```bash
  adb devices                   # 先に接続確認。切断されているとコマンドが長時間ハングする
  adb logcat -c -b crash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb shell am start -n <package>/.MainActivity
  adb logcat -d -b crash        # 空であること
  adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png <scratchpad>/
  ```

  `sleep` はこの環境で使えない。待機が必要なら別の手段を取る
- 画面が表示されたことの確認は、プロセスの生存だけでなく実際のスクリーンショットで行う
