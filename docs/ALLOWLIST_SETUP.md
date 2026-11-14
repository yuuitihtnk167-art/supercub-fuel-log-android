# 許可リストの設定と検証（本番未公開）

この方式はGoogle認証後の利用可否を判定します。Firebase Authenticationへのユーザー登録自体を防止するものではありません。

## 締め出しを防ぐ公開順序

1. Firebaseコンソールでプロジェクト `supercub-fuel-log` を選び、Authentication → Usersで自分のGoogleアカウントの行を開き、UIDをコピーします。メールアドレスやOAuthクライアントIDではありません。
2. Firestore DatabaseのStandard `(default)` を選び、コレクション `allowedUsers` を作成します。
3. ドキュメントIDをコピーした自分のUIDと完全一致させ、`enabled` フィールドを **boolean型のtrue** で保存します。文字列 "true" ではありません。
4. ドキュメントパスと型を確認し、`allowedUsers/{自分のUID}` に `enabled: true` が存在することを確認します。まず自分自身を登録してから次へ進みます。
5. 現在の公開済みルールを別ファイルへ控えます。本リポジトリの `firestore.rules` 全文をFirestoreの「ルール」画面へ貼り付け、コンソール上の構文確認を行い、その後に公開します。既存の広い許可ルールを残して追記する方式は避け、全文を置き換えます。
6. 更新版アプリで自分のアカウントの承認と記録読込を確認します。承認されない場合はコンソールでUID・enabledの型・対象プロジェクト・データベースを再確認します。拒否された場合もFirebaseコンソールの管理者権限から修正できます。
7. 追加ユーザーも、まずGoogleログインしてAuthenticationにUIDが作成された後、管理者がそのUIDの `allowedUsers` ドキュメントを登録します。初回は未登録なのでアプリからログアウトされます。登録後にログインし直します。

Firebaseコンソールでの管理にはGoogle Cloud/Firebaseの適切なIAM権限が必要です。アプリのFirebase Authログインは管理権限にはなりません。アプリ用ルールで許可リストの追加・更新・削除をすべて禁止しています。

## 取り消し・通信失敗

- コンソールで `enabled: false` にするか、そのドキュメントを削除すると利用許可を取り消せます。各自の給油記録は削除しません。
- アプリは自分のドキュメントだけを監視します。サーバーから無効・未登録と確認できた時点でデータ・入力・取込状態を破棄し、承認が必要という通知を表示してログアウトします。
- キャッシュに残ったtrueでは承認しません。監視エラーやサーバーとの接続喪失を検知した場合はデータ表示・操作を止め、通信失敗として承認確認を再試行できる画面を表示します。初回のサーバー結果が15秒以内に届かない場合も通信失敗とします。
- アプリ起動・アカウント変更・通常のフォアグラウンド復帰時に再確認します。各クラウド操作の前にもサーバー確認します。ファイル選択からの復帰では選択要求を維持し、後続の操作前確認で検証します。
- ネットワーク越しの取り消し検知には通信遅延があります。サーバー側ルールも常に承認とUIDを検証するため、改造アプリや未更新アプリによる迂回はアプリ内判定だけに依存しません。
- 通信失敗時の同一アカウントの入力・選択済みCSV本文は再試行用に保持します。承認が戻るまで表示・操作しません。明示的拒否・ログアウト・アカウント変更では保持しません。
- 旧ローカルDBは変更・削除しません。CSV入出力はログイン・承認確認後の「設定」→「データ管理」にあり、本人のFirestore記録が対象です。旧端末内記録の書き出しUIはありません。

## データモデル

- `allowedUsers/{uid}`: `enabled` (bool)。自分のドキュメントのみget可能、一覧・他人のget・クライアント書込みは禁止です。ドキュメント全体が本人へ読めるため、管理者の秘密メモをここへ保存しないでください。
- `users/{uid}/records/{recordId}`: date、mileage、fuel、fuelEfficiency、isEstimated、lastUpdatedの6フィールド。通常のアプリ書込みと一致する型・サイズ・値域の検証をcreate/update両方へ適用します。
- 日付のルール検証はYYYY-MM-DDの形式のみです。暦としての日付検証はアプリの入力・CSVパーサーが担当します。
- usersの親ドキュメントは不要です。所有者はパスのUIDと認証UIDの一致で判断し、クライアントが書くownerId等のフィールドは権限に使いません。
- 記録の取得は本人のrecordsコレクション全件取得です。where/orderBy/limitのサーバー条件はありません。許可リストのコレクション検索は行いません。

## 実機確認

- 自分のUIDを登録 → ルール公開 → 自分でログインし、記録表示・新規保存・CSV入出力を確認。
- 別のテストアカウントを未登録でログインし、「利用には管理者の承認が必要です」と表示されログアウトすることを確認。
- enabledをfalseにしたアカウントでも同様に拒否されることを確認。
- 承認済みで起動後、通信を切り、承認確認失敗時に表示データが消えて記録操作できず、再試行できることを確認。
- 通信復旧後の確認成功で本人の記録だけが戻ることを確認。
- テストユーザーが利用中に管理者がenabledをfalseにし、表示データが消えてログアウトすることを確認。自分の管理用UIDで不用意に試さないでください。
- 許可ユーザーA/Bを切り替えて、履歴・入力・CSV取込確認が混ざらないことを確認。
- ログインが残った状態でアプリを終了・起動し、承認確認を経てから記録を表示することを確認。

## ローカル検証

Android: `:app:assembleDebug :app:testDebugUnitTest`

Rules: `cd tests/firestore` → `npm ci` → `npm test`。Java 21以上をPATHへ設定してください。
テストは127.0.0.1:8085と `demo-supercub-access-test` だけを使用し、本番Firebaseには接続・公開しません。

I've set up prototype Security Rules to keep the data in Firestore safe. They are designed to be secure for approved users accessing only their own records, while client allowlist writes and enumeration are denied. However, you should review and verify them before broadly sharing your app. If you'd like, I can help you harden these rules.
