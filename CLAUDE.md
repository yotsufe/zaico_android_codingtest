# CLAUDE.md

zaico Android コーディングテスト。**SDD（仕様駆動）+ TDD で開発する。**

## ドキュメントの役割分担

**この表がドキュメント運用の正本。** 他の文書はここへリンクするだけで、表を複製しない。

| 文書 | 答えるもの | 更新 |
|---|---|---|
| [docs/spec.md](docs/spec.md) | **今どう動くか**（利用者から観測できること） | 振る舞いを変えたら同じ PR で必ず |
| [docs/design.md](docs/design.md) | **どう作るか**（構成・テスト戦略・API の扱い・スタイル） | 判断が変わったときだけ |
| [docs/specs/NNN-*.md](docs/specs/) | **そのときどう決めたか** | マージ時に凍結。以後は書き換えない |
| [README.md](README.md) | セットアップと動かし方 | 手順が変わったとき |

**同じことを 2 か所に書かない。** 迷ったら「この情報が変わったとき、何か所直すか」で判断する。
2 か所以上になるなら、片方をリンクにする。

- API のエンドポイントや JSON の形は `design.md` が持つ。`spec.md` はその**観測できる帰結**だけを書く
- 仕様が後から変わったら `specs/` の古いファイルは触らず、**新しい変更 spec を起こす**

## 進め方

```
0. main から作業ブランチを切る        ← 実装より前。手順は [pr-create](.claude/skills/pr-create/SKILL.md)
1. docs/specs/NNN-*.md を書く         ← 仕様を先に確定。ユーザーに見てもらう
2. 受け入れ基準をテストにする          ← TDD の red。この時点では落ちる
3. 実装してテストを通す                ← green
4. docs/spec.md を更新                 ← 現在の仕様に反映
5. 変更 spec の「実機で守る」を消化する ← UI に触る変更のみ。結果を spec に記録する
6. peer-review を通し、結果を添えてユーザーに報告する ← ここで待つ
7. 指示を受けたら pr-create Skill に従う  ← 検証 → ユーザーゲート → commit → PR
8. PR 作成後、同じブランチに 状態 と PR 番号を追記する ← マージされた時点で凍結
```

**ステップ 7 は自分から始めない。** `pr-create` は「実装が終わったから、という状態を理由に
自動で起動しない」と定めている。ステップ 6 で報告して、指示を待つ。

ステップ 8 は**凍結の直前に行う最後の書き込み**。PR 番号は `gh pr create` の直後に分かるので、
**マージ前に同じブランチへ追記する**。マージ後だと `main` への直接コミットになるか、
peer-review マーカーの無い新ブランチを作ることになり、どちらも詰む。

### 止まる場所

`pr-create` は「ユーザーがレビューする §5 が唯一のゲート」と書いているが、
**それは実装が終わってから先の話**。実際には次の 3 か所で止まりうる。

| いつ | 何を決めるか | 根拠 |
|---|---|---|
| ステップ 1 の後 | 何を作るか（仕様） | この文書 |
| ステップ 6 | 実装とレビュー結果を見てもらう。**ここで指示を待つ** | この文書 / `pr-create` §2 |
| `pr-create` §5 | 履歴に刻んでよいか | `pr-create` |

「§5 の手前で別のゲートを置かない」という `pr-create` の指示は、
**§2 が自ら認めている停止点と、実装前の仕様確認までは禁じていない。**

### コミットの分け方

`pr-create` の分割方針に従いつつ、SDD の 3 段が見えるように接頭辞を使う。

```
spec: 在庫詳細の画像表示の仕様を追加   ← docs/specs/NNN のみ
test: 画像 URL の読み取りテストを追加   ← 単体では落ちる
feat: 在庫詳細に画像を表示する         ← 実装 + docs/spec.md の更新
```

`spec.md` の更新は**実装コミットに含める**。振る舞いが変わるのと同時に正になるため、
分けると「実装済みだが spec.md が古い」コミットが履歴に残る。

### 受け入れ基準をどう守るか

**UI 層（Activity / Fragment）はテストしない方針**（[design.md](docs/design.md#テスト戦略)）。
そのため受け入れ基準は、書いた時点で 2 つに仕分ける。**この分類はここが正本。**

| 種類 | 守り方 |
|---|---|
| ViewModel 以下で判断できること（状態・バリデーション・通信・データの読み取り） | **自動テスト** |
| 描画・画面遷移・文言の表示（Fragment / Activity の責務） | **実機確認**（ステップ 5） |

**peer-review の指摘で UI を直したら、ステップ 5 をやり直す。** 実機確認の結果は
直す前のものなので無効になる。チェックを埋めた変更 spec は `feat:` コミットに含める
（`spec:` は仕様だけを載せる）。

**判断を Fragment に置かない**（[design.md](docs/design.md#各層の責務)）。
「出す / 出さない」「押せる / 押せない」の判断は ViewModel 以下に寄せれば自動テストで守れる。
実機に回してよいのは、状態が決まったあとの**描画だけ**。

### 自分で決めてよいこと・人に聞くこと

基準は「戻せるか」ではなく、**他の選択肢が消えるか**。

| 自分で決める | 人に聞く |
|---|---|
| 命名、コメント、テストの書き方、リファクタの手順、実装の詳細 | **依存を増やす**、**公開 API や画面の仕様を変える**、**やらないと決める**、**スコープを増減する** |

依存は後から抜きにくい。やらない判断は記録が残らないと蒸し返される。
どちらも一度決めると他の道が閉じるので、**判断材料を添えて聞く**。

## 守ること

- **明示的な指示なしに `git commit` / `git push` / `gh pr create` をしない。**
  「進めて」「対応して」は作業の指示であって、履歴に刻む承認ではない。
  リソースの提供（「実機につなぎました」等）も承認ではない
- **取り返しがつかない操作は人間が引く。** マージ、履歴の書き換え（`rebase` / `--amend` /
  `filter-branch`）、`main` への直接 push、ブランチの削除、リリース。
  必要だと判断したら、実行せず提案する
- **実装したら `peer-review` Skill を通してから、結果を添えてユーザーに依頼する**
- **`main` に直接コミットしない。** 実装前に `git branch --show-current` を確認する
- 新しいコードで `!!` を使わない
- zaico API の版と参照するドキュメントは [design.md](docs/design.md#api-バージョン) に従う
- 秘密情報はコミットしない。トークンは `local.properties` の `zaico.apiToken`

`git push` と `gh pr create` は `.claude/hooks/git-guard.sh` が機械的に検査する。
コミット・PR の手順そのものは [pr-create](.claude/skills/pr-create/SKILL.md) が持つ。

**ハーネス（`CLAUDE.md` / `.claude/` / `tools/`）の変更も、アプリと同じゲートを通す。**
自分の制約を自分で緩められるぶん、コードより危険。実際に `peer-review` Skill の
マーカー掃除コードには「一致しているのにファイルを削除する」バグが入ったことがある
（`pipefail` の下で `grep -q` が SIGPIPE を起こす）。
**制約を緩める変更は、なぜ緩めてよいのかを必ず書く。**

### 機械で守られるもの・そうでないもの

| 守られる（`git-guard.sh`） | 守られない（自分で守るしかない） |
|---|---|
| force push、peer-review マーカーの有無、detached HEAD、秘密情報（push されるコミットと PR 本文） | **`git commit` 全般**、マージ、`rebase` / `--amend`、`git branch -D`、**`git push --delete`**、リリース |

右の列は宣言だけで、**誰も検査していない**。hook が見るのは `git push` と `gh pr create` だけ。
`git-guard.sh` 自身も「マーカーを手で書けば通る。防げるのは黙って飛ばすことであって、
意図的に迂回することではない」と認めている。

## 検証

**このコマンド一覧が正本。** `pr-create` の検証もここを参照する。

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest   # ユニットテスト
./gradlew :app:lintDebug           # Android Lint
./gradlew ktlintCheck              # 書式
./tools/check_docs.py              # .md を変えたとき
```

**変更に該当するものをすべて通す。** `.md` を触っていなければ `check_docs.py` は不要、
Kotlin を触っていなければ Gradle 系は不要。該当するのに飛ばさない。

`./gradlew ktlintFormat` は**整形であって検証ではない**。ファイルを書き換えるので、
ユーザーに差分を見せるゲートの直前には走らせない。

- **`UP-TO-DATE` を「通った」と報告しない。** 確認したいときは `--rerun-tasks` を付ける
- 書式は ktlint が強制する（`intellij_idea`）。手で整えない
- **検査できないものを「ルール」として増やさない。** 守られたか分からないルールは、
  実質存在しないのと同じ。書くなら、**何が機械で守られ何がそうでないかを明示する**
  （下記「機械で守られるもの・そうでないもの」）

### 実機確認

UI に触る変更は、自動テストで守れない部分がある。**実機確認が唯一の検証手段になる。**

```bash
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
adb devices                   # 先に接続確認。切断されているとコマンドが長時間ハングする
adb logcat -c -b crash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n jp.co.zaico.codingtest/.MainActivity
adb logcat -d -b crash        # 空であること
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png <scratchpad>/
```

- ホストの `sleep` は使えない。`adb shell sleep N` を使う
- 状態の確認は**スクリーンショットを読む**。`uiautomator dump` は画面に見えているノードしか
  出力しないので、不在の証明には使えない
- `input text` の前に IME を無効化する。**終わったら必ず戻す**

## このコードベース

Hilt（kapt。Data Binding が KSP 非対応のため）、Ktor、Navigation + Safe Args。
**バージョンはすべて `gradle/libs.versions.toml` が正本。** ここには書かない。

層の構成は [design.md](docs/design.md#構成) を見る。
`app/src/main/java/jp/co/zaico/codingtest/` にフラット配置で、パッケージは切っていない。
実機は Pixel 9。
