---
name: sasakiy-typescript
description: Sasakiy-style TypeScript and JavaScript implementation rules for API boundaries, request/response tests, payload construction, and TypeScript-specific safety. Use when Codex edits TypeScript or JavaScript code.
---

# Sasakiy TypeScript

TypeScript / JavaScript のコードを実装・修正するときに使う。まずリポジトリの `AGENTS.md`、`package.json`、tsconfig、既存コード、関連テストを確認する。

## TypeScript 固有の型安全性

- 関数の引数と返り値には型を明示する。
- `any`、広すぎる型、安易な型アサーションで型エラーを回避しない。
- 更新可能なフィールドは型で表現する。たとえば `Pick` を使って更新可能なフィールドだけを型にする。
- 外部入力、DB レコード、API response は境界で検証または型変換し、内部型と混ぜない。

## HTTP API とエラー

- エラーレスポンスを設計するときは RFC 7807 / 9457 を参照する。
- 内部状態を推測させる必要のないエラーは、外部レスポンス上ではまとめる。
- request / response の互換性、エラーケース、空入力、境界値を確認する。
- payload 構築と保存処理は分ける。

## テスト

- HTTP API は、可能ならサーバーやブラウザを起動せず、`Request` から `Response` を検証する。
- テスト helper は、テストで確認したいフィールドだけを明示的に指定できる形にする。
- 呼び出し元で直接書ける処理を無駄に wrapper 化しない。たとえば `response.json()` を呼ぶだけの wrapper は避ける。
- 型エラーや lint エラーを `ignore`、`any`、型アサーションで安易に回避しない。
- Playwright では web-first assertion を使う。
- Testing Library を有効活用する。
- MSW で外部 API をモックする。
