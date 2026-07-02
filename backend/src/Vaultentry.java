import java.io.Serializable;
public class Vaultentry implements  Serializable{
    String website;
    String username;
    String password;

    Vaultentry(String website,String username,String password)
    {
        this.website=website;
        this.username=username;
        this.password=password;
    }
}
