package at.lucny.p2pbackup.core.support;

import java.io.Console;
import java.util.Scanner;

/**
 * Helper-class to read from console or System.in.
 */
public class UserInputHelper {

    @SuppressWarnings("java:S106") // suppress warning about System.out-usage
    public String read(String message) {
        Console console = System.console();
        if (console != null) {
            return console.readLine(message);
        } else {
            System.out.println(message);
            Scanner scanner = new Scanner(System.in);
            return scanner.nextLine();
        }
    }
}
