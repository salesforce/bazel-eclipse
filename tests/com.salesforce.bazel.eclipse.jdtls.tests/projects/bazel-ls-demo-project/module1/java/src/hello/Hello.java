package hello;

import library.Greeting;

import log.Logger;

import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Iterables;



public class Hello {


    public static void main(String[] args) {
        Logger.logDebug("Hello.main");

    	  Greeting greeter = new Greeting();
        System.out.println(new mybuilder_sources.MybuilderSources());

        List<String> modules = Lists.newArrayList("module1", "module2");

        Iterable<String> result = Iterables.transform(modules, greeter::greet);

        result.forEach(System.out::println);
    }
}
