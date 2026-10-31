# daylog

Android 16 以上向けの、短い動画を1日分にまとめる個人用 Vlog アプリです。

[最新版をダウンロード](https://github.com/sasakiy84/solo-vlog-android/releases/latest)

## 使い方

1. 2・3・5秒を選んで撮影。
2. 文字・場所を選び、「保存」で1日の一覧へ。
3. 必要なら個別クリップでトリミングし、「レンダリング」で動画を作成・確認。
4. 保存名を確認し、「保存」で `Movies/Daylog` に保存（同名は上書き）。

設定で動画の向き、1日の区切り時刻、位置情報、1時間ごとの撮影通知を変更できます。

## リリース

```sh
python3 scripts/release.py 1.0.6
```

GitHub Actions はリリース用のみです。テスト・lint は実行せず、署名付き APK とチェックサムを GitHub Releases に公開します。
