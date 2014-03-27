package unitth.test;

import static org.junit.Assert.assertEquals;

import org.jenkinsci.plugins.unitth.TestHistoryReporter;
import org.jenkinsci.plugins.unitth.entities.TestCase;
import org.jenkinsci.plugins.unitth.entities.TestReport;
import org.jenkinsci.plugins.unitth.entities.TestSuite;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.TreeSet;

public class ParseMultipleBuildsTest {

   private static ArrayList<TestReport> br = new ArrayList<TestReport>();

   @BeforeClass
   public static void beforeAll() throws Exception {
      TestHistoryReporter thr = new TestHistoryReporter("");

      // Reflection stuff
      Field logger = thr.getClass().getDeclaredField("logger");
      logger.setAccessible(true);
      logger.set(thr, new PrintStream(System.out));
      Method parseFile = thr.getClass().getDeclaredMethod("parseReport", File.class);
      parseFile.setAccessible(true);

      parseFile.invoke(thr, new File(thr.getClass().getResource("/test-junitReport-01.xml").getFile()));
      parseFile.invoke(thr, new File(thr.getClass().getResource("/test-junitReport-02.xml").getFile()));

      Field buildReports = thr.getClass().getDeclaredField("buildReports");
      buildReports.setAccessible(true);
      br = (ArrayList<TestReport>)buildReports.get(thr);
   }

   @Test
   public void noUniqueSuites() {
      TreeSet<String> suiteNames = new TreeSet<String>();
      for (TestReport tr : br) {
         for (TestSuite ts : tr.getTestSuites().values()) {
            suiteNames.add(ts.getName());
         }
      }
      assertEquals("Number of unique suites.", 2, suiteNames.size());
   }

   @Test
   public void noUniqueTestCases() {
      TreeSet<String> testCaseNames = new TreeSet<String>();
      for (TestReport tr : br) {
         for (TestSuite ts : tr.getTestSuites().values()) {
            for (TestCase tc : ts.getTestCases().values())
            testCaseNames.add(tc.getQualifiedName());
         }
      }
      assertEquals("Number of unique test cases.", 6, testCaseNames.size());
   }
}
