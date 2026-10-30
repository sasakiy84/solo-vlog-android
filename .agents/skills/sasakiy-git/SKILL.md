---
name: sasakiy-git
description: Git workflow rules for Sasakiy-style repositories, including commit message style, issue-linked commits, hook/test requirements, branch-agnostic safety checks, clean staging, and safe git operations. Use before Codex creates branches, stages files, commits, moves issue files, merges, rebases, checks status, writes commit messages, or otherwise operates git.
---

# Sasakiy Git

git 操作を行うときに使う。まずリポジトリの `AGENTS.md` を読む。リポジトリ固有の git・コミット規約がある場合はそれを優先する。issue 起点の作業では、リポジトリ内の issue 運用ルールも確認する。

## 基本方針

- 勝手にコミットしない。ユーザーからコミットを求められた場合、または issue 運用上コミットが明示されている場合だけコミットする。
- 全ての必要なテストが通らない限りコミットしない。
- 破壊的な git 操作をしない。`reset --hard`、作業ツリーを捨てる checkout/restore、強制 push などは、ユーザーが明示した場合だけ行う。
- 作業前後に `git status --short` で差分を確認する。
- 作業前から存在する未コミット変更は、明示的に依頼されない限り触らない・戻さない・コミットに混ぜない。
- ユーザーや他プロセスの差分を巻き戻さない。

## コミットメッセージ

- 日本語で書く。
- 命令形で書く。
- 「〜する」という形で書く。
- 原則として1行目だけでなく、複数行書く
- issue に対応するコミットでは、件名に issue ID とタイトルまたは要約を含める。
- 1 issue 完了ごとに 1 コミットを原則にする。

コミット件名は次のような形にする。

```text
i00001 mux_mp4_file.rs の data_size の u32 トランケーションを修正する
i00003 Encoder と Decoder の close と reset の binding で GIL 保持デッドロックが発生する issue を追加する
i00049 closed prop_h264_sps の PBT を構造化 Strategy に置き換えてクラッシュフリー専用テストを fuzz/ に移管する
i00039 解決方法セクションに判定結果と後続 issue 方針を追記する
```

本文が必要な場合は、件名の後に空行を置き、箇条書きで変更内容、理由、テスト、判断を具体的に書く。件名だけで説明しきれない変更では本文を書く。
本文はほとんどの場合で必要。変更の要約、背景、意図、具体的な計測結果なども書いておく。

```text
i00001 mux_mp4_file.rs の data_size の u32 トランケーションを修正する

- src/mux_mp4_file.rs の暗黙キャストを try_from() に置き換える
- 境界値成功、境界値超過、エラー後リトライのテストを追加する
- CHANGES.md に FIX エントリを追加する
```

## コミット前チェック

コミット前に以下を確認する。

- `git status --short` で意図したファイルだけが含まれている。
- issue 対応なら issue 本文の完了条件を満たしている。
- `CHANGES.md` 更新が必要な変更では、リポジトリ固有の形式と直近の記載に従っている。
- リポジトリで指定された format / lint / test が通っている。
- フックをスキップしていない。

テストコマンドはリポジトリ固有の `AGENTS.md`、`README.md`、`Makefile`、`package.json`、`pyproject.toml`、`Cargo.toml` などから判断する。確認できない場合は、実行できなかった理由をユーザーに報告する。

## ブランチと同期

- ブランチ戦略は前提としない。ブランチを作成・切り替え・同期する必要がある場合は、ユーザー指示またはリポジトリ固有の規約に従う。
- 作業ブランチを最新化する必要がある場合は、まず `git status --short` を確認する。
- merge や rebase は、リポジトリ固有の規約とユーザー指示に合わせる。
- ローカルで不要に履歴を書き換えない。
- conflict が出た場合は、関係ファイルを読んで解決し、解決内容をコミット本文か報告に残す。

## 禁止事項

- ユーザーが明示していないコミット。
- テスト未実行または失敗を隠したコミット。
- `--no-verify` によるフックスキップ。
- 関係ない差分の巻き戻し。
- 根拠のないコミットメッセージや、変更内容と一致しないメッセージ。
