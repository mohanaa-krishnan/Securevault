import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class CryptoEngine {
    public  byte []deriveKey(String masterpass,byte[]salt)throws Exception
    {
PBEKeySpec key=new PBEKeySpec(masterpass.toCharArray(),salt,100000,256);

SecretKeyFactory secret=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
byte[] subkey=secret.generateSecret(key).getEncoded();
return subkey;
    }
    public static byte[] generateSalt()
    {
        byte[]bytes=new byte[16];
        SecureRandom random=new SecureRandom();
        random.nextBytes(bytes);
        return bytes;
    }

    public byte[] encrypt(String password,byte[] key)throws Exception,NoSuchAlgorithmException {
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[12];
        random.nextBytes(iv);
       Cipher cipher= Cipher.getInstance("AES/GCM/NoPadding");


        SecretKeySpec  secretkey=new SecretKeySpec(key,"AES");
        GCMParameterSpec spec=new GCMParameterSpec(128,iv);
        cipher.init(Cipher.ENCRYPT_MODE,secretkey,spec);
        byte[] encrypted=cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        byte [] result=new byte[iv.length+ encrypted.length];
        System.arraycopy(iv,0,result,0,iv.length);
        System.arraycopy(encrypted,0,result,iv.length,encrypted.length);
        return result;
    }

    public byte[] decrypt(byte[] encrytedwithiv,byte[] key)throws Exception,NoSuchAlgorithmException {

        byte[] iv = new byte[12];
        byte[] encryteddata=new byte[encrytedwithiv.length-12];
        System.arraycopy(encrytedwithiv,0,iv,0,12);
        System.arraycopy(encrytedwithiv,12,encryteddata,0,encryteddata.length);

        Cipher cipher= Cipher.getInstance("AES/GCM/NoPadding");


        SecretKeySpec  secretkey=new SecretKeySpec(key,"AES");
        GCMParameterSpec spec=new GCMParameterSpec(128,iv);
        cipher.init(Cipher.DECRYPT_MODE,secretkey,spec);

        byte[] decrypted=cipher.doFinal(encryteddata);
        return decrypted;
    }

    public static void main(String[] args) throws Exception {
        Hash temp2=new Hash();
        byte[] salt = generateSalt();
        CryptoEngine temp=new CryptoEngine();
       // System.out.println(salt);

        byte[] key = temp.deriveKey("testpassword", salt);
        byte[] encrypted= temp.encrypt("02122007",key);
        byte[] decrypted= temp.decrypt(encrypted,key);

        System.out.println("Key length: " + key.length);
      System.out.println("Encrypted " + temp2.bytesToString(encrypted));

        System.out.println("Decrypted " + new String(decrypted,StandardCharsets.UTF_8));

    }
}
