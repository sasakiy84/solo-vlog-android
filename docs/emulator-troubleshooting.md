# クラウドのAndroid起動障害

当時の調査記録。撮影画面の「明るさ」ボタンはその後削除し、現在は設定で自動調整方式を選ぶ。記載する`/tmp`のログ・画像は調査セッション内の一時ファイル。

撮影画面を開けなかった原因は、APKの起動より前に発生したAndroidの起動停止。

- KVMデバイスがなく、x86_64のAndroid 16をTCGとSwiftShaderで実行していた。
- ホストのメモリ・ディスク容量に余裕があり、cgroupのOOM記録は0。
- Android DropBoxの`system_server_watchdog`には、メインスレッドの61秒停止が記録されていた。
- スタックは`RoleService.maybeGrantDefaultRolesSync`で既定ロール設定を待機。CPU pressureの`some avg10`は約79%。Watchdogの再起動により、`package`や`activity`サービスが利用できなくなった。
- 別に、旧端末テスト環境では共有ストレージの`Transport endpoint is not connected`も発生していた。今回のWatchdogと同一原因とは断定しない。

調査用AVDのadbdをroot化して`ro.hw_timeout_multiplier=10`を設定し、Androidフレームワークを再起動。再試行ではロール設定が約7.6秒で完了した（初回処理のキャッシュの影響もあり得る）。起動後にWatchdogが60秒へ戻るため、同AVDの`settings put global watchdog_timeout_millis 600000`も設定した。`sys.boot_completed=1`を確認。

これらはローカルのソフトウェアエミュレーター専用の設定。アプリや配布APKのタイムアウト・動作は変更しない。実機同等の性能確認にはKVM対応環境または実機が必要。

その後、最新デバッグAPKのインストールとMainActivity起動に成功。CameraXの`Preview stream state to STREAMING`と、実際の仮想カメラ映像・「明るさ」ボタンを含む撮影画面を確認した。スクリーンショットは `/tmp/daylog-camera-exposure.png`。

再発時の確認・対処は、調査用AVDに対してのみ以下を使う（`stop/start`はAndroidフレームワークを再起動するため、アプリ実行中には行わない）。

```sh
adb -s emulator-5556 root
adb -s emulator-5556 wait-for-device
adb -s emulator-5556 shell 'setprop ro.hw_timeout_multiplier 10; stop; start'
# settingsサービスの起動後。再起動後に設定値と実際のWatchdogログを再確認する。
adb -s emulator-5556 shell settings put global watchdog_timeout_millis 600000
adb -s emulator-5556 shell getprop sys.boot_completed
```

`ro.*`は読み取り専用プロパティなので、既に値が設定されている端末では`setprop`が失敗し得る。今回の調査用AVDでは未設定だったため設定できた。倍率変更だけで改善したと断定せず、初回処理のキャッシュの影響も区別する。
