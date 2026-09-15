# 設計メモ

zaico Android コーディングテストの設計方針を記録する。
アーキテクチャ・テスト戦略・API の扱いなど、**変更頻度の低い判断**を対象とする。

> **この文書が答えるのは「どう作るか」だけ。**
> 振る舞いは [spec.md](spec.md)、変更の経緯は [specs/](specs/) が持つ。
> 役割分担は [CLAUDE.md](../CLAUDE.md#ドキュメントの役割分担) にまとめてある。
>
> **実装状況・既知の制約・残タスクはこの文書に書かない。**
> 二重に持つと片方の更新が漏れ、「対応済みなのに制約として残っている」といった
> 事実と逆の記述が生まれるため。

## 構成

```
InventoriesFragment ──────┐
InventoryDetailFragment ──┼─→ 各 ViewModel ──→ InventoryRepository (interface)
CreateInventoryActivity ──┘                              │
                                            ZaicoInventoryRepository (実装)
                                                         │
                                         ┌───────────────┴───────────────┐
                                   CompanyIdProvider                      │
                                         └───────────────┬───────────────┘
                                                     ZaicoApi
                                                         │
                                                  ZaicoApiEndpoint
                                            （接続先・認証ヘッダ・BuildConfig）
```

### テストの継ぎ目を 2 段置く

差し替え可能な箇所を 2 段用意している。

- **ViewModel のテストは HTTP を知らない。** `InventoryRepository` に手書きの Fake を刺す
- **Repository のテストは ViewModel を知らない。** `HttpClient` に Ktor の `MockEngine` を刺す

どちらも実ソケットを開かないため、高速かつ決定的に動く。

### Android 依存の隔離

着手時点の `ZaicoApi` は `object` かつ `Context` と `BuildConfig` を直接参照しており、
**JVM 上のユニットテストから 1 行も呼べなかった**。

`ZaicoApiEndpoint` を設けて `Context` / `BuildConfig` 依存をそこへ閉じ込め、
データ層をテストから呼べるようにしている。これに伴う設計判断は次のとおり。

| 判断 | 理由 |
|---|---|
| `ZaicoApiEndpoint` が URL 組み立てと認証ヘッダを持つ | 接続先の知識を 1 箇所に集約する。認証方式を変える場合もここだけで済む |
| `ApiException` の各サブクラスを投げる | `Context` を持たないデータ層では文字列リソースを引けないため、**何が起きたかだけ**を型で伝える。表示文言は UI 層が決める。`when` が網羅的なので、例外を足して文言を忘れるとコンパイルが通らない |
| `Context.displayMessageOf(Throwable)` で文言に変換する | 例外から表示文言への変換を UI 側の 1 箇所に集約する |
| company_id の解決を `CompanyIdProvider` に分ける | `ZaicoApi` は `object` なので、保持した company_id がプロセス全体で共有されテストから初期化できない。`@Singleton` のクラスにして Hilt に寿命を任せると、テストは新しいインスタンスを作れる |

### 各層の責務

**UI 層（Activity / Fragment）にビジネスロジックを置かない。**
バリデーション、通信、状態遷移、エラーの分類は、すべて ViewModel 以下が持つ。
UI がするのは、状態を受け取って描画することと、入力をそのまま ViewModel に渡すことだけ。

| 層 | 持つもの | 持たないもの |
|---|---|---|
| Activity / Fragment | 状態の購読と描画、入力の受け渡し | 判断、通信、状態 |
| ViewModel | 画面の状態、バリデーション、Repository の呼び出し | Android のリソース、View への参照 |
| Repository | API の呼び出し、取得結果の組み立て | 画面の都合、HTTP とJSON の詳細 |
| `ZaicoApi` | HTTP の送受信、ステータス判定、JSON の解析とモデルへの変換 | `Context`、表示文言 |

**UI 層が通信を持たない原則の唯一の例外が画像の読み込み（Glide）。**
画像は表示そのものと不可分で、ViewModel を経由させても中継するだけになるため。
「どの画像を出せるか」の判断は ViewModel が持ち、UI は取得と描画だけを担う。

### 依存の受け取り方

- **依存はコンストラクタで受け取る。** ViewModel は `InventoryRepository` を、
  `ZaicoInventoryRepository` は `HttpClient` と `ZaicoApiEndpoint` を引数で受け取る。
  テストはそこに Fake や `MockEngine` を差し込む
- **テストダブルは手書きの Fake を基本とする。** 差し替える対象がメソッド数個の interface である限り、
  呼び出し内容を素直に記録でき、読んで挙動が分かる

コンストラクタ注入は DI ライブラリの有無に関わらず成り立つ。
どう組み立てるか（手動の Factory か、DI コンテナか）は、この原則とは独立した選択になる。

## テスト戦略

| 対象 | 手段 | 検証内容 |
|---|---|---|
| `ZaicoApiEndpoint` | なし（純関数） | URL の連結、認証ヘッダの組み立て、トークンの有無 |
| `ZaicoApi` | MockEngine | URL 組み立て、認証ヘッダ、ステータス判定、エラーメッセージ抽出、JSON 解析 |
| `CompanyIdProvider` | MockEngine | company_id の解決、保持して 2 回目以降は叩かないこと、異常系の例外 |
| `ZaicoInventoryRepository` | MockEngine | 一覧・詳細・作成のリクエストの形（メソッド・パス・ボディ）、読み飛ばし、異常系の例外 |
| ViewModel 各種 | 手書き Fake | バリデーション、状態遷移、通知の消費 |

**Activity / Fragment はテストしない。** 上記の責務分離により、UI 層にはテスト対象となる
ロジックが存在しないため。

残るのは「どの状態をどのビューに反映するか」という描画の対応付けだけで、
それを検証するために Robolectric や Espresso を導入するのは、
この規模では得るものに対して依存が大きすぎる。

テストに使う依存は次の 2 つ。いずれも `testImplementation` なので APK には入らない。

- `io.ktor:ktor-client-mock`（Apache 2.0）— Ktor の `MockEngine`
- `org.jetbrains.kotlinx:kotlinx-coroutines-test`（Apache 2.0）— `runTest`、`Dispatchers.setMain`

### エラー処理の流れ

```
ZaicoApi が例外を投げる
  → Repository はそのまま伝播（変換しない）
  → ViewModel が状態（Failed など）に変換する
  → UI が displayMessageOf で表示文言にして出す
```

ViewModel は `runCatching` を使わない。`CancellationException` まで捕まえてしまい、
画面を閉じた際のキャンセルが「失敗」として表示されるため、明示的に再送出する。

入力不備は通信エラーと区別した状態（`TitleRequired`）で公開する。
UI が例外の型を判定しなくて済み、ユーザーが取るべき行動の違いをそのまま表現できる。

## API バージョン

一覧・詳細の既存実装と揃えて **公開 API v2** を使う。
v1 は組織ユーザートークンを受け付けず 403 を返す。

仕様は [公開 API v2 ドキュメント](https://public-docs.zaico.co.jp/public-api-v2-doc/openapi.html)
を参照する（OpenAPI 定義は同ディレクトリの `openapi.yaml` で取得できる）。
README がリンクしている v1 のドキュメントは内容が異なるため参照しない。

在庫作成は `Inventories_create` に準拠する。

```
POST /api/v2/orgs/companies/{company_id}/inventories.json
{"title": "..."}
```

必須パラメータは `title` のみ。`company_id`（拠点 ID）はパスに必要なため、
`/api/v2/orgs/companies.json` から取得する。

`createInventory` の戻り値は `Unit` とし、**成功判定を 2xx だけで行ってレスポンス本文の形に依存させていない**。
作成した ID を使う要件がないためで、これにより次の差異を吸収できている。

- ドキュメントの成功ステータスは `201` だが、実機では `200` が返った（2026-09 時点）
- レスポンスは `{"data": {...}}` 形式だが、作成直後は `quantity` が `null` になる

エラーは RFC 7807 Problem Details 形式（`title` / `status` / `detail`）で返るため、
`detail` を優先し、無ければ `title` を表示する。

リクエストの形（メソッド・パス・ボディ）はテストで文字列として固定してある。

## CI を置かない

検証はローカルで完結する（[CLAUDE.md の検証](../CLAUDE.md#検証)）。
コミットするのが 1 人で、同じコマンドが手元で全部走る状況では、
CI は**同じ検査をもう一度、遅れて走らせる**だけになる。

複数人が並行して触るようになったら入れる。それまでは置かない。

## コードスタイル

**書式は ktlint で機械的に揃える。** 静的解析としては detekt もあるが、
detekt が扱う複雑度や潜在バグはしきい値の調整が前提になる。この規模では調整の手間のほうが大きい。
バグ検出は Android Lint が見る範囲に留め、**書式だけを ktlint で強制する**。

**コードスタイルは `intellij_idea` を選ぶ。** ktlint の既定は `ktlint_official` だが、
公式ドキュメント自身が「IntelliJ IDEA と Android Studio の既定フォーマッタが受け付けない整形をする
場合がある」「このコードスタイルを使うならエディタの整形機能は無効にするのが最善」と述べている。
エディタの整形を封じる運用は取らない。

`android_studio` ではなく `intellij_idea` にしたのは次の理由による。

- `intellij_idea` は Kotlin Coding conventions に基づく。`gradle.properties` で宣言している
  `kotlin.code.style=official` と対応する
- `android_studio` は既存コードの末尾カンマをすべて削除する。本プロジェクトは一貫して
  末尾カンマを使っており、削除するとパラメータを 1 つ足すたびに差分が 2 行になる
- 本プロジェクトの import 順序が `intellij_idea` の既定レイアウトと一致している

**`backing-property-naming` だけ無効化する。** Fragment の ViewBinding は `_binding` と
private な getter の組で扱うが、このルールはバッキングプロパティに対応する公開プロパティを要求する。
Android 公式ドキュメントの作法を優先した。

**ktlint 本体のバージョンは明示的に固定する。** Gradle プラグインのパッチ更新で既定値が
変わりうるため、固定しないとルールが黙って変わる。
