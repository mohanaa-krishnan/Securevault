import java.io.Serializable;
public class Vaultentry implements  Serializable{
    String website;
    String username;
    byte [] password;

    Vaultentry(String website,String username,byte[] password)
    {
        this.website=website;
        this.username=username;
        this.password=password;
    }
}
