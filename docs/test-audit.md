# テスト監査とリファクタリング

対象: アプリのJVMテスト、Android端末テスト、リリーススクリプトのPythonテスト。変更前の40メソッドをすべて下表に記録した。ビルド生成物と導入済みスキル配下の参考コードは対象外。モック呼び出し自体を検証するテストは存在しなかった。

判定は「削除すると見逃す現実的な不具合」と準備・待機・環境依存・修正負担の比較による。件数やカバレッジは目標にしていない。削除した保証を、別のテストで完全に置き換えたとは扱わない。

## コードの整理

- `MovieDraftStore`: 原子的なJSON保存、読み込み、保存先、容量集計、古い結果の削除をモデルから分離。保存キー、既定値、ディレクトリ、失敗時の処理は維持。
- `ClipPreview`: 撮影直後と詳細画面で重複していた消音・ループ・字幕・再試行・解放を共通化。撮影直後のGPS更新に備えたファイル固定も維持。View階層、サイズ、操作ラベルは維持。
- `MovieDraft.displayClips`: レンダリングとプレビューのテキスト／場所の表示判定を一本化。

## 全テストの判断

### app/src/test/java/com/sasakiy84/solovlog/CaptionColorsTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `whiteBackgroundUsesBlackInkAndBlackBackgroundUsesWhiteInk` | 統合 | CaptionRenderingTest | 白地の黒文字は実際の書き出しで確認。黒地専用ケースは茶色地の白文字と同じ判断側のため削除。黒だけの回帰は個別には検知しなくなる。 |
| `midtonesAndLightGrayPreferWhite` | 削除 | — | 灰色3色を同じ閾値の白文字側で並べるケース分け。室内の茶色地による書き出し回帰に絞る。灰色だけを誤判定する変更の検知は失う。 |
| `indoorBrownWallFromCameraExampleUsesWhite` | 統合 | CaptionRenderingTest | 過去の見づらい室内文字の再発を、計算結果と書き出しの二重確認から、実際の符号化ピクセルの確認へ一本化。 |
| `brightYellowAndDarkBlueUseDifferentInkDespiteBothBeingSaturated` | 維持 | CaptionColorsTest | 最大RGB成分で輝度を代用して黄色と青を同じ判定にする不具合。白・茶色の描画テストでは検知できず、端末不要で安価。 |

### app/src/test/java/com/sasakiy84/solovlog/GeoPointTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `invalidCoordinatesAndAccuracyAreRejected` | 削除 | — | 現在は端末Location由来で、外部の任意座標を取り込む経路がない。人工的な直接生成4例の防御チェックへの依存を削る。将来座標インポートを追加する際は、その入力境界で再検討。プロダクションの検証処理は維持。 |
| `approximateCoordinatesUseFewerDecimals` | 維持 | GeoPointTest | 概算位置にも精密位置と同じ桁数を表示し、精度を誤認させる不具合。 |
| `nearbyHistoryDoesNotSuggestAnotherTravelDestination` | 簡略化 | GeoPointTest | 近隣と離れた旅行先の区別を維持。100m±1mという測地式の数値精度保証をやめ、履歴候補に関係する距離の区別へ。狭い数値誤差は検知対象から外す。 |
| `editingTextAndPlacePreservesCoordinatesAndKeepsTheirValuesSeparate` | 統合 | ClipTest.editingTextAndPlaceKeepsTrimAudioLocationAndCaptureMetadata | 文字・場所の独立編集とGPS保持を、既存の編集メタデータ保持ケースへまとめる。 |

### app/src/test/java/com/sasakiy84/solovlog/JournalRulesTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `justBeforeBoundaryBelongsToPreviousDay` | 維持 | JournalRulesTest | 境界直前／ちょうどのクリップを別の日へ誤分類する不具合。 |
| `midnightBoundaryUsesCalendarDate` | 削除 | justBeforeBoundaryBelongsToPreviousDay | 境界ちょうどを当日扱いする分岐が重複。午前0時というLocalTime値だけの追加例を削る。0時だけの特別扱いを将来導入する場合は再検討。 |
| `changingBoundaryReclassifiesExistingCapture` | 維持 | JournalRulesTest | ユーザーが日付切替時刻を変えても既存動画の分類が更新されない不具合。 |
| `daylightSavingUsesLocalClockRatherThanFixedHourSubtraction` | 維持 | JournalRulesTest | 時刻境界をInstantからの固定時間減算へ置き換えると夏時間切替日に誤分類。通常境界テストとは別の実害。 |
| `portraitAndLandscapeNeverJoinAndExcludedOriginalRemains` | 維持 | JournalRulesTest | 縦横の混在、除外クリップの書き出し混入、一覧からの消失、撮影順の逆転を検知。ファイル自体の存在は元から確認していない。 |
| `firstClipLocksDayFormatEvenIfExcludedOrPreferencesChange` | 維持 | JournalRulesTest | 除外操作や設定変更でその日の縦横が変わり、撮影済み動画と合わなくなる不具合。 |
| `freshDayUsesTheConfiguredFormat` | 維持 | JournalRulesTest | 前日の縦横を新しい日に引き継いでしまう不具合。 |
| `timestampUsesTheCaptureTimezoneAndActualClockEvenBeforeDayBoundary` | 簡略化 | JournalRulesTest | 実時刻の文字と撮影地タイムゾーンを維持。日付境界の再確認だけを削除（境界テストとの重複）。 |
| `captionCanBeTrimmedAndClearedWithoutChangingTheCaptureTime` | 統合 | ClipTest.editingTextAndPlaceKeepsTrimAudioLocationAndCaptureMetadata | 前後空白除去、クリア、撮影情報保持を一つの編集シナリオに統合。 |
| `editingACaptionPreservesOriginalCaptureAndSelectionMetadata` | 統合 | ClipTest.editingTextAndPlaceKeepsTrimAudioLocationAndCaptureMetadata | 期待値用Clipの全引数コピーとrecordの不変性確認を削除。GPS・トリム・音声・日時・向き・選択状態が編集で消える実害を直接確認。 |
| `exportKeepsRequestedDurationOrShortInterruptedRecording` | 移動 | ClipTest | 録画終了の余分なフレームを含める不具合と、短い中断録画を予定秒数へ延ばす不具合を維持。編集対象Clipのテストに集約。 |
| `travelRetainsTheCaptureTimezone` | 維持 | JournalRulesTest | 端末の現在タイムゾーンで旅行先の過去クリップを別の日へ移す不具合。時刻表示テストとは利用経路が異なる。 |
| `selectedFormatHandlesPortraitAndLandscapeNativeSensors` | 簡略化 | JournalRulesTest | 実装と同じ剰余計算で期待値を作るループを削除。縦向き・横向きセンサーに対する4つの具体的な回転値を残す。180°／270°ごとのケースは削り、それら固有の回帰検知は失う。 |

### app/src/test/java/com/sasakiy84/solovlog/LocationTrackerTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `staleAndFutureTravelPositionsAreNotReused` | 維持 | LocationTrackerTest | 古い旅行先や未来時刻の位置を新しいクリップへ誤付与する不具合。 |
| `aFreshAccurateFixIsNotReplacedByANewerCoarseFix` | 維持 | LocationTrackerTest | 新しいだけの粗い位置で良い測位結果を上書きする不具合。 |
| `anOldAccurateFixEventuallyYieldsToACurrentPosition` | 維持 | LocationTrackerTest | 古い高精度位置に固執し、移動後も現在地へ更新できない不具合。 |

### app/src/test/java/com/sasakiy84/solovlog/MovieSourceTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `changingLogicalDayDoesNotReuseAnotherDaysRender` | 統合 | MovieSourceTest.sourceEditsAndDisplayOptionsInvalidateCachedRender | 書き出しキャッシュの無効化条件を一つに集約。前日結果の再利用を検知する入力は残す。 |
| `changingSourceTextOrTimePreferenceRequiresAnotherRender` | 統合・修正 | MovieSourceTest.sourceEditsAndDisplayOptionsInvalidateCachedRender | 同じ無効化契約を統合。旧シグネチャの呼び出しでコンパイル不能だったため現行APIへ修正。SHA-256実装や文字列連結のコピーを期待値にせず、文字・トリム・日付表示等の独立した編集を残す。 |

### app/src/androidTest/java/com/sasakiy84/solovlog/CameraJourneyTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `captureExportAndChangeSettings` | 削除 | 一部を描画テスト・日付ルールで継続／実機チェック参照 | 旧ラベル・旧遷移に依存し、実データの日時を一括変更して復元しない長いシナリオ。2/3/5秒録画、設定UIからの反映、全体の保存導線の自動保証は失う。現行導線の短い実機確認のほうが観察可能性が高い。 |

### app/src/androidTest/java/com/sasakiy84/solovlog/CaptionRenderingTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `encodedTimeAndBothCaptionsKeepInkForEachClip` | 簡略化 | CaptionRenderingTest | 文字が消える・明暗を誤る・次クリップに前の色を引き継ぐ実害は維持。6背景→白と実例の茶色の2背景、準備用の6回のエンコードを削除。計7回のTransformer処理を1回にし、MediaStoreへの画像出力とBitmap.compress等のライブラリ保証の確認も削除。黒・灰色4例のみの色判定回帰は個別には検知しない。 |

### app/src/androidTest/java/com/sasakiy84/solovlog/LocationJourneyTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `postCaptureInputPermissionOneTapPlacesAndGeotaggedExports` | 削除・一部移設 | ClipGeotagWriterTest／LocationMetadataTest／実機チェック | モック位置サービス、Geocoder、権限ダイアログ、旧保存ラベルへの依存と共有データ書換えが大きい。GPS付与後の映像・音声・回転保持は小さなMP4による直接テストへ。権限／候補選択UIとエクスポーター全経路の結線は自動保証を失う。 |

### app/src/androidTest/java/com/sasakiy84/solovlog/LocationMetadataTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `travelTimelineKeepsOffsetsAcrossClipsWithoutGpsAndRemovesMisleadingSingleLocation` | 維持・集約 | LocationMetadataTest | GPSなしの中間クリップによるオフセットずれ、旅行全体に単一地点を付ける誤りを維持。公開スキーマキーをリテラルで確認し定数自身との比較をやめる。位置埋め込みオフで既存GPS・地名を残さない確認も同じポリシーのテストへ集約。 |
| `manualPlaceWithoutPermissionDoesNotInventCoordinates` | 維持 | LocationMetadataTest | 手入力した駅名から架空のGPSを記録してしまう不具合。位置埋め込みオフとは異なる入力。 |

### app/src/androidTest/java/com/sasakiy84/solovlog/MovieDraftTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `adjustmentsAndReadyResultPersistWithoutPublishingToGallery` | 維持・隔離 | MovieDraftTest | 再開時に選択順、テキスト、表示・音声設定や処理結果を失う不具合。専用一時ディレクトリへ隔離し、圧縮設定と位置埋め込みオフの保持も確認。アプリ本来のrenderedフォルダを触らない。 |
| `unsafeRenderIdentifiersCannotReachFilesystem` | 移動・簡略化 | MovieSourceTest.unsafeRenderIdentifiersCannotReachFilesystem | パストラバーサルの代表1入力を端末不要のテストへ。空文字／別のスラッシュ例／例外メッセージの確認は同一拒否分岐への依存なので削除。 |

### app/src/androidTest/java/com/sasakiy84/solovlog/NotificationJourneyTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `hourlyCaptureReminderPermissionToggleAndNotificationOpensCamera` | 削除 | 実機チェック | OSの権限文言、通知領域、待機タイミングに依存し、設定も元に戻さない。実通知の発火・無許可時の動作・タップ遷移と、再開で予定を先延ばししない確認の自動保証は失う。通知実装は変更せず、純粋化の名目で本番コードをテスト都合に分割しない。 |

### app/src/androidTest/java/com/sasakiy84/solovlog/RepositoryMigrationTest.java

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `locationUpgradePreservesExistingClipAndDayCaptions` | 削除 | — | 旧DBスキーマ2のDDLコピーと列構造に密結合。旧バージョン互換を優先しない方針に従い履歴移行専用の保証を外す。既存ユーザーデータの移行処理自体は変更しない。 |
| `captionUpgradePreservesLegacyMetadata` | 削除 | — | 旧DBスキーマ1のDDLコピー。上と同じコスト判断。旧DBからの自動移行の回帰検知を失うが、今回スキーマや移行処理は変更しない。 |

### scripts/test_version.py

| 変更前のテスト | 対応 | 継続先 | 判断・失う保証 |
| --- | --- | --- | --- |
| `test_version_and_tag_have_identical_apk_metadata` | 維持 | scripts/test_version.py | バージョン指定とタグ指定でAPKのバージョン名・コードがずれる配布不具合。 |
| `test_first_version_and_android_version_code_limit` | 維持 | scripts/test_version.py | 初回バージョンを拒否したり、Android上限を超えて配布不能になる不具合。 |
| `test_versions_increase_across_patch_minor_and_major_rollovers` | 簡略化 | scripts/test_version.py | 桁の繰り上がりで更新不能になる不具合を維持。同一桁内の0.1.0→0.1.1を削り4例へ。通常加算自体の確認より、桁の重み付けの契約を優先。 |
| `test_rejects_ambiguous_overflow_and_shell_or_output_injection` | 簡略化 | scripts/test_version.py | 19入力→8入力。先頭ゼロ、要素不足、プレリリース、各桁の上限、0、改行によるGITHUB_OUTPUT混入を残す。空文字・ゼロ位置違い・記号等の同じ正規表現拒否の追加例は削除。これらの入力だけを特別に許可する将来変更の検知は失う。 |

## 補助コード・依存関係

| 対象 | 対応と理由 |
| --- | --- |
| `DeviceImages.java` | 削除。削除対象UIシナリオ専用のスクリーンショット・MediaStore出力。撮影結果の存在と画面の見た目を混ぜて判定していた。 |
| `MovieJourney.java` | 削除。削除対象UIシナリオ専用の旧レンダリング／保存導線とポーリング。 |
| UI Automator依存 | 利用するテストがなくなったため削除。 |
| 動画フィクスチャ | 生成済みの個人情報を含まない約18 KBのMP4を3本追加。描画の準備エンコードと実カメラ撮影を不要にする。生成手順はassets/README.md。 |
| `TestVideos` | フィクスチャをテスト専用キャッシュへコピー。GPS付与で書換える入力も各テストが所有し、終了時に削除する。 |

## 自動保証から外した実機確認

以下は削除したUIテストが担おうとしていた保証であり、残した小さなテストだけでは代替できない。関連する機能を変更する際は、現行導線で実機確認する。今回は実施していない。

- 2／3／5秒の撮影→撮影後画面→保存→一覧、縦横固定、日付切替設定の反映。
- 位置権限の拒否／許可、無許可時に位置を取得しないこと、候補選択、GPS付与後の編集継続。
- 通知許可、毎時リマインダーのオン／オフ、再開で予定を先延ばししないこと、通知タップから撮影画面へ移動。
- 下書きのプレビュー→レンダリング→端末保存→開く、同日ファイル上書き、個別保存、トリミングと音声設定のエクスポーターへの接続。

## 検証

前回の作業ではコンパイルのみ行っていた。2026-11-01のユーザーの補足に合わせ、AGENTS.mdを「テスト・lintはローカルで実行」に訂正して実行した。GitHub Actionsは実行していない。workflowはrelease.ymlのみで、テスト・lint用の削除対象ファイルはなかった。

```sh
source /workspace/android-dev/env.sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
python3 scripts/gradle-cloud.py :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python3 scripts/gradle-cloud.py :app:lintRelease
ANDROID_SERIAL=emulator-5556 python3 scripts/gradle-cloud.py :app:connectedDebugAndroidTest
```

- JVMテスト19件、Pythonテスト4件は成功。
- debug・release両方のlintは成功（各0 errors、37 warnings）。Camera2のOptIn宣言とカメラ権限のSecurityExceptionを明示し、標準テーマ・明示的なアイコン着色を用いる2つのViewだけAppCompatCustomViewを理由付きで除外した。依存更新推奨等の警告は残しており、一括抑制やベースラインの追加は行っていない。
- Android端末テスト5件もローカル実行を試みたが、Android 16ソフトウェアエミュレーターで `/sdcard/Android` に `Transport endpoint is not connected` が発生。APKインストール後にランナーから結果が返らず、今回起動したエミュレーターを停止して中断した。5件は未検証で、テスト成功には含めない。

テスト実行時間の改善値は測定していない。カメラ実機を用いる前節の手動シナリオは別途確認が必要。
