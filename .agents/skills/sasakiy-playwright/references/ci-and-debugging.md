# CI と障害調査

retry、parallel、shard、reporter、CI、TypeScript、失敗調査を扱うときに読む。

## Retry、parallel、shard を品質指標と分ける

- retry は環境変動の診断情報を得るために使い、flaky test の修正を延期する仕組みにしない。
- retry 後に通ったテストを `flaky` として収集し、件数と対象を追う。
- test が失敗すると worker process と browser は破棄される。retry や後続テストでは `beforeAll` と worker fixture が再実行されるため、setup と teardown を冪等にする。
- CI の worker 数は `1` を初期値にする。増やす場合は CPU、memory、DB connection、server capacity を測る。
- parallel 実行で落ちるテストは、状態衝突と分離不足を直す。
- suite が許容時間を超える場合は shard を使う。`fullyParallel: true` では test 単位、無効では file 単位で分配される。
- shard では CI reporter を `blob` に切り替え、同じ Playwright version で HTML report へ統合する。
- GitHub Actions の shard matrix では `fail-fast: false` を使う。blob artifact と merge job は `!cancelled()` 相当の条件で実行する。
- browser × device × shard の job 数を計算し、coverage と CI 時間を比較する。

## CI を再現可能にする

- lockfile に従って依存関係を install し、必要な browser だけ `playwright install --with-deps` で導入する。
- 各 commit と pull request で実行する。smoke suite と full-browser suite を分ける場合は、各 gate の対象を明示する。
- Linux を基本候補にするが、OS 固有の仕様は対象 OS でも実行する。
- test source を信頼できない環境や外部サイトの巡回では、Docker の user、Chromium sandbox、権限、network access を設計する。
- HTML report、trace、screenshot、video、console log を失敗時にも取得する。secret を artifact に含めない。
- browser binary は原則として cache しない。採用する場合は Playwright version の hash を cache key に含める。
- shard ごとの blob report を一意な名前で upload し、全 shard 完了後に merge する。
- `github` reporter は matrix で同じ annotation を増やすことがある。matrix では blob と統合 HTML report を優先する。
- 日本語の visual test では、本番と同じ CJK font と emoji font を使う。

## TypeScript と lint で欠落を検出する

- Playwright は test 実行時に TypeScript の型検査を行わないため、CI で `tsc --noEmit` を別に実行する。
- `@typescript-eslint/no-floating-promises` で async Playwright API の `await` 漏れを検出する。
- fixture option、Page Object の引数、test data factory の返り値へ型を付ける。
- response body は外部入力として扱い、重要な事後条件ではスキーマまたは明示的な field assertion で確認する。
- `any`、無根拠な型 assertion、non-null assertion で失敗条件を隠さない。

## 失敗を調査する

1. 対象 test を file、line、project で絞り、同じ条件で再現する。
2. assertion の received value と call log を読む。
3. `--debug` または UI Mode で Locator、actionability、画面状態を確認する。
4. trace で DOM snapshot、network、console、source、attachment を確認する。
5. 並列時だけ失敗する場合は `--workers=1` と通常 worker 数を比較する。
6. flaky の疑いがある場合は `--repeat-each` で再現率を測る。固定 wait や retry 追加で隠さない。
7. browser 固有の場合は project を切り替え、skip の前に互換性要件と実装差を確認する。

調査後は `page.pause()`、過剰な log、不要な headed 設定を残さない。
