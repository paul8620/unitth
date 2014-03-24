package unitth.parser;

import static org.junit.Assert.assertEquals;

import org.jenkinsci.plugins.unitth.TestHistoryReporter;
import org.jenkinsci.plugins.unitth.entities.TestReport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ParserSingleBuildTest {

   private static TestReport tr = null;

   @BeforeClass
   public static void beforeAll() throws Exception {
      TestHistoryReporter thr = new TestHistoryReporter();

      // Reflection stuff
      Field logger = thr.getClass().getDeclaredField("logger");
      logger.setAccessible(true);
      logger.set(thr, new PrintStream(System.out));
      Method parseFile = thr.getClass().getDeclaredMethod("parseReport", File.class);
      parseFile.setAccessible(true);

      System.out.println("File; "+new File(thr.getClass().getResource("/test-junitReport.xml").getFile()));
      System.out.println("THR: "+thr);
      parseFile.invoke(thr, new File(thr.getClass().getResource("/test-junitReport.xml").getFile()));

      Field buildReports = thr.getClass().getDeclaredField("buildReports");
      buildReports.setAccessible(true);
      tr = ((ArrayList<TestReport>)buildReports.get(thr)).get(0);
   }

   @Test
   public void noTests() throws Exception {
      assertEquals("Checking the number of tests in this build result.", 6, tr.getNoTests());
   }

   @Test
   public void noOfSuites() throws Exception {
      assertEquals("Checking the number of suites in this build result.", 2, tr.getNoSuites());
   }

   @Test
   public void suiteReportFile() throws Exception {
      assertEquals("Checking the report file.", "/a/b/c/test-junitResult1.xml", tr.getSuiteByName("a.b.c.Test1").getFile());
      assertEquals("Checking the report file.", "/a/b/c/test-junitResult2.xml", tr.getSuiteByName("a.b.c.Test2").getFile());
   }

   @Test
   public void suiteDuration() throws Exception {
      assertEquals("Checking the suite duration.", 3.0, tr.getSuiteByName("a.b.c.Test1").getDuration(), 0.0);
      assertEquals("Checking the suite duration.", 3.0, tr.getSuiteByName("a.b.c.Test2").getDuration(), 0.0);
   }
}
