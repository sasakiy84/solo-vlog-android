# 導入と設定

Playwright の導入、テスト範囲、browser project、`playwright.config.*`、`webServer` を決めるときに読む。

## テスト範囲を決める

- 利用者がブラウザーで観測できる振る舞いと、画面をまたぐ主要な利用経路を検証する。
- 関数の内部構造、配列の形、CSS class、フレームワーク固有の state など、利用者が観測しない実装詳細を E2E テストの契約にしない。
- 純粋関数、複雑な分岐、境界値の組み合わせは単体テストへ寄せる。ブラウザーを起動しなくても同じ仕様を検証できる場合は、より小さいテストを選ぶ。
- request / response だけで確認できる仕様は API テストで検証する。UI テストでは、API と画面の接続や利用者に見える結果を検証する。
- 管理していない外部サイトや第三者 API の可用性をテストしない。境界で応答をモックし、自分たちの表示とエラー処理を検証する。
- Component Testing は experimental である。新規採用では安定性、対応フレームワーク、Vite 構成への影響を明示する。

## Browser と project を選ぶ

- Chromium、Firefox、WebKit を候補にし、利用者、対応ブラウザー、変更リスクに基づいて project を選ぶ。
- desktop、mobile、locale、認証 role、feature flag の直積を無条件に作らない。各 project が検出するリスクを説明できる組み合わせにする。
- ブラウザー共通の仕様差を `test.skip()` で隠さない。対応範囲外または browser 固有機能である根拠を skip 理由に書く。
- bundled Chromium と branded Chrome / Edge は同じものではない。enterprise policy、codec、branded browser 固有挙動が対象の場合だけ channel を追加する。
- Playwright と browser binary の version を lockfile と CI image で再現可能にする。Docker image を使う場合は、テスト側の version と image tag を合わせる。

## 設定を環境ごとに明示する

次は出発点であり、suite の規模、CI 資源、対象ブラウザーに合わせる。

```ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.GITHUB_ACTIONS ? [['html'], ['github']] : [['html']],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: 'npm run preview',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
  },
});
```

- `forbidOnly` を CI で有効にする。
- local と CI で retry を分ける。最終結果が成功でも、`flaky` に分類されたテストを無視しない。
- CI の worker 数は `1` を初期値にする。十分な資源がある self-hosted runner では測定後に増やす。
- trace は CI の全テストで常時記録しない。retry がある場合は `on-first-retry`、retry がない場合は `retain-on-failure` を検討する。
- screenshot と video は調査価値と artifact サイズを基準に選ぶ。CI 失敗では trace を優先する。
- `webServer.url` で readiness を確認する。local では既存 server を再利用できるが、CI ではテストが起動した server を使う。
- test、expect、action、navigation、fixture、global の timeout を区別し、最も狭い責務に設定する。

## DB を準備する

- suite 共通の DB 初期化や migration は setup project に置き、対象 project から `dependencies` で依存させることを基本にする。通常の `globalSetup` は project dependency で扱えない事情がある場合だけ選ぶ。
- `webServer.command` は server の起動に集中させる。DB 準備との連結は、準備済みの DB がなければ server を起動できない場合に限る。
- worker ごとに独立した DB が必要な場合は、worker-scoped fixture で作成、割り当て、破棄を管理する。
- DB を削除または再作成する前に、対象パスがテスト用の許可範囲内にあることを検証する。処理は途中失敗後や再実行でも同じ結果になるようにする。
- UI Mode では setup project が既定で実行されない。依存するテストの前に setup project を明示的に実行する。

設定例の `npm run preview` は例示である。production build と development server のどちらを検証するか確認して command を決める。
