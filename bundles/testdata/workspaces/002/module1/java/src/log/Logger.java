package log;

import java.time.Instant;

public final class Logger {

    private Logger() {
      throw new RuntimeException();
    }

    public static void logDebug(String message) {
        String output = String.format("[DEBUG] %s", message);
        System.out.println(output);
    }

}
