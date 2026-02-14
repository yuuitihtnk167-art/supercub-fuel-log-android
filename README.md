# Supercub Fuel Log (Android)

Kotlin + Jetpack Compose で作り直した Android アプリです。  
給油記録・履歴・CSV入出力・月次レポートに対応しています。

## セットアップ

1. **Firebase プロジェクト作成**
   - Firestore を有効化
   - Authentication で **Google ログイン** を有効化
   - Android アプリを追加（パッケージ名: `com.yuu.supercubfuellog`）
   - `google-services.json` をダウンロードして `app/` に配置

2. **web client id の設定**
   - `app/src/main/res/values/strings.xml` の `web_client_id` に
     Firebase の **Web クライアント ID** を設定

3. **デバッグ用 SHA-1 の登録**
   - Firebase の Android アプリ設定で **SHA-1** を登録  
     （Android Studio の `gradle signingReport` で取得）

## 起動・ビルド

Android Studio で開いて実行してください。  
ターミナルでビルドする場合は、**先に `gradle wrapper` を実行**してから以下を使います。

```bash
./gradlew assembleDebug
```

生成された APK は以下に出力されます。

```
app/build/outputs/apk/debug/app-debug.apk
```

## 使い方

- 保存先は **保存のたびにローカル/クラウドを選択** できます。
- 画面上部の切り替えで **ローカル / クラウド** の表示を変更できます。
- CSV インポート / エクスポート対応

## 注意

- `google-services.json` を配置しないと Firebase が初期化されません。
- クラウド保存は Google ログインが必要です。
