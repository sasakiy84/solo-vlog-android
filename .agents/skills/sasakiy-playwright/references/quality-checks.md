# 品質検査

アクセシビリティ、ARIA snapshot、visual test、browser・device 差を検証するときに読む。

## アクセシビリティを複数の方法で検証する

- role、label、keyboard 操作、focus、dialog の閉じ方を通常の利用経路としてテストする。
- `@axe-core/playwright` の自動 scan は検出可能な違反だけを対象にする。成功を WCAG 適合の証明にしない。
- axe scan の前に、対象画面または component が検証したい状態へ到達したことを web-first assertion で確認する。
- scan の除外や rule 無効化には、対象、理由、追跡先を残す。画面全体を広く除外しない。
- `toMatchAriaSnapshot()` は role、name、state、階層の回帰検知に使う。page 全体より、navigation、form、dialog など責務が明確な範囲を対象にする。
- ARIA snapshot の更新は仕様変更として差分をレビューする。
- 自動検査に加えて、keyboard-only 操作、focus 順序、visible label、色以外の状態表現を確認する。

## Visual test を決定的にする

- baseline を作成した OS、browser、viewport、device scale factor、font、locale、timezone、color scheme を CI と揃える。
- production で使う font をテスト環境にも導入し、font の loading 完了を確認する。
- animation、caret、時刻、乱数、広告、外部画像などの動的要素を制御する。必要な箇所だけ `mask` または `stylePath` で安定化する。
- `toHaveScreenshot()` または `toMatchSnapshot()` の差分許容値は、既知の rendering 差を根拠に最小限で設定する。
- baseline は、意図した UI 変更を実画面と差分画像で確認してから更新する。CI で自動更新しない。
- screenshot だけで機能を検証せず、操作結果を semantic assertion でも確認する。

## Browser・device 差を確認する

- 対応 browser の project で、同じ利用者向け仕様を検証する。
- mobile は viewport だけでなく、touch、isMobile、device scale factor、user agent を必要に応じて含める。
- locale、timezone、color scheme、reduced motion、permission、offline が仕様に影響する場合は project を分ける。
- browser 固有の skip には、対応範囲外または browser 固有機能である理由を書く。
- browser × device × locale の全組み合わせを作らず、リスクに対応する代表 project を選ぶ。
