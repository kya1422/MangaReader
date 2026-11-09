package jp.reader.manga;
class TestHttp { public static void main(String[]x){for(int code:new int[]{200,401,403,404,416,500}){String m=HttpErrors.range(code);if(!m.contains(String.valueOf(code)))throw new AssertionError("missing "+code);if(code!=200&&m.contains("部分取得に対応していません"))throw new AssertionError("wrong hint "+code);}System.out.println("PASS HTTP状態ごとに適切なエラーを表示");} }
