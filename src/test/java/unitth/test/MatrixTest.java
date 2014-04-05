package unitth.test;

import static org.junit.Assert.assertEquals;

import org.jenkinsci.plugins.unitth.TestHistoryReporter;
import org.jenkinsci.plugins.unitth.entities.TestCase;
import org.jenkinsci.plugins.unitth.entities.TestCaseMatrix;
import org.jenkinsci.plugins.unitth.entities.TestCaseVerdict;
import org.jenkinsci.plugins.unitth.entities.TestReport;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;

public class MatrixTest {

   private static ArrayList<TestReport> brr = new ArrayList<TestReport>();
   private static TreeMap<String,TestCaseMatrix> tcmTree = new TreeMap<String,TestCaseMatrix>();
   private static TestHistoryReporter thr = new TestHistoryReporter("");

   @BeforeClass
   public static void beforeAll() throws Exception {
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
      brr = (ArrayList<TestReport>)buildReports.get(thr);
      for (int i=0; i<brr.size(); i++) {
         brr.get(i).setBuildNumber(i+1);
      }

      Field buildNumbers = thr.getClass().getDeclaredField("buildNumbers");
      buildNumbers.setAccessible(true);
      TreeSet<Integer> iii = new TreeSet<Integer>();
      iii.add(1);
      iii.add(2);
      buildNumbers.set(thr, iii);

      Method populateMatrix = thr.getClass().getDeclaredMethod("populateMatrix");
      populateMatrix.setAccessible(true);
      populateMatrix.invoke(thr);

      Field tcmField = thr.getClass().getDeclaredField("testCaseMatrix");
      tcmField.setAccessible(true);
      tcmTree = (TreeMap<String,TestCaseMatrix>)tcmField.get(thr);

      // Sanity check print-outs
      System.out.println("TCM.size() "+tcmTree.size());
      for (TestCaseMatrix tcm : tcmTree.values()) {
         System.out.println("spread.size "+tcm.getSpread().size());
      }

      thr.failureMatrixToConsole();
   }

   @Test
   public void noRunsInMatrix() {
      int i = 0;
      int max = 0;
      for (TestCaseMatrix tcm : tcmTree.values()) {
         i = tcm.getSpread().size();
         if (i>max) {
            max=i;
         }
      }
      assertEquals("No runs at most.", 2, max);
   }

   @Test
   public void noTestCases() {
      assertEquals("No unique test cases.", 6, tcmTree.size());
   }

   @Test
   public void noFailedCases() {
      int i = 0;
      for (TestCaseMatrix tcm : tcmTree.values()) {
         for (TestCase tc : tcm.getSpread().values()) {
            if (tc!=null && tc.getVerdict()==TestCaseVerdict.FAILED) {
               i++;
            }
         }
      }
      assertEquals("Failed test cases...", 4, i);
   }

   @Test
   public void noSkippedCases() {
      int i = 0;
      for (TestCaseMatrix tcm : tcmTree.values()) {
         for (TestCase tc : tcm.getSpread().values()) {
            if (tc!=null && tc.getVerdict()==TestCaseVerdict.SKIPPED) {
               i++;
            }
         }
      }
      assertEquals("Skipped test cases...", 4, i);
   }

   @Test
   public void noPassedCases() {
      int iAll = 0;
      for (TestCaseMatrix tcm : tcmTree.values()) {
         for (TestCase tc : tcm.getSpread().values()) {
            if (tc!=null && tc.getVerdict()==TestCaseVerdict.PASSED) {
               iAll++;
            }
         }
      }
      assertEquals("Passed test cases...", 4, iAll);
   }

   @Test
   public void sortOutFailedTests() {
      ArrayList<TreeMap<Integer, TestCase>> spreads = thr.getTestCaseFailureOnlySpread();
      assertEquals("No unique tests that includes failures", 2, spreads.size());
   }

   @Ignore
   public void htmlOut() throws Exception {
      File f = new File("testreport");
      f.mkdir();
      thr.generateMatrix(f);
   }
}
