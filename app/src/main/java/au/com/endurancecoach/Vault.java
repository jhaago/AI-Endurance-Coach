package au.com.endurancecoach;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;

/** Android Keystore wraps all credential values; no keys in resources or backups. */
final class Vault {
 private final Context context;private static final String ALIAS="endurance.credentials.v1";
 Vault(Context c){context=c.getApplicationContext();}
 private SecretKey key() throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(ALIAS)){KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
  return ((KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null)).getSecretKey();
 }
 synchronized void set(String name,String value) throws Exception {
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
  String encoded=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
  if(!context.getSharedPreferences("vault",0).edit().putString(name,encoded).commit())throw new java.io.IOException("Could not save encrypted settings.");
 }
 synchronized String get(String name) throws Exception {
  String stored=context.getSharedPreferences("vault",0).getString(name,"");if(stored.isEmpty())return "";
  String[] parts=stored.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
 }
 synchronized void clear(){context.getSharedPreferences("vault",0).edit().clear().commit();}
}
