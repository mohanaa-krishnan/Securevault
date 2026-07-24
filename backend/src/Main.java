import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Main {
    public static void main(String[] args) throws Exception, ClassNotFoundException {

        Scanner in = new Scanner(System.in);
        ArrayList<User> users = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            System.out.println("Enter username" + i + ":");
            String name = in.nextLine();
            System.out.println("Enter password" + i + ":");
            String password = in.nextLine();
            User u = new User(name, password);




            users.add(u);
        }

        for (int i = 0; i < 1; i++) {
            System.out.println(users.get(i).username);
            System.out.println(users.get(i).derivedkey);
        }
        login(users);


    }


    public static void login(ArrayList<User> userss) throws Exception{
        CryptoEngine engine=new CryptoEngine();

        Scanner in = new Scanner(System.in);
        System.out.println("enter login username");
        String loginuser = in.nextLine();
        System.out.println("enter login password");
        String loginpassword = in.nextLine();
        boolean found = false;
        byte[] logkey = new byte[0];
        for (User user : userss) {
            CryptoEngine enginee=new CryptoEngine();
            byte []attemptedkey=enginee.deriveKey(loginpassword,user.salt);
            if (user.username.equals(loginuser) && Arrays.equals(attemptedkey,user.derivedkey)) {
                found = true;
                logkey=attemptedkey;
                break;
            }
        }
        if (found) {
            System.out.println("Login successful");
            try {
                loadVault(logkey);


            } catch (Exception e) {
                System.out.println("Cannot load file");
            }
        } else {
            System.out.println("Login failed");
        }

    }

    public static void loadVault(byte [] masterkey) throws Exception, ClassNotFoundException {
        Scanner in = new Scanner(System.in);
        ArrayList<Vaultentry> vaults = new ArrayList<>();
        File file = new File("storage.dat");
        if (file.exists() && file.length() != 0) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                vaults = (ArrayList<Vaultentry>) ois.readObject();
            }
        } else {
            vaults = new ArrayList<>();
        }
        boolean running = true;
        while (running) {
            System.out.println("0.nothing");
            System.out.println("1.add credentials");
            System.out.println("2.view credentials");
            System.out.println("3.Delete credentials");
            System.out.println("4.edit credentials");
            System.out.println("5.search credentials");


            int check = in.nextInt();
            in.nextLine();
            running = handleMenuOption(check, vaults,masterkey);


        }

    }

    public static boolean handleMenuOption(int check, ArrayList<Vaultentry> vaults,byte[]masterkey)throws Exception {
        if (check == 1) {
            addcredentials(vaults,masterkey);
            return true;
        } else if (check == 2) {
            viewcredentials(vaults,masterkey);
            return true;
        } else if (check == 3) {
            deletecredentials(vaults);
            return true;
        } else if (check == 4) {
            editcredentials(vaults,masterkey);
            return true;
        }
        else if (check==5)
        {
            searchcredentials(vaults);
            return true;
        }else if (check == 0) {
            try {
                savevault(vaults);
                return false;
            } catch (Exception e) {
                System.out.println("Cannot save file");
            }
        }
        return false;
    }

    public static void addcredentials(ArrayList<Vaultentry> vaults,byte[] masterkey)throws Exception {
        Scanner in = new Scanner(System.in);
        System.out.println("enter website or app name:");
        String website = in.nextLine();
        System.out.println("enter username/email");
        String email = in.nextLine();
        System.out.println("enter password");
        String password = in.nextLine();
        passwordCheck(password);
        CryptoEngine engine=new CryptoEngine();
        byte[] encrypted=engine.encrypt(password,masterkey);
        Vaultentry v = new Vaultentry(website, email, encrypted);
        vaults.add(v);
    }

    public static void viewcredentials(ArrayList<Vaultentry> vaults,byte[] masterkey)throws Exception {
        Scanner in = new Scanner(System.in);
        if (vaults.isEmpty()) {
            System.out.println("Vault is empty");
        } else {
            CryptoEngine engine=new CryptoEngine();
            for (Vaultentry vv : vaults) {
                System.out.println("Website:"+vv.website);
                System.out.println("Email:"+vv.username);
               byte[] decryted=engine.decrypt(vv.password,masterkey);


            }
        }
    }

    public static void deletecredentials(ArrayList<Vaultentry> vaults) {
        Scanner in = new Scanner(System.in);
        if (vaults.isEmpty()) {
            System.out.println("Vault is empty");
        } else {
            System.out.println("Websites");
            for (Vaultentry entry : vaults) {
                System.out.println(entry.website);
            }
            System.out.println("Choose the website:");
            String temp = in.nextLine();
            boolean found2 = false;
            for (int i = 0; i < vaults.size(); i++) {
                if (temp.equals(vaults.get(i).website)) {
                    found2 = true;
                    vaults.remove(i);
                }
            }
            if (found2) {
                System.out.println("Removed successfully");
            } else {
                System.out.println("Not such credentials exsits");
            }
        }
    }

    public static void editcredentials(ArrayList<Vaultentry> vaults,byte[]masterkey)throws Exception {
        CryptoEngine engine=new CryptoEngine();
        Scanner in = new Scanner(System.in);
        if (vaults.isEmpty()) {
            System.out.println("Vault is empty");
        } else {
            System.out.println("Websites");
            for (Vaultentry entry : vaults) {
                System.out.println(entry.website);
            }
            System.out.println("Choose the website:");
            String temp = in.nextLine();
            boolean found2 = false;
            for (Vaultentry entry : vaults) {
                if (temp.equals(entry.website)) {
                    found2 = true;
                    System.out.println("1.password update");
                    System.out.println("2.email update");
                    int check2 = in.nextInt();
                    in.nextLine();
                    if (check2 == 1) {
                        System.out.println("Enter new password:");
                        String newpassword = in.nextLine();
                        passwordCheck(newpassword);
                        byte[] newencrypt=engine.encrypt(newpassword,masterkey);
                        entry.password = newencrypt;
                    } else if (check2 == 2) {
                        System.out.println("Enter new mail:");
                        String new_email = in.nextLine();
                        entry.username = new_email;

                    }

                }
            }
            if (found2) {
                System.out.println("Updated successfully");
            } else {
                System.out.println("Not such credentials exsits");
            }
        }

    }

    public static void savevault(ArrayList<Vaultentry> vaults)
            throws Exception, ClassNotFoundException {


        System.out.println("Out of process");


        try (FileOutputStream fos = new FileOutputStream("storage.dat"); ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(vaults);

        }
    }

    public static void passwordCheck(String passcode) {
        Scanner in = new Scanner(System.in);
        String pass = passcode;
        int score = 0;
        if (pass.length() >= 8) {
            score++;
        } if (pass.matches(".*[a-z].*")) {
            score++;
        }  if (pass.matches(".*[0-9].*")) {

            score++;
        }
        if (pass.matches(".*[A-Z].*")) {
            score++;
        }
        Pattern p = Pattern.compile("[^A-Za-z0-9]");
        Matcher m = p.matcher(pass);
        boolean b = m.find();
                        if (b) {
                    score++;


                }
                        if(score>=0 && score<=2)
                        {
                            System.out.println("weak password");
                        }
       else  if(score>2 && score<=4)
        {
            System.out.println("mid password");
        }
        if(score>4 && score<=5)
        {
            System.out.println("strong password");
        }
            }
        public static void searchcredentials(ArrayList<Vaultentry>vaults)
        {
            Scanner in=new Scanner(System.in);
            System.out.println("Enter website name:");
            String web=in.nextLine();
            boolean found=false;
            for(Vaultentry v:vaults)
            {
                if(v.website.equals(web))
                {
                    System.out.println("Email:"+v.username);
                    System.out.println("Password:"+v.password);
                    found=true;
                    break;

                }
            }
            if(found)
            {
                System.out.println("Credentials found");
            }
            else
            {
                System.out.println("Credentials not exsits");
            }
        }
        }





