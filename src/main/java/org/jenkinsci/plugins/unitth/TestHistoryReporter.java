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
import jenkins.model.Jenkins;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
      PluginAction pa = new PluginAction(project);
      if (testCaseMatrix!=null) {
         pa.setBuildNumbers(buildNumbers);
         pa.setTheMatrix(testCaseMatrix);
         LOG_MESSAGE+="-> getProjectActions TCM.size: "+testCaseMatrix.size();
      }
      Collection<PluginAction> collection = new ArrayList<PluginAction>();
      collection.add(pa);
      return collection;
   }

   @Override
   public boolean needsToRunAfterFinalized() {
      return true;
   }

   @Override
   public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
      throws InterruptedException, FileNotFoundException, IOException {
      LOG_MESSAGE+="-> perform ";
      project = build.getProject();
      logger = listener.getLogger();
      logger.println("[unitth] Calculating test matrix...");
      readBuildTestReports();
      populateMatrix();

      // TEMP
      failureMatrixToConsole();

      int diff = buildNumbers.last()-buildNumbers.first(); // To be able to find spread size
      String[][] ss = new String[testCaseMatrix.size()][diff];
      logger.println("Rows: "+testCaseMatrix.size()+" Columns: "+diff+" (from "+buildNumbers.last()+"-"+buildNumbers.first()+" )");
      logger.println(LOG_MESSAGE);

      // Build page PluginAction/summary.jelly
      // TODO: Configurable when setting up the job.
      /*
      PluginAction buildAction = new PluginAction(project);
      buildAction.setTheMatrix(testCaseMatrix);
      build.addAction(buildAction);
      build.save();
      */
      return true;
   }

   private void readBuildTestReports() throws FileNotFoundException {
      final List<? extends AbstractBuild<?, ?>> builds = project.getBuilds();
      for (AbstractBuild<?, ?> currentBuild : builds) {
         /* REMOVE */
         logger.println("BUILD: "+currentBuild.getNumber());
         logger.println("jenks: "+Jenkins.getInstance().getRootUrl());
         logger.println("build: "+currentBuild.getRootDir());
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
         return "Test history stats (UnitTH)";
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
   public ArrayList<TreeMap<Integer, TestCase>> getTestCaseFailureOnlySpread() {
      ArrayList<TreeMap<Integer, TestCase>> failsOnlySpread = new ArrayList<TreeMap<Integer, TestCase>>();
      for (TestCaseMatrix tcm : testCaseMatrix.values()) {
         if (tcm.hasFailed()) {
            failsOnlySpread.add(tcm.getSpread());
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

   // R E M O V E 2 H E R E

   public String getName() {
      return name;
   }
}
