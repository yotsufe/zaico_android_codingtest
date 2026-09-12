# 株式会社ZAICO Android エンジニアコーディングテスト

## 概要

本プロジェクトは株式会社ZAICO（以下弊社）が、弊社に Android エンジニアを希望する方に出す課題のベースプロジェクトです。
下記の概要を詳しく読んだ上で課題を取り組んでください。

## アプリ仕様

本アプリは zaico API を利用して、在庫情報を取得・表示と作成をするアプリです。

<img src="sample/app_sample.gif" width="320">

### zaico APIの仕様と使い方

API 仕様は以下の通りです。

[zaico API Document](https://zaicodev.github.io/zaico_api_doc/)

zaicoのAPIを利用するためzaicoのアカウントを登録し、APIトークンを取得して使用してください。

[API利用に関するドキュメント](https://support.zaico.co.jp/hc/ja/articles/4406632009625-zaico-API%E3%82%92%E4%BD%BF%E3%81%A3%E3%81%A6%E5%9C%A8%E5%BA%AB%E3%83%87%E3%83%BC%E3%82%BF%E3%82%92%E6%93%8D%E4%BD%9C%E3%81%99%E3%82%8B)

ビルドしたアプリで動作確認をスムーズに行えるように、まずはzaicoから在庫登録してデータを作成してください。

[在庫登録のドキュメント](https://support.zaico.co.jp/hc/ja/articles/9425011130265--WEB-%E5%9C%A8%E5%BA%AB%E3%83%87%E3%83%BC%E3%82%BF%E3%82%92%E7%99%BB%E9%8C%B2%E3%81%99%E3%82%8B)

### セットアップ

API トークンはリポジトリに含めないため、`local.properties` で管理しています。
クローン後、`local.properties` に以下を追記してください（このファイルは `.gitignore` 対象です）。

```properties
zaico.apiToken=＜あなたの zaico API トークン＞
```

トークンは Web 版 zaico の ［ユーザー名］＞［ユーザー情報］ から取得できます。
事前に ［設定］＞［利用機能設定］ で「API機能」を有効にしておいてください。

未設定のままビルドすると、実行時に「API トークンが設定されていません」と表示されます。

### 使用している API のバージョンについて

本アプリは公開 API v2 を使用しています。

在庫データ作成は [公開 API v2 ドキュメント](https://public-docs.zaico.co.jp/public-api-v2-doc/openapi.html)
の `Inventories_create` に従い、以下を使用しています。

```
POST /api/v2/orgs/companies/{company_id}/inventories.json
Content-Type: application/json

{"title": "..."}
```

`title` のみが必須です（`quantity` / `place` / `unit` などは任意）。
`company_id`（拠点 ID）はパスに必要なため、`/api/v2/orgs/companies.json` から取得しています。

> ドキュメント上の成功ステータスは `201` ですが、実機では `200` が返りました。
> 本実装は 2xx を成功として扱い、レスポンス本文の形式に依存しないため、どちらでも動作します。

### テストの実行

```bash
./gradlew :app:testDebugUnitTest
```

JVM 上で完結します（実機・API トークン不要）。通信は Ktor の `MockEngine` で差し替えています。

設計方針は [docs/design.md](docs/design.md) を参照してください。

### 動作確認済の開発環境

- IDE：Android Studio Ladybug Feature Drop | 2024.2.2 Patch 1
- Kotlin：1.9.24
- Java：17
- Gradle：8.8.1
- minSdk：28
- targetSdk：35

※ ライブラリの利用はオープンソースのものに限ります。
※ 環境は適宜更新してください。

### アプリ動作

1. 登録されている在庫一覧の表示
2. 在庫詳細データの表示
3. 在庫データの追加・検索

## 課題取り組み方法

本プロジェクトを [**Duplicate** してください](https://help.github.com/en/github/creating-cloning-and-archiving-repositories/duplicating-a-repository)（Fork しないようにしてください。必要ならプライベートリポジトリにしても大丈夫です）。今後のコミットは全てご自身のリポジトリで行ってください。

次の選考を開始する前までに[課題](https://github.com/zaicodev/zaico_android_codingtest/issues/9)を確認・対応し、出来た所までで問題ありませんので、リポジトリのアドレスをご連絡ください。
