---
name: sasakiy-playwright
description: Sasakiy-style rules for writing, reviewing, configuring, debugging, and operating Playwright tests.
---

# Sasakiy Playwright

Playwright Test を使ってフロントエンドをテストするときに使う。テストの追加・修正・レビュー・デバッグ、`playwright.config.*`、fixture、認証、モック、スナップショット、CI が対象である。

TypeScript と `@playwright/test` を基本にする。リポジトリが JavaScript または別の言語を使っている場合は、既存の言語とテストランナーに合わせる。導入済みの Playwright のバージョンを確認し、依頼されていないアップグレードやテスト方式の移行は行わない。

## 最初に確認するもの

- リポジトリの `AGENTS.md` とテスト規約
- `package.json`、lockfile、パッケージマネージャー、Playwright のバージョン
- `playwright.config.*`、既存の fixture、認証 setup、Page Object、テストデータ生成処理
- アプリの起動方法、`baseURL`、環境変数、DB、外部 API
- CI 設定、対象ブラウザー、shard、worker、retry、reporter、artifact
- 変更対象画面の表示、アクセシブルネーム、URL、通信、loading・成功・失敗状態

コマンドはリポジトリのパッケージマネージャーと scripts を優先する。

## 参照先を選ぶ

作業に該当する参照ファイルを読む。すべてのファイルを毎回読む必要はない。複数の作業にまたがる場合は、該当するファイルを組み合わせる。

- Playwright の導入、テスト範囲、browser project、`playwright.config.*`、`webServer` を決めるときは、[導入と設定](references/setup-and-configuration.md)を読む。
- テスト、Locator、待機、assertion、fixture、Page Object を書く、直す、レビューするときは、[テストの作成](references/writing-tests.md)を読む。
- DB data、server-side state、test-scoped・worker-scoped fixture、retry、UI Mode を扱うときは、[副作用と分離](references/side-effects-and-isolation.md)を読む。
- 認証、テストデータ、network mock、API、時刻、browser API、Component Testing を扱うときは、[状態と外部境界](references/state-and-integrations.md)を読む。
- アクセシビリティ、ARIA snapshot、visual test、browser・device 差を検証するときは、[品質検査](references/quality-checks.md)を読む。
- retry、parallel、shard、reporter、CI、TypeScript、失敗調査を扱うときは、[CI と障害調査](references/ci-and-debugging.md)を読む。
- 変更を完了扱いにする前、または Playwright の変更をレビューするときは、[レビューと検証](references/review-and-verification.md)を読む。
