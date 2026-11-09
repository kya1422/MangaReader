package jp.reader.manga;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts the WebDAV password; the AES key stays in Android Keystore. */
final class PasswordStore {
  private static final String KEY_ALIAS="manga_reader_webdav_password_v1";
  private static final String CIPHER_KEY="webdav_password_cipher_v1";
  private static final String IV_KEY="webdav_password_iv_v1";
  private static final String LEGACY_KEY="pass";

  private static SecretKey key(boolean create) throws Exception {
    KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
    if(ks.containsAlias(KEY_ALIAS)){
      java.security.Key k=ks.getKey(KEY_ALIAS,null);
      if(!(k instanceof SecretKey))throw new java.security.GeneralSecurityException("保存用の鍵が利用できません");
      return (SecretKey)k;
    }
    if(!create)throw new java.security.GeneralSecurityException("保存用の鍵が見つかりません");
    KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
    return generator.generateKey();
  }

  static synchronized String read(SharedPreferences prefs) throws Exception {
    String encrypted=prefs.getString(CIPHER_KEY,null), iv=prefs.getString(IV_KEY,null);
    if(encrypted!=null || iv!=null){
      if(encrypted==null || iv==null)throw new java.security.GeneralSecurityException("保存されたパスワードのデータが不完全です");
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
      return new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    String legacy=prefs.getString(LEGACY_KEY,"");
    if(prefs.contains(LEGACY_KEY))write(prefs,legacy); // remove old value only after successful encryption and commit
    return legacy;
  }

  /** Explicit destructive recovery only; never discard unreadable data automatically. */
  static synchronized void reset(SharedPreferences prefs) throws Exception {
    if(!prefs.edit().remove(CIPHER_KEY).remove(IV_KEY).remove(LEGACY_KEY).commit())
      throw new java.io.IOException("保存済みパスワードを削除できませんでした");
    KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
    if(ks.containsAlias(KEY_ALIAS))ks.deleteEntry(KEY_ALIAS);
  }

  static synchronized void write(SharedPreferences prefs,String password) throws Exception {
    if(password.isEmpty()){
      if(!prefs.edit().remove(CIPHER_KEY).remove(IV_KEY).remove(LEGACY_KEY).commit())throw new java.io.IOException("パスワード設定を保存できませんでした");
      return;
    }
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));
    byte[] encrypted=cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
    if(!prefs.edit().putString(CIPHER_KEY,Base64.encodeToString(encrypted,Base64.NO_WRAP))
        .putString(IV_KEY,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).remove(LEGACY_KEY).commit())
      throw new java.io.IOException("パスワード設定を保存できませんでした");
  }
}
