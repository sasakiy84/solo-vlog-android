---
name: sasakiy-testing
description: Sasakiy-style testing and verification rules for implementation work, including test design as specification, boundary and error cases, side-effect isolation, repository test command discovery, and reporting unverified risks. Use when Codex adds, edits, reviews, or decides whether to run tests.
---

# Sasakiy Testing

テストを追加・修正・レビューするとき、または変更後の検証方針を決めるときに使う。まずリポジトリの `AGENTS.md`、既存テスト、CI 設定、ビルド設定を読む。

## 基本方針

- テストは仕様書として読めるように書く。
- テスト名、入力、期待出力を明確にする。
- 1 ケースでは 1 つの振る舞いだけを検証する。
- 正常系だけでなく、境界値、空入力、異常系、既存バグの再発防止を必要に応じて追加する。
- テストのためだけに本番コードを不自然に歪めない。
- テストを通すために仕様を変更しない。
- バグをドキュメント化できることはテストの意義の 1 つである。
- 標準 API や DB 機能そのものではなく、アプリケーション仕様をテストする。
- 変更内容に応じて、最小限かつ十分な確認を行う。

## テスト設計

- バグ修正では、可能なら先に失敗するテストまたは回帰を検知するテストを追加する。
- 複雑な条件は、条件の組み合わせと期待結果が読み取れる形にする。
- DB 不要のロジックは軽い単体テストに切り出す。
- 副作用のある処理は、できるだけ影響の少ない環境で実際の振る舞いに近い形で検証する。
- DB 結合テストは、temporary file 相当の機能などを使い、ホスト環境に影響を与えない。
- HTTP API は、可能ならサーバーやブラウザを起動せず、request から response を検証する。

## 実行コマンドの決定

テストコマンドは、リポジトリ内の各種設定ファイルから判断する。
コミット前は `$sasakiy-git` に従い、必要な format / lint / test が通っていることを確認する。

## 検証報告

- 実行したコマンドと結果を報告する。
- 実行できなかった検証がある場合は、理由と残るリスクを書く。
- flaky な可能性がある失敗は、根拠なく無視しない。再実行、ログ確認、テスト対象の切り分けを行う。
- ドキュメント変更のみでも、リンク、コマンド、コード例が現状と矛盾しないか確認する。
- API 変更では request / response の例、互換性、エラーケースを確認する。
