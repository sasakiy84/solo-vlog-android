# テストの作成

テスト、Locator、待機、assertion、fixture、Page Object を書く、直す、レビューするときに読む。

## 利用者の言葉で書く

- テスト名は、利用者が理解できる前提、操作、期待する結果で書く。library や framework の内部用語を避け、必要な場合も利用者向けの表現を主にする。
- 1 テストでは 1 つの利用者向け振る舞いを主題にする。主題に必要な複数の確認は同じテストに含めてよい。
- 1 つのテストに複数の準備、操作、確認がある場合は、意味のあるまとまりごとに目的を説明する短いコメントを置く。各行の処理を言い換えるコメントは書かない。目安として数行から十数行のブロックごとにコメントがつく
- 長い workflow では `test.step()` も検討する。
- 単純なテストでは、抽象化のためだけの helper を作らない。
- codegen の出力は開始点として使い、Locator、不要な操作、assertion をレビューしてから残す。

```ts
import { expect, test } from '@playwright/test';

test('商品をカートに追加すると件数と小計を更新する', async ({ page }) => {
  await page.goto('/products');

  const product = page
    .getByRole('listitem')
    .filter({ has: page.getByRole('heading', { name: 'Keyboard' }) });
  await product.getByRole('button', { name: 'カートに追加' }).click();

  await expect(page.getByRole('status')).toHaveText('カートに追加しました');
  await expect(page.getByTestId('cart-count')).toHaveText('1');
  await expect(page.getByTestId('cart-total')).toHaveText('¥12,000');
});
```

## 各テストを分離する

- E2E テストでは、各テストが独立した browser context、cookie、local storage、session storage を使う。
- 別のテストの実行順や成功結果を前提にしない。
- テストデータと server-side state をテスト間で分離する。詳細は[副作用と分離](side-effects-and-isolation.md)に従う。
- 作成したサーバー側データは fixture の teardown または API で片付ける。
- `beforeEach` は各テストを同じ既知状態にするために使う。
- `beforeAll` と worker-scoped fixture は、高コストで安全に共有できる準備に限る。変更可能な `page` や `context` を共有しない。
- serial mode は独立化できない場合だけ使う。通常の利用経路を分割する手段にしない。
- `fullyParallel` の前に、アカウント、DB、port、出力ファイルの衝突がないことを確認する。

## Locator を契約として扱う

次の順序を基本にする。

1. `getByRole()` と accessible name
2. `getByLabel()`、`getByPlaceholder()`、`getByText()`、`getByAltText()`、`getByTitle()`
3. 明示的に契約化した `getByTestId()`
4. CSS または XPath。利用者向け属性で一意にできない場合だけ使う

- role と accessible name が実際のアクセシビリティツリーにあることを確認する。
- list、table、dialog、navigation などの親 Locator から絞り込む。
- `filter({ hasText })` または `filter({ has })` を使い、DOM 階層や `nth-child` を固定しない。
- strictness violation を `.first()`、`.last()`、`.nth()` で黙らせない。順序自体が仕様の場合だけ使う。
- iframe では `frameLocator()` を使う。
- `ElementHandle`、`page.$()`、`page.$$()`、`waitForSelector()` は基本的に使わない。
- `page.evaluate()` は browser 内でしか取得できない状態または API に限る。
- Locator が作りにくい場合は、画面側の ARIA、label、見出し構造も確認する。

## Playwright の待機を使う

- Locator action の actionability check と web-first assertion の retry を基本にする。
- `page.waitForTimeout()`、固定時間の `setTimeout`、shell の `sleep` で同期しない。
- `networkidle` を ready 判定に使わず、画面の期待状態を assertion する。
- timeout を増やす前に、対象、Locator、server log、通信、trace を確認する。
- `force: true` で、利用者が操作できない UI を通過しない。
- navigation 後は `expect(page).toHaveURL()` または `page.waitForURL()` を使う。racy な `waitForNavigation()` は使わない。
- popup、download、request、response の待機 Promise は、event を起こす操作より前に作る。

```ts
const responsePromise = page.waitForResponse(response =>
  response.url().endsWith('/api/orders') &&
  response.request().method() === 'POST',
);
await page.getByRole('button', { name: '注文を確定' }).click();
const response = await responsePromise;

expect(response.status()).toBe(201);
await expect(page.getByRole('heading', { name: '注文を受け付けました' })).toBeVisible();
```

dialog listener を登録した場合は、すべての dialog を accept または dismiss する。

## Web-first assertion を使う

- Locator、Page、APIResponse 用の非同期 matcher を優先し、必ず `await` する。
- `expect(await locator.isVisible()).toBe(true)` のような one-shot read を避ける。
- DOM の変化には `toHaveCount()`、`toHaveText()`、`toHaveAttribute()`、`toHaveURL()`、`toHaveTitle()`、`toMatchAriaSnapshot()` などを使う。
- 任意の非同期値には `expect.poll()`、再試行可能な callback には `expect(async () => { ... }).toPass()` を検討する。副作用が重複しないことを確認する。
- `expect.soft()` は独立した診断情報を集める場合だけ使う。
- 数値や object の通常の `expect()` は retry されない。

## Fixture と Page Object を小さく保つ

- fixture は準備、提供、後片付けを 1 つの責務としてまとめる。
- test-scoped fixture を既定にする。custom worker-scoped fixture は server、account、認証 state など、worker 内で安全に共有できる準備に使う。
- 自動 fixture は、全テストに必要な log 収集や安全な後片付けに限定する。
- fixture timeout は test timeout と分ける。
- Page Object は、画面または再利用する component の意味のある Locator と操作をまとめる。
- 単一の `click()` を言い換えるだけの wrapper を増やさない。
- テストの主題である期待結果は test 側へ残す。
