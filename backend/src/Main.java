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

    ArrayList<Vaultentry>vaults=new ArrayList<>();
    boolean running=true;
    while(running) {
    System.out.println("0.nothing");
    System.out.println("1.add credentials");
    System.out.println("2.view credentials");
    System.out.println("3.Delete credentials");
    System.out.println("4.edit credentials");

    int check=in.nextInt();
    in.nextLine();


        if (check == 1) {
            System.out.println("enter website or app name:");
            String website = in.nextLine();
            System.out.println("enter username/email");
            String email = in.nextLine();
            System.out.println("enter password");
            String password = in.nextLine();
            Vaultentry v = new Vaultentry(website, email, password);
            vaults.add(v);
        }
       else  if (check == 2) {
            if (vaults.isEmpty()) {
                System.out.println("Vault is empty");
            } else {
                for (Vaultentry entry : vaults) {
                    System.out.println("website:" + entry.website);
                    System.out.println("email/username:" + entry.username);
                    System.out.println("password:" + entry.password);
                }
            }
        }
       else  if(check==3)
        {
            if(vaults.isEmpty())
            {
                System.out.println("Vault is empty");
            }
            else
            {
                System.out.println("Websites");
                for(Vaultentry entry :vaults)
                {
                    System.out.println(entry.website);
                }
                System.out.println("Choose the website:");
                String temp=in.nextLine();
                boolean found2=false;
                for(int i=0;i<vaults.size();i++)
                {
                    if(temp.equals(vaults.get(i).website))
                    {
                        found2=true;
                        vaults.remove(i);
                    }
                }
                if(found2)
                {
                    System.out.println("Removed successfully");
                }
                else
                {
                    System.out.println("Not such credentials exsits");
                }
            }
        }
       else if(check==4)
        {
            if(vaults.isEmpty())
            {
                System.out.println("Vault is empty");
            }
            else
            {
                System.out.println("Websites");
                for(Vaultentry entry:vaults)
                {
                    System.out.println(entry.website);
                }
                System.out.println("Choose the website:");
                String temp=in.nextLine();
                boolean found2=false;
                for(Vaultentry entry :vaults)
                {
                    if(temp.equals(entry.website))
                    {
                        found2=true;
                        System.out.println("1.password update");
                        System.out.println("2.email update");
                        int check2=in.nextInt();
                        in.nextLine();
                        if(check2==1)
                        {
                            System.out.println("Enter new password:");
                            String newpassword=in.nextLine();
                            entry.password=newpassword;
                        } else if (check2==2) {
                            System.out.println("Enter new mail:");
                            String newemail=in.nextLine();
                            entry.username=newemail;

                        }

                    }
                }
                if(found2)
                {
                    System.out.println("Updated successfully");
                }
                else
                {
                    System.out.println("Not such credentials exsits");
                }
            }
        }
       else if(check==0)
        {
            running=false;
            System.out.println("Out of process");
        }
    }
}
else
{
    System.out.println("Login failed");
}


    }
}
