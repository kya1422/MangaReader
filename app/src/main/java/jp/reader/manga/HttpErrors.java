package jp.reader.manga;
final class HttpErrors {
  static String range(int code){
    if(code==200)return "サーバーが部分取得に対応していません（HTTP 200）。WebDAVとCloudflareのRange設定を確認してください。";
    if(code==401)return "認証に失敗しました（HTTP 401）。ユーザー名・パスワードまたは認証方式を確認してください。";
    if(code==403)return "アクセスが拒否されました（HTTP 403）。証明書やアクセス権限を確認してください。";
    if(code==404)return "ファイルが見つかりません（HTTP 404）。移動または削除された可能性があります。";
    if(code==416)return "取得範囲が不正です（HTTP 416）。ファイルが変更された可能性があります。";
    if(code>=300&&code<400)return "サーバーから転送先が返されました（HTTP "+code+"）。WebDAV URLを確認してください。";
    return "ファイル取得に失敗しました（HTTP "+code+"）。";
  }
}
