import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.NoSuchAlgorithmException;
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
        byte[]bytes=new byte[20];
        SecureRandom random=new SecureRandom();
        random.nextBytes(bytes);
        return bytes;
    }
    public static void main(String[] args) throws Exception {
        Hash temp2=new Hash();
        byte[] salt = generateSalt();
        CryptoEngine temp=new CryptoEngine();
        System.out.println(salt);

        byte[] key = temp.deriveKey("testpassword", salt);
        System.out.println("Key length: " + key.length);
        System.out.println("Key" + temp2.bytesToString(key));
    }
}
