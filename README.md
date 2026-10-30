# Solo Vlog Android

Android 16（API 36）以上向けの、短い日常を積み重ねるローカル Vlog アプリ **daylog**。起動すると撮影画面が開き、2・3・5 秒をワンタップで切り替えられます。

- 撮影後は自動保存し、そのまま次の撮影へ。直後の「取り消す」で不要なクリップを戻せます。
- 一日の開始時刻は設定で変更でき、撮影時刻と撮影地のタイムゾーンから既存のクリップも再分類します。初期値は 04:00 です。
- 縦 9:16／横 16:9 は別々の記録にし、向きが違う動画を無理に結合しません。
- 日ごとの一覧では撮影順に全クリップが選択済みです。不要なものだけチェックを外し、一本の動画としてギャラリーの `Movies/Daylog` に保存できます。個別の再生・保存・削除にも対応します。
- 環境音と構図グリッドは切り替え可能。クリップを自動削除せず、ログイン・サーバーへの送信は使いません。アプリの削除前には必要な記録を書き出してください。

## ビルド

JDK 21、Android SDK Platform 36、Build Tools 36.0.0 を使用します。Gradle はチェックサムを固定した Wrapper を使用します。

GitHub Actions は公式 Android CLI から SDK を準備するため、ランナーに `sdkmanager` が導入済みであることを前提にしません。

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
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


## カメラの動作確認とスクリーンショット

専用の Android 16 テスト端末で `./gradlew :app:connectedDebugAndroidTest` を実行すると、CameraX による 2・3・5 秒の撮影、Media3 によるギャラリー保存、区切り時刻と縦横の変更を操作します。テストはカメラとマイクの権限を許可し、撮影用の設定を変更するため、普段使いの端末ではなく専用端末で実行してください。

GitHub Actions の `Android camera journey` は KVM 対応の専用エミュレーターを起動し、同じテストと実画面のスクリーンショットを `daylog-ui-verification` に保存します。重いテストのため手動実行にしています。カメラ映像はエミュレーターの仮想シーンです。

## 撮影導線の参考

[けいすけ氏「FilmLog の使い方」](https://note.com/keisukemiura/n/n7b114c260f82) の、短い定尺撮影、時系列での整列、全素材を選んで始める編集、構図グリッド、環境音を参考にしました。タップの手ブレを避けるため録画前に 350ms の静止時間を置き、指定した秒数はその後から数えます。原本の末尾にはエンコーダー用の短い余裕を確保し、書き出し時に指定の 2・3・5 秒へ揃えます。日常を中断しないことを優先し、毎回の確認画面や色編集・SNS 機能は追加していません。素材の期限付き自動削除も採用していません。
