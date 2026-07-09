import java.security.*;

public class Hash {

    public static void main(String[] args)throws Exception
    {
        String data="hello";
        String algo="SHA256";
        byte[]salt=createsalt();
        System.out.println("SHA256:"+genreate(data,algo,salt));

    }
    private static String genreate(String data,String algo,byte[] salt)throws NoSuchAlgorithmException
    {
MessageDigest digest=MessageDigest.getInstance(algo);
digest.reset();
digest.update(salt);
byte[]hash=digest.digest(data.getBytes());
return bytesToString(hash);
    }
    private final static char[]hexarray="0123456789ABCDEF".toCharArray();
    public static String bytesToString(byte[]hash)
    {
        char[]hexchar=new char[hash.length*2];
        for(int i=0;i<hash.length;i++)
        {
            int v=hash[i]&0xFF;
            hexchar[i*2]=hexarray[v>>>4];
            hexchar[i*2+1]=hexarray[v&0x0F];

        }
        return new String(hexchar);
    }
    public static byte[] createsalt()
    {
        byte[]bytes=new byte[20];
        SecureRandom random=new SecureRandom();
        random.nextBytes(bytes);
        return bytes;
    }
}
