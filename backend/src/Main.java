import java.util.ArrayList;
import java.util.Scanner;
public class Main {
    public static void main(String[] args) {

        Scanner in=new Scanner(System.in);
        ArrayList<User>users=new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            System.out.println("Enter username"+i+":");
            String name=in.nextLine();
            System.out.println("Enter password"+i+":");
            String password=in.nextLine();
            User u=new User(name,password);

            users.add(u);
        }

        for (int i = 0; i < 1; i++) {
            System.out.println(users.get(i).username);
            System.out.println(users.get(i).password);
        }
        System.out.println("enter login username");
    String loginuser=in.nextLine();
    System.out.println("enter login password");
    String loginpassword=in.nextLine();
    boolean found=false;
    for (User user:users)
    {
        if(user.username.equals(loginuser) && user.password.equals(loginpassword))
        {
            found=true;
            break;
        }
    }


if(found)
{
    System.out.println("Login successful");
}
else
{
    System.out.println("Login failed");
}

    }
}
