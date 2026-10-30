# 副作用と分離

DB data や server-side state を分離する、並列 worker を使う、retry 時の状態を扱うときに読む。

## テスト間で状態を分離する

- 各テストをほかのテストから分離し、local storage、session storage、data、cookie を共有しない。
- DB を使う場合はテストデータを管理下に置き、内容が変わらない staging 環境でテストする。
- test-scoped fixture は各テストの終了後に teardown される。テストごとに必要な準備と後片付けに使う。
- `APIRequestContext` は、UI テスト前の server-side state の準備と、browser 操作後の server-side post-condition の確認に使える。
- 管理していない第三者の依存先をテストしない。必要な response は Network API で保証する。

## 並列 worker 間でデータを分離する

- worker-scoped fixture は worker process ごとに setup され、その worker が実行する複数の test file で再利用される。
- 並列 worker 間で DB の user data を分離する場合は、worker-scoped fixture と `workerIndex` を使える。同じ worker のテストは同じ user data を再利用する。
- worker を再作成すると `workerIndex` は変わり、`parallelIndex` は同じ値を維持する。

## Retry と UI Mode の実行条件を確認する

- test が失敗すると worker process と browser は破棄され、新しい worker で retry される。新しい worker では `beforeAll` が再実行される。
- `testInfo.retry` で retry を検出し、retry 前に server-side state を消去できる。
- UI Mode は setup project を自動実行しない。依存するテストより先に setup test を手動で実行する。
- `--repeat-each` で繰り返す場合は、`testInfo.repeatEachIndex` で各実行を識別できる。
