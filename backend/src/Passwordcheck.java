import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Passwordcheck {
    public static void main() {
        Scanner in=new Scanner(System.in);
        String pass=in.nextLine();
        if(pass.length()>=8) {


            if (pass.matches(".*[a-z].*")) {
                if (pass.matches(".*[0-9].*")) {
                    if (pass.matches(".*[A-Z].*")) {
                        Pattern p = Pattern.compile("[^A-Za-z0-9]");
                        Matcher m = p.matcher(pass);
                        boolean b = m.find();
                        if (b) {
                            System.out.println("yes");

                        } else {
                            System.out.println("NO special char");
                        }
                    } else {
                        System.out.println("Doesn't contain upper case");
                    }

                } else {
                    System.out.println("Does'nt contain  numbers ");
                }
            } else {
                System.out.println("Doesn't contain lower case");
            }
        }
        else
        {
            System.out.println("Atleast eight charcters needed");
        }
    }
}
