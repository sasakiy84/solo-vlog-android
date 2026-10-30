# 状態と外部境界

認証、テストデータ、network mock、API、時刻、browser API、Component Testing を扱うときに読む。

## 認証情報を安全に再利用する

- 認証 setup は project dependency を基本にする。setup 自体を report、trace、fixture、retry の対象にする。
- `storageState` は専用ディレクトリへ置き、Git から除外する。cookie や header はなりすまし可能な秘密情報として扱う。
- テストが共有アカウントの server-side state を変更しない場合は、共有アカウントを使える。
- state を変更するテストを並列実行する場合は、worker ごとに account と `storageState` を用意する。
- worker ごとの識別には `test.info().parallelIndex` を使う。`workerIndex` は retry に伴う worker 再作成で変わる。
- role ごとに setup project と依存 project を分ける。
- 未認証テストでは空の `storageState` を明示する。
- IndexedDB に token を保存する場合は、対応 version を確認して `storageState({ indexedDB: true })` を検討する。
- `storageState` は session storage を保存しない。必要な場合は host を限定した `addInitScript()` で復元する。
- 認証 UI 自体のテストと、認証済み状態を前提にした機能テストを分ける。

## Network と API を目的に応じて扱う

- routing は `page.goto()` や対象操作より前に登録する。popup を含む context 全体には `context.route()` を使う。
- 第三者 API、異常系、境界値、再現しにくい応答は mock する。
- 自分たちの backend との結合を確認する E2E テストでは、テスト対象をすべて mock しない。
- mock response には status、content type、body を明示し、本番 API の契約と揃える。
- `route.fetch()` で実際のレスポンスを変更する場合は、変更するフィールドと目的を明示する。
- HAR では秘密情報、期限切れデータ、巨大化、API 仕様の陳腐化を確認する。更新と replay を同じ CI 実行で混ぜない。
- Service Worker により network event や route が見えない場合がある。Service Worker が対象でなければ `serviceWorkers: 'block'` を検討する。
- `APIRequestContext` は前提データ作成、後片付け、UI 操作後の server-side state 確認に使う。
- request / response の検証では、method、URL、status に加えて、主題に関係する payload と body を確認する。

## 時刻と Browser API を制御する

- 時刻依存のテストでは実時間を待たず、Clock API で時刻と timer を進める。
- fake clock は、対象が `Date`、timer、`requestAnimationFrame` を使う前に導入する。
- geolocation、permission、locale、timezone、color scheme、offline は context または project option で指定する。
- browser API の mock は `addInitScript()` を navigation より前に登録する。
- Node.js と browser の実行環境を混同せず、必要な値を引数として明示的に渡す。
- touch、mobile viewport、device scale factor は `devices` を起点にし、spread 後の上書き順を確認する。

## Component Testing の境界を扱う

- `mount()` は各テスト内で assertion の近くに置く。
- Component Testing は `context` と `page` を再利用する場合がある。local storage、cookie、router、singleton などの global state は test setup または `beforeMount` で戻す。
- test は Node.js、component は browser で動く。複雑な object や同期 callback を渡せない場合は、primitive な値へ変換する test story または wrapper component を作る。
- `vi.mock()` や `jest.mock()` は browser bundle に届かない。`hooksConfig` と `playwright/index.*` の `beforeMount` を使う。
- network mock では experimental な `router` fixture と MSW handler を利用できる。対応 version を確認する。
- 既存の Vite config はそのまま再利用されない。必要な alias や plugin だけを `ctViteConfig` へ移す。
