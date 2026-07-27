import java.io.Serializable;
public class User implements Serializable{
    public static final long serialVersionuid=1;
    String username;
    byte[] salt;
    byte[] derivedkey;
    User(String username,String password)throws Exception
    {
        this.username=username;
        this.salt=CryptoEngine.generateSalt();
        CryptoEngine engine=new CryptoEngine();
        this.derivedkey=engine.deriveKey(password,this.salt);
    }
}
