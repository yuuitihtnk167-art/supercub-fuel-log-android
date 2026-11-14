# Firestoreルール検証メモ

本番未公開。Androidビルド成功・ユニットテスト24件成功。Firestore Emulator v1.22.0（Standard）のルールテスト21件成功（0失敗）。テスト定義: tests/firestore/rules.test.mjs。

## 静的分析
全Firestoreパスは allowedUsers/{uid} と users/{uid}/records/{recordId}。
allowedUsersは自分のドキュメントの監視/getのみ。recordsは本人コレクションの全件get、
対象ドキュメントのトランザクション読書き、200件ずつの取込・削除。
管理権限はコンソール/IAMのみ。クライアント属性やメール文字列から権限を作らない。

## 反証チェック
| 攻撃・条件 | ルール上の対応・テスト |
|---|---|
| 1. 未認証の一覧取得 | 全対象で拒否。自分の承認getにも認証必須 |
| 2. 他人のCRUD | 承認されたAから承認されたBへの全操作を拒否 |
| 3. updateによる検証迂回 | create/updateで同じisValidRecordを使用 |
| 4. 作成時のowner詐称 | UIDパスで所有権を確認、ownerId追加はhasOnlyで拒否 |
| 5. 更新時のowner詐称 | 同上。別パスへの書込はUID不一致で拒否 |
| 6. 不変属性変更 | ownerはパス由来。lastUpdatedは更新可能メタデータで権限には不使用 |
| 7. 型すり替え | mileage文字列/isEstimated文字列等のcreate/updateを拒否 |
| 8. 必須フィールド削除 | date欠落等のcreate/updateを拒否 |
| 9. 巨大文字列・配列 | dateは10文字固定。他のフィールドは型・値域を制約 |
| 10. 必須値未指定 | hasAllと型チェックで拒否 |
| 11. 自己昇格 | allowedUsersのクライアントcreate/update/deleteをすべて拒否 |
| 12. 任意フィールド追加 | hasOnlyで拒否 |
| 13. 不正状態遷移 | 承認はクライアントで変更不可。他の業務状態遷移フィールドはなし |
| 14. パス越境 | UIDパスを認証UIDと照合。クライアント指定の外部パスフィールドなし |
| 15. 時刻詐称 | lastUpdatedは型・非負・上限のみ。サーバー権限判定には使わない |
| 16. 負値・無限大 | fuel/mileage/efficiencyの値域を制約。負値・Infinityのテスト |
| 17. 個人情報混在 | 他人の承認とusers/recordsは読み取れない。承認docへ管理者秘密メモを置かない運用 |
| 18. 再送・カウンター | カウンターなし。CSVは固定IDと既存doc非上書きで再送時の増殖を防ぐ |
| 19. 親docなしの子アクセス | 本アプリは親users docを作らない設計。承認済み本人UIDの子だけ許可する |
| 20. クエリ不整合 | 本人records全件取得を許可。allowedUsers一覧は常に拒否 |
| 21. Validator呼出漏れ | records create/update両方で共通validatorを必須にする |

## 追加シナリオ
- allowedUsersの未登録/false/文字列trueで記録アクセスを拒否。
- 本人のfalse/欠落ドキュメントのgetは可能。承認確認のため未承認でもget自体は拒否しない。
- 監視中の取り消し、削除、再承認。新規のサーバー操作が拒否/再許可される。
- 200件の取込トランザクションでルールのアクセス回数制約に抵触しないことをエミュレーターで確認済み。
- Kotlin側ではキャッシュ・通信失敗・再試行・アカウント変更・取り消しで承認リースを失効する。

ルールはPrototypeであり、デバイスからの本番通信・コンソール公開手順は別途実機確認が必要。
