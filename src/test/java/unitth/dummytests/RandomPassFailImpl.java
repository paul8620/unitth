package unitth.dummytests;

import static org.junit.Assert.fail;

import java.util.Random;

public class RandomPassFailImpl {
   public RandomPassFailImpl() {
      int r = new Random(System.currentTimeMillis()*new Random().nextInt()).nextInt(100);
      if (r < 34) {
         fail("Random fail as wanted... r="+r);
      }
      System.out.println(r);
   }
}
