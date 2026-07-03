import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

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
            System.out.println(users.get(i).password);
        }
        login(users);


    }


    public static void login(ArrayList<User> userss) {
        Scanner in = new Scanner(System.in);
        System.out.println("enter login username");
        String loginuser = in.nextLine();
        System.out.println("enter login password");
        String loginpassword = in.nextLine();
        boolean found = false;
        for (User user : userss) {
            if (user.username.equals(loginuser) && user.password.equals(loginpassword)) {
                found = true;
                break;
            }
        }
        if (found) {
            System.out.println("Login successful");
            try {
                loadVault();
            }
            catch (Exception e)
            {
                System.out.println("Cannot load file");
            }
        }else {
            System.out.println("Login failed");
        }

    }

    public static void loadVault() throws Exception, ClassNotFoundException {
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

            int check = in.nextInt();
            in.nextLine();
            running = handleMenuOption(check, vaults);


        }

    }

    public static boolean handleMenuOption(int check, ArrayList<Vaultentry> vaults) {
        if (check == 1) {
            addcredentials(vaults);
            return true;
        } else if (check == 2) {
            viewcredentials(vaults);
            return true;
        } else if (check == 3) {
            deletecredentials(vaults);
            return true;
        } else if (check == 4) {
            editcredentials(vaults);
            return true;
        } else if (check == 0) {
            try {
                savevault(vaults);
                return false;
            }
            catch (Exception e)
            {
             System.out.println("Cannot save file");
            }
        }
        return false;
    }

    public static void addcredentials(ArrayList<Vaultentry> vaults) {
        Scanner in = new Scanner(System.in);
        System.out.println("enter website or app name:");
        String website = in.nextLine();
        System.out.println("enter username/email");
        String email = in.nextLine();
        System.out.println("enter password");
        String password = in.nextLine();
        Vaultentry v = new Vaultentry(website, email, password);
        vaults.add(v);
    }

    public static void viewcredentials(ArrayList<Vaultentry> vaults) {
        if (vaults.isEmpty()) {
            System.out.println("Vault is empty");
        } else {
            for (Vaultentry vv : vaults) {
                System.out.println(vv.website);
                System.out.println(vv.username);
                System.out.println(vv.password);
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

    public static void editcredentials(ArrayList<Vaultentry> vaults) {
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
                        entry.password = newpassword;
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
}


