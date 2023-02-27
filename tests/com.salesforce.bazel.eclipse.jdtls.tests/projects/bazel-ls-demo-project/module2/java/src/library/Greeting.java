package library;

import log.Logger;

public class Greeting {

    public String greet(String name) {
        Logger.logDebug("Greeting.greet");
        return "Hello ".concat(name);
    }

}
