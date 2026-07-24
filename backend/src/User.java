public class User {
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
