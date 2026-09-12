# 設計メモ

zaico Android コーディングテストの設計方針を記録する。
アーキテクチャ・テスト戦略・API の扱いなど、**変更頻度の低い判断**を対象とする。

> **実装状況・既知の制約・残タスクはこの文書に書かない。** それらは PR の本文で管理する。
> 二重に持つと片方の更新が漏れ、「対応済みなのに制約として残っている」といった
> 事実と逆の記述が生まれるため。

## 構成

```
AddActivity ──→ AddViewModel ──→ InventoryRepository (interface)
                                          │
                             ZaicoInventoryRepository (実装)
                                          │
                                      ZaicoApi
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
| `ApiTokenMissingException` を投げる | `Context` を持たないデータ層では文字列リソースを引けないため、事実だけを例外で伝える |
| `Context.messageOf(Throwable)` で文言に変換する | 例外から表示文言への変換を UI 側の 1 箇所に集約する |
| `companyId` とは別に `fetchCompanyId` を持つ | `companyId` のキャッシュはプロセス全体で共有されテスト間で漏れるため、キャッシュを通さない経路をテスト対象にする |

### 各層の責務

**UI 層（Activity / Fragment）にビジネスロジックを置かない。**
バリデーション、通信、状態遷移、エラーの分類は、すべて ViewModel 以下が持つ。
UI がするのは、状態を受け取って描画することと、入力をそのまま ViewModel に渡すことだけ。

| 層 | 持つもの | 持たないもの |
|---|---|---|
| Activity / Fragment | 状態の購読と描画、入力の受け渡し | 判断、通信、状態 |
| ViewModel | 画面の状態、バリデーション、Repository の呼び出し | Android のリソース、View への参照 |
| Repository | API の呼び出し、レスポンスからモデルへの変換 | 画面の都合 |
| `ZaicoApi` | HTTP の送受信、ステータス判定 | `Context`、表示文言 |

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
| `ZaicoApi` | MockEngine | URL 組み立て、認証ヘッダ、ステータス判定、エラーメッセージ抽出、JSON 解析 |
| `ZaicoInventoryRepository` | MockEngine | 作成リクエストの形（メソッド・パス・ボディ）、異常系の例外 |
| `AddViewModel` | 手書き Fake | バリデーション、状態遷移 |

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
  → UI が messageOf で表示文言にして出す
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

- ドキュメントの成功ステータスは `201` だが、実機では `200` が返る
- レスポンスは `{"data": {...}}` 形式だが、作成直後は `quantity` が `null` になる

エラーは RFC 7807 Problem Details 形式（`title` / `status` / `detail`）で返るため、
`detail` を優先し、無ければ `title` を表示する。

リクエストの形（メソッド・パス・ボディ）はテストで文字列として固定してある。
