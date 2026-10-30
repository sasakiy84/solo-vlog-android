# レビューと検証

Playwright の変更をレビューするとき、または変更を完了扱いにする前に読む。

## レビュー観点

- テスト名から利用者向け仕様が分かり、実装詳細を契約にしていないか。
- Locator は role、label、text、test ID の順で検討され、strict か。
- `waitForTimeout()`、`networkidle`、`ElementHandle`、racy な `waitForNavigation()` が残っていないか。
- event の待機 Promise を、event を起こす操作より前に作っているか。
- assertion に `await` があり、one-shot read で非同期 UI を確認していないか。
- test data、認証状態、DB、出力ファイルが parallel と retry の両方で分離されているか。
- mock がテスト対象そのものを置き換えていないか。API 契約と一致しているか。
- secret を source、storage state、trace、HAR、screenshot、report に含めていないか。
- visual baseline の環境と更新理由を再現できるか。
- browser、device、role、shard の matrix が必要な coverage に対応しているか。
- retry、skip、soft assertion、差分閾値、timeout の追加に具体的な理由があるか。
- CI で typecheck、lint、対象 test、artifact 保存が実行されるか。

## 検証

- 変更した test を最小単位で実行し、その後に関連 file または project を実行する。
- config、fixture、認証 setup、共通 Page Object を変更した場合は、それを使う全 project への影響を確認する。
- Locator または UI 操作を変更した場合は、少なくとも対象 browser で実画面を確認する。
- parallel、retry、shard、CI を変更した場合は、可能な範囲で実際の並列実行または CI と同じ command を実行する。
- snapshot を更新した場合は、生成物の差分を目視し、意図した箇所以外が変わっていないことを確認する。
- 実行した command、成功・失敗、未実行の project、残る環境差を報告する。
