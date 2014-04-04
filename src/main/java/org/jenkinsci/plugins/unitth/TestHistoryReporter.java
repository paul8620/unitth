package org.jenkinsci.plugins.unitth;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import org.jenkinsci.plugins.unitth.entities.TestCase;
import org.jenkinsci.plugins.unitth.entities.TestCaseMatrix;
import org.jenkinsci.plugins.unitth.entities.TestCaseVerdict;
import org.jenkinsci.plugins.unitth.entities.TestReport;
import org.jenkinsci.plugins.unitth.entities.TestSuite;
import org.kohsuke.stapler.DataBoundConstructor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

// Observed
// Adding and removing the matrix as a post build step will trigger the rendering of the matrix on the job page

// TODO, configurable: no runs back. 0-no-of-available
// TODO, configurable, progress counter....
public class TestHistoryReporter extends Publisher {
//public class TestHistoryReporter extends Recorder {

   private AbstractProject project = null;
   public static PrintStream logger = null; // Change to private
   private String name;

   private static ArrayList<TestReport> buildReports = new ArrayList<TestReport>();
   private static TreeMap<String,TestCaseMatrix> testCaseMatrix = new TreeMap<String,TestCaseMatrix>();
   private static TreeSet<Integer> buildNumbers = new TreeSet<Integer>();

   // REMOVE
   private static String LOG_MESSAGE = "UNSET";

   public BuildStepMonitor getRequiredMonitorService() {
      return BuildStepMonitor.NONE;
   }

   @DataBoundConstructor
   public TestHistoryReporter(final String name) {
      this.name = name;
   }

   //
   // THE MAGIC GLUE!!!! -> triggers the data to end up on the job main page...
   //
   /*
   @Override
   public Action getProjectAction(final AbstractProject<?, ?> project) {
      PluginAction pa = new PluginAction(project);
      if (testCaseMatrix!=null) {
         pa.setBuildNumbers(buildNumbers);
         pa.setTheMatrix(testCaseMatrix);
         LOG_MESSAGE+="-> getProjectAction TCM.size: "+testCaseMatrix.size();
      }
      return pa;
   }
   */

   @Override
   public Collection<? extends Action> getProjectActions(final AbstractProject<?, ?> project) {
      //PluginAction pa = new PluginAction(project);
      /*
      if (testCaseMatrix!=null) {
         pa.setBuildNumbers(buildNumbers);
         pa.setTheMatrix(testCaseMatrix);
         LOG_MESSAGE+="-> getProjectActions TCM.size: "+testCaseMatrix.size();
      }
      */
      Collection<Action> collection = new ArrayList<Action>();
      //LinkAction la = new LinkAction(project, project.getBuildDir()+"/thx/index.html");
      //LinkAction la2 = new LinkAction(project, Hudson.getInstance().getRootUrl()+project.getRootDir()+"/thx/index.html");
      //LinkAction la3 = new LinkAction(project, "thx/index.html"); // <<<<======== DA ONE
      //LinkAction la3 = new LinkAction(project, "thx/test-matrix.html"); // <<<<======== DA ONE
      //LinkAction la4 = new LinkAction(project, Hudson.getInstance().getRootUrl()+project.getBuildDir()+"thx/index.html");
      //LinkAction la5 = new LinkAction(project, Hudson.getInstance().getRootUrl()+project.getUrl()+"thx/index.html");
      LinkAction la6 = new LinkAction(project, "thx");
      //LinkAction la7 = new LinkAction(project, project.getSomeWorkspace()+"thx");
      //LinkAction la8 = new LinkAction(project, "file://Users/Shared/Jenkins/Home/jobs/unitth-matrix/thx/index.html");
      //collection.add(la);
      //collection.add(la2);
      //collection.add(la3);
      //collection.add(la4);
      //collection.add(la5);
      collection.add(la6);
      //collection.add(la7);
      return collection;
   }

   @Override
   public boolean needsToRunAfterFinalized() {
      return true;
   }

   @Override
   public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
      throws InterruptedException, FileNotFoundException, IOException {
      logger = listener.getLogger();
      logger.println("[unitth] Calculating test matrix...");
      LOG_MESSAGE+="-> perform ";
      project = build.getProject();
      readBuildTestReports();
      populateMatrix();

      failureMatrixToConsole(); // TEMP
      logger.println("[unitth] Project URL... " + project.getUrl());

      generateMatrix(project.getRootDir());
      //generateMatrix(project.getBuildDir());
      //generateMatrix(new File(project.getSomeWorkspace().toURI()));

      int diff = buildNumbers.last()-buildNumbers.first(); // To be able to find spread size
      String[][] ss = new String[testCaseMatrix.size()][diff];
      logger.println("Rows: "+testCaseMatrix.size()+" Columns: "+diff+" (from "+buildNumbers.last()+"-"+buildNumbers.first()+" )");
      logger.println(LOG_MESSAGE);
      return true;
   }

   private void readBuildTestReports() throws FileNotFoundException {
      final List<? extends AbstractBuild<?, ?>> builds = project.getBuilds();
      for (AbstractBuild<?, ?> currentBuild : builds) {
         /* REMOVE */
         logger.println("BUILD: "+currentBuild.getNumber()+" / "+currentBuild.getRootDir());
         /* ALL THIS */
         File f = new File(currentBuild.getRootDir()+"/junitResult.xml"); ///+"/build/"+currentBuild.getNumber()+"/junitResult.xml"); // FIXME,
         // what about testng or custom?
         logger.println("daFile: "+f.getAbsoluteFile());
         if (f.exists()) {
            logger.println("parsing . . .");
            parseReport(f);
            buildReports.get(buildReports.size()-1).setBuildNumber(currentBuild.getNumber());
            buildNumbers.add(currentBuild.getNumber());
         }
      }
   }

   private void parseReport(File f) {
      try {
         SAXParserFactory fabrique = SAXParserFactory.newInstance();
         SAXParser parser = fabrique.newSAXParser();

         ReportHandler handler = new ReportHandler();
         parser.parse(f, handler);
      } catch (Exception e) {
         logger.println("Parsing exception: "+e.getMessage());
         e.printStackTrace();
      }
   }

   private void populateMatrix() {
      for (TestReport tr : buildReports) {
         //logger.println("BR:"+tr.getBuildNumber());
         for (TestSuite ts : tr.getTestSuites().values()) {
            //logger.println("TS:"+ts.getName());
            for (TestCase tc : ts.getTestCases().values()) {
               //logger.println("TC:"+tc.getName());
               if (!testCaseMatrix.containsKey(tc.getQualifiedName())) {
                  testCaseMatrix.put(tc.getQualifiedName(), new TestCaseMatrix(tc, tr.getBuildNumber()));
               } else {
                  testCaseMatrix.get(tc.getQualifiedName()).increment(tc, tr.getBuildNumber());
               }
            }
         }
      }
   }

   @Extension
   public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

      @Override
      public String getDisplayName() {
         return "Test history matrix (UnitTH)";
      }

      @Override
      public boolean isApplicable(Class<? extends AbstractProject> jobType) {
         return true;
      }
   }


   public static class ReportHandler extends DefaultHandler {

      private TestReport tr = null; // Created on document start
      private TestSuite testSuite = null; // Currently parsed test suite
      private TestCase testCase = null; // Currently parsed test case

      private Stack elementStack = new Stack();
      private StringBuilder buffer = new StringBuilder();

      private final String RESULT = "result";
      private final String SUITES = "suites";
      private final String SUITE = "suite";
      private final String FILE = "file";
      private final String NAME = "name";
      private final String DURATION = "duration"; // SUITE, CASE, ALL levels
      private final String CASES = "cases";
      private final String CASE = "case";
      private final String CLASS_NAME = "className";
      private final String TEST_NAME = "testName";
      private final String SKIPPED = "skipped";
      private final String ERROR_DETAILS = "errorDetails";

      @Override
      public void startElement(String uri, String localName, String qName,
         Attributes attributes) throws SAXException {
         this.elementStack.push(qName);
         if (qName.equals(SUITE)) {
            testSuite = new TestSuite();
         }
         else if (qName.equals(CASE)) {
            testCase = new TestCase();
         }
         else if (qName.equals(ERROR_DETAILS)) {
            testCase.setVerdict(TestCaseVerdict.FAILED);
         }
      }

      @Override
      public void characters(char[] ch, int start, int length)
         throws SAXException {

         String qName = (String)this.elementStack.peek();

         if (qName.equals(FILE)) {
            testSuite.setFile(new String(ch, start, length));
         }
         else if (qName.equals(NAME)) {
           testSuite.setName(new String(ch, start, length));
         }
         else if (qName.equals(CLASS_NAME)) {
            testCase.setClassName(new String(ch, start, length));
         }
         else if (qName.equals(TEST_NAME)) {
            testCase.setName(new String(ch, start, length));
         }
         else if (qName.equals(SKIPPED)) {
            boolean isSkipped = Boolean.parseBoolean(new String(ch, start, length));
            if (isSkipped) {
               testCase.setVerdict(TestCaseVerdict.SKIPPED);
            }
         }
         else if (qName.equals(DURATION)) {
            String top = (String)this.elementStack.pop();
            if (((String)this.elementStack.peek()).equals(SUITE)) {
               testSuite.setDuration(new String(ch, start, length));
            }
            else if (((String)this.elementStack.peek()).equals(CASE)) {
               testCase.setDuration(new String(ch, start, length));
            }
            else {

            }
            this.elementStack.push(top);
         }
      }

      @Override
      public void endElement(String uri, String localName, String qName)
         throws SAXException {
         this.elementStack.pop();
         if (qName.equals(CASE)) {
            testSuite.addTestCase(testCase);
            testCase = null; // Resetting
         }
         else if (qName.equals(SUITE)) {
            tr.addSuite(testSuite);
            testSuite = null; // Resetting
         }
      }

      @Override
      public void endDocument() throws SAXException {
         buildReports.add(tr);
      }

      @Override
      public void startDocument() throws SAXException {
         tr = new TestReport();
      }
   }

   //
   // GETTERS
   //
   public static ArrayList<TreeMap<Integer, TestCase>> getTestCaseFailureOnlySpread() {
      ArrayList<TreeMap<Integer, TestCase>> failsOnlySpread = new ArrayList<TreeMap<Integer, TestCase>>();
      for (TestCaseMatrix tcm : testCaseMatrix.values()) {
         if (tcm.hasFailed()) {
            failsOnlySpread.add(tcm.getSpread());
         }
      }
      return failsOnlySpread;
   }

   public static TreeSet<Integer> getBuildNumbers() {
      logger.println("called getBuildNumbers");
      return buildNumbers;
   }

   public static TreeMap<String,TestCaseMatrix> getTestFailingMatrixes() {
      TreeMap<String,TestCaseMatrix> failsOnlySpread = new TreeMap<String,TestCaseMatrix>();
      for (TestCaseMatrix tcm : testCaseMatrix.values()) {
         if (tcm.hasFailed()) {
            failsOnlySpread.put(tcm.getQName(), tcm);
         }
      }
      return failsOnlySpread;
   }

   // R E M O V E

   //
   // PRINTERS
   //
   public void failureMatrixToConsole() {
      logger.print("Class.testName || ");
      for (int buildNumber : buildNumbers) {
         logger.print(buildNumber+" ");
      }
      logger.print("\n");
      for (TreeMap<Integer, TestCase> spread : getTestCaseFailureOnlySpread()) {
         logger.print(spread.firstEntry().getValue().getQualifiedName() + " || ");
         for (int buildNumber : buildNumbers) {
            String str = "- ";
            //logger.print("B"+buildNumber+ "-S"+spread.size()+" ");
            if (spread.get(buildNumber) == null) {
               str = ". ";
            }
            else if (spread.get(buildNumber).getVerdict()==TestCaseVerdict.FAILED) {
               str = "x ";
            }
            logger.print(str);
         }
         logger.print("\n");
      }
   }

   public String failureMatrix() {
      String theMatrix = "";
      for (int buildNumber : buildNumbers) {
         theMatrix += buildNumber+" ";
      }
      theMatrix += "\n";
      for (TreeMap<Integer, TestCase> spread : getTestCaseFailureOnlySpread()) {
         theMatrix += spread.firstEntry().getValue().getQualifiedName() + " || ";
         for (int buildNumber : buildNumbers) {
            String str = "- ";
            //logger.print("B"+buildNumber+ "-S"+spread.size()+" ");
            if (spread.get(buildNumber) == null) {
               str = ". ";
            }
            else if (spread.get(buildNumber).getVerdict()==TestCaseVerdict.FAILED) {
               str = "x ";
            }
            theMatrix += str;
         }
         theMatrix += "\n";
      }
      return theMatrix;
   }

   // Support
   private final String LF = System.getProperty("line.separator");
   private final String TAB = "\t";
   private String t(int n) {
      String s = "";
      for (int i=0; i<n; i++) {
         s+=TAB;
      }
      return s;
   }

   public void generateMatrix(File rootDir) throws IOException {
      int i = 0;
      StringBuffer sb = new StringBuffer();
      sb.append("<li id=\"1\" class=\"unselected\" onclick=\"updateBody('1');\" value=\"" + rootDir + "/thx/test-matrix.html\">History matrix</li>"+LF);
      sb.append("<br><br>"+LF);
      sb.append("<tbody>"+LF);
      sb.append(t(++i)+"<table>"+LF);
      sb.append(t(++i)+"<tr>"+LF);
      sb.append(t(++i)+"<th class=\"graphHeaderLeft\">ClassName.TestName</th>"
         + "<th class=\"graphHeader\">Runs</th>"
         + "<th class=\"graphHeader\">Passed</th>"
         + "<th class=\"graphHeader\">Failed</th>"
         + "<th class=\"graphHeader\">Skipped</th>"
         + "<th class=\"graphHeader\" align=\"left\" colspan=\"2\">Spread</th>"+LF);
      sb.append(t(--i)+"</tr>"+LF);

      for(TestCaseMatrix tcm : getTestFailingMatrixes().values()) {
         sb.append(t(i)+"<tr>"+LF);
         sb.append(t(++i)+"<td class=\"graphItemLeft\" align=\"left\" width=\"2*\">"+tcm.getQName()+"</td>");
         sb.append("<td class=\"graphItem\" width=\"1*\">"+tcm.getNoRuns()+"</td>");
         sb.append("<td class=\"graphItem\" width=\"1*\">"+tcm.getNoPassed()+"</td>");
         sb.append("<td class=\"graphItem\" width=\"1*\">"+tcm.getNoFailed()+"</td>");
         sb.append("<td class=\"graphItem\" width=\"1*\">"+tcm.getNoSkipped()+"</td>");
         sb.append("<td class=\"graphItem\" width=\"40*\">"+LF);
         sb.append(t(++i) + "<table class=\"barGraph\" cellspacing=\"0\">" + LF);
         sb.append(t(++i) + "<tbody>" + LF);
         sb.append(t(++i) + "<tr>" + LF);
         generateSpreadBar(sb, tcm);
         sb.append(t(i) + "</tr>" + LF);
         sb.append(t(--i) + "</tbody>" + LF);
         sb.append(t(--i) + "</table>" + LF);
         sb.append(t(--i)+"</td>"+LF);
         sb.append(t(--i)+"</tr>"+LF);
      }
      sb.append(t(--i)+"</table>"+LF);
      sb.append("</tbody>"+LF);
      sb.append("</li>"+LF);
      if (project!=null) {
         sb.append("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").innerHTML=\"Back to " + project.getName() + "\";</script>");
      }

      // Write to file in the correct location
      logger.println("Location to write to: "+rootDir+"/thx");
      File folder = new File(rootDir, "thx");
      folder.mkdir();
      File f = new File(folder, "index.html");
      f.createNewFile();

      BufferedWriter out = new BufferedWriter(new FileWriter(f));
      try {
         out.write(readFile(this.getClass().getResourceAsStream("/org/jenkinsci/plugins/unitth/TestHistoryReporter/header.html")));
         out.write(sb.toString());
         out.write(readFile(this.getClass().getResourceAsStream("/org/jenkinsci/plugins/unitth/TestHistoryReporter/footer.html")));
         out.flush();
      } catch (Exception e) {
         logger.println("[unitth] Exception thrown when trying to write the report file."+e.getMessage()+e.getStackTrace());
         e.printStackTrace();
      } finally {
         out.close();
      }
      logger.println("[unitth] Wrote test history matrix to '"+f.getCanonicalFile()+"'");
   }

   private String readFile(InputStream file) throws Exception {
      BufferedReader reader = new BufferedReader(new InputStreamReader(file));
      String line;
      StringBuffer sb = new StringBuffer();
      String ls = System.getProperty("line.separator");

      while( ( line = reader.readLine() ) != null ) {
         sb.append(line);
         sb.append(ls);
      }
      return sb.toString();
   }

   private void generateSpreadBar(StringBuffer sb, TestCaseMatrix tcm) {
      String cssClass = "";
      for (int buildNumber : buildNumbers) {
         TestCase tc = tcm.getSpreadAt(buildNumber);
         if (tc==null) {
            cssClass = "norun";
         } else {
            TestCaseVerdict tcv = tc.getVerdict();
            if (TestCaseVerdict.PASSED == tcv) {
               cssClass = "pass";
            } else if (TestCaseVerdict.FAILED == tcv) {
               cssClass = "fail";
            } else if (TestCaseVerdict.SKIPPED == tcv) {
               cssClass = "ignored";
            }
         }
         sb.append(t(7));
         sb.append("<td class=\""
            +cssClass
            +"\" align=\"center\">&nbsp;&nbsp;"
            +"</td>"+LF);
      }
   }

   public String getName() {
      return name;
   }
}
