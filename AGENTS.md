# 開発方針

- このリポジトリでは Android 向けの Vlog アプリを開発する。
- 対応 OS は Android 16 以上とする。Android プロジェクト作成時の `minSdk` は 36 とし、`compileSdk`・`targetSdk` は 36 以上で使用するツールチェーンに合わせる。
- `.agents/skills/` に Google / Android 公式スキルと、`sasakiy84/agent-toolbox` の `pkg00-base`・`pkg01-dev` を導入している。作業に該当する `SKILL.md` を読み、その指示を適用する。
- 会話では `sasakiy-chat`、実装では `sasakiy-coding`、Git 操作では `sasakiy-git`、検証では `sasakiy-testing` を参照する。UI 作業では `sasakiy-ui`、Android の各機能には該当する公式スキルを使う。
- ユーザーから求められない限りコミットしない。

## ビルドとリリース

- `README.md` にビルド・署名・配布手順を記載している。通常の検証は `python3 -m unittest discover -s scripts -p 'test_*.py' -v` と `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` を使う。
- クラウドでは `/workspace/android-dev/env.sh` を source し、Gradle の実行には `python3 scripts/gradle-cloud.py` を使う。完全な JDK 21 と既存のプロキシ・CA 設定を使用する。
- push やバージョン指定リリースを依頼された場合は、検証、必要なコミットと push、Actions の監視、成果物の確認までエージェントが実施する。通常の各段階で再確認は求めない。権限や資格情報が実際に不足している場合だけ、必要な操作を具体的に伝える。
- リリースには `python3 scripts/release.py 1.2.3` を使い、署名付き APK とチェックサムが GitHub Releases に揃うまで確認する。検証だけの場合は `Android release` を `publish=false` で実行する。
- 署名鍵・パスワード・トークンをコミットやログに含めない。公開証明書のハッシュ `release-certificate.sha256` と APK の署名を照合する。鍵を勝手に再生成・交換しない。
