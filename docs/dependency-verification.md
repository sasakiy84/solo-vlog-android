# 依存物のチェックサム検証

`gradle/verification-metadata.xml`に、ビルドで取得するJAR・AAR・POM等のSHA-256を記録する。Gradleはこのファイルがある場合、既定でstrict検証する。リリースActionsでも`--dependency-verification=strict`を明示する。

- 本番・JVMテスト・端末テスト・debug/releaseのlintとビルドに必要な依存物を登録する。
- CIでは検証メタデータを生成・更新しない。不一致や未登録の依存物はエラーとして扱う。
- ライブラリ更新時に限り、正規の配布元・バージョン・変更内容を確認したうえで、ローカルで`--write-verification-metadata sha256`を付けて対象タスクを実行し、差分をレビューする。
- 不一致を解消するためだけにハッシュを追加したり、検証を無効化したりしない。
- 初回登録は現行の正規リポジトリから解決した依存物（既存ビルドキャッシュを含む）が基準。上流コードの安全性や、初回取得物の完全な独立監査を保証するものではない。承認後に同じバージョンのファイルが差し替わることの検知に使う。
- 依存ロック、SDK導入用CLIの固定は今回の対象外。Gradle本体の配布ZIPは既存の`distributionSha256Sum`で検証する。Android SDK本体はこのGradleメタデータの対象ではない。

Truthは削除し、テストの比較はJUnit Assertを使用する。JUnit・AndroidX Testは維持する。配布アプリの機能・UIは変更しない。

## 導入時の確認

- Android Gradle PluginのJAR用ハッシュを一時的に不正な値へ変え、`--offline --dependency-verification=strict help`が検証エラーで拒否することを確認。直後に元のファイルへ復元した。
- 正常な一覧でJVMテスト19件・Pythonテスト4件、debug/release lint、アプリ・端末テストAPKのビルドを確認。
- Android端末テストはコンパイル・APK生成までで、端末上の実行は今回未実施。
