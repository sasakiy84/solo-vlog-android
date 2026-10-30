# Solo Vlog Android

Android 16（API 36）以上向けの Vlog アプリ。現在は APK のビルド・署名・配布を検証するための最小起動画面のみで、撮影・編集機能は未実装です。

## ビルド

JDK 21、Android SDK Platform 36、Build Tools 36.0.0 を使用します。Gradle はチェックサムを固定した Wrapper を使用します。

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew --no-daemon :app:lintDebug :app:assembleDebug
```

クラウド環境では、最初に `source /workspace/android-dev/env.sh` を実行し、Gradle の代わりに `python3 scripts/gradle-cloud.py` を使うと、既存プロキシを JVM に適用できます。プロキシ設定をリポジトリに保存する必要はありません。

`main` への push と Pull Request で GitHub Actions が検証・ビルドし、debug APK を実行結果の Artifacts に保存します。debug APK は開発用で、配布用署名とは異なります。

## バージョンを指定してリリース

変更をコミットして `main` に push した後、次を実行します。

```sh
python3 scripts/release.py 1.2.3
```

エージェントに「1.2.3 でリリースして」と依頼すれば、コミット・push・実行結果の確認までエージェントが行えます。GitHub Actions の `Android release` を手動実行して番号を指定する方法や、`v1.2.3` タグの push にも対応します。

Actions は検証・lint、署名付き APK のビルド、署名と APK 内のバージョン確認を行い、タグと GitHub Release を作成します。APK、SHA-256 チェックサム、APK メタデータを添付します。既存リリースの上書きと、公開済み番号以下のリリースを拒否します。

署名までを確認して公開しない場合は、`Android release` の `publish` を false にして実行します。検証用 APK は Artifacts に保存され、タグと公開リリースは作成しません。

番号は `MAJOR.MINOR.PATCH`、major は 0〜1999、minor/patch は 0〜999 とします。先頭の 0 と `0.0.0` は使えません。Android の `versionCode` は `major * 1000000 + minor * 1000 + patch` です（`1.2.3` → `1002003`）。

## リリース用の署名

初回に以下の GitHub Actions Repository Secrets を登録します。

| Secret | 内容 |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE` | リリース用 keystore の Base64 |
| `ANDROID_RELEASE_PASSWORD` | keystore と鍵に共通のパスワード |

鍵の alias は `solo-vlog-release` です。署名鍵とパスワードはリポジトリやログに含めません。Actions は一時ファイルを使い、成功・失敗にかかわらず削除します。未署名や debug 鍵でのリリースへの代替は行いません。

署名証明書の公開 SHA-256 を `release-certificate.sha256` に固定し、APK の署名が同じ鍵であることも検証します。このファイルには秘密鍵は含まれません。

ローカルで署名付き APK を作る場合は、秘密を出力せず `ANDROID_KEYSTORE_PATH` と `ANDROID_KEYSTORE_PASSWORD` を設定して、`./gradlew :app:assembleRelease -PreleaseVersion=1.2.3` を実行します。鍵を紛失すると、同じアプリを既存ユーザーの端末で更新できなくなるため、秘密のバックアップを保管してください。

配布先は GitHub Releases です。Google Play への公開は含みません。端末上での動作確認には、Android 16 以上の端末またはエミュレーターを別途使用します。

GitHub 連携に権限が不足する場合は、対象リポジトリだけを許可した fine-grained token をクラウド環境の `SOLO_GITHUB_TOKEN` に安全に登録します。セットアップからエージェントが操作するには、Repository permissions の Secrets・Actions・Contents・Workflows に Read and write が必要です。`scripts/release.py` はこの変数が存在すると GitHub CLI に適用します。GitHub Actions 内部では組み込みの `GITHUB_TOKEN` を使用するため、個人トークンをワークフローの Secrets に登録する必要はありません。
