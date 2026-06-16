package library;

import org.junit.Assert;
import org.junit.Test;

public class GreetingTest {
  @Test
  public void testGreet() {
    Assert.assertEquals("Hello JUnit", new Greeting().greet("JUnit"));
  }
}
