package org.jenkinsci.plugins.unitth;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ProminentProjectAction;
import hudson.util.RunList;
import org.jenkinsci.plugins.unitth.entities.TestCase;
import org.jenkinsci.plugins.unitth.entities.TestCaseMatrix;
import org.jenkinsci.plugins.unitth.entities.TestCaseVerdict;
import org.jenkinsci.plugins.unitth.entities.TestReport;
import org.jenkinsci.plugins.unitth.entities.TestSuite;
import org.kohsuke.stapler.StaplerProxy;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

//
// Action performed on the build page.
//
public class PluginAction implements ProminentProjectAction,
   Serializable, StaplerProxy {

   private static Logger theLogger = Logger.getLogger(PluginAction.class.getName());

   private TreeMap<String,TestCaseMatrix> matrix = new TreeMap<String,TestCaseMatrix>();
   private TreeSet<Integer> buildNumbers = new TreeSet<Integer>();
   private static ArrayList<TestReport> buildReports = new ArrayList<TestReport>();
   private static TreeMap<String,TestCaseMatrix> testCaseMatrix = new TreeMap<String,TestCaseMatrix>();

   // Needed?
   @SuppressWarnings("rawtypes")
   private AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project;

   public PluginAction(AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project) {
      theLogger.setLevel(Level.INFO);
      this.project = project;
   }

   // Filter out the 100% passing tests
   public void setTheMatrix(TreeMap<String,TestCaseMatrix> matrix) {
      TreeMap<String,TestCaseMatrix> failuresOnly = new TreeMap<String,TestCaseMatrix>();
      for (TestCaseMatrix tcm : matrix.values()) {
         if (tcm.hasFailed()) {
            failuresOnly.put(tcm.getQName(), tcm);
         }
      }
      this.matrix = failuresOnly;
   }

   public String getIconFileName() {
      return null;
   }

   public String getDisplayName() {
      return null;
   }

   public String getUrlName() {
      return null;
   }

   public Object getTarget() {
      return null;
   }

   // Shall we generate the full HTML cell, with colour and all?
   /**
    * Called from the PluginAction/jobMain.jelly
    * @return The matrix to print out.
    */
   public String[][] getSpreads() throws FileNotFoundException {
      theLogger.info("getSpreads");


      // Try getting all the data at this point.
      //readBuildTestReports();
      //populateMatrix();
/*
      // Filter out fail only
      for (TestCaseMatrix tcm : testCaseMatrix.values()) {
         if (tcm.hasFailed()) {
            matrix.put(tcm.getQName(), tcm);
         }
      }
*/
      buildNumbers = TestHistoryReporter.getBuildNumbers();
      matrix = TestHistoryReporter.getTestFailingMatrixes();

      int diff = buildNumbers.last()-buildNumbers.first(); // To be able to find spread size
      String[][] ss = new String[matrix.size()][diff+2]; // +2 since it is a range and we need the name
      int row = 0;
      TestHistoryReporter.logger.println("GetSpreads.size:"+matrix.size());
      for (TestCaseMatrix tcm : matrix.values()) {
         ss[row][0] = tcm.getQName();
         int column = 1;
         for (int bn : buildNumbers) {
            String verdictString = "-";
            if (tcm.getSpread().get(bn)==null) {
               verdictString = ".";
            }
            else if (tcm.getSpread().get(column).getVerdict()==TestCaseVerdict.FAILED) {
               verdictString = "x";
            }
            else if (tcm.getSpread().get(column).getVerdict()==TestCaseVerdict.SKIPPED) {
               verdictString = "s";
            }
            ss[row][column] = verdictString;
            column++;
         }
         row++;
      }
      return ss;
   }

   public void setBuildNumbers(TreeSet<Integer> buildNumbers) {
      this.buildNumbers = buildNumbers;
   }

   public Object[] getBuildNumbers() {
      return buildNumbers.toArray();
   }

   private void readBuildTestReports() throws FileNotFoundException {
      RunList<? extends AbstractBuild> builds = project.getBuilds();
      for (AbstractBuild<?, ?> currentBuild : builds) {
         /* REMOVE */
         //logger.println("BUILD: "+currentBuild.getNumber());
         //logger.println("jenks: "+ Jenkins.getInstance().getRootUrl());
         //logger.println("build: "+currentBuild.getRootDir());
         theLogger.info("Build: "+currentBuild.getNumber()+" / "+currentBuild.getRootDir());
         /* ALL THIS */
         File f = new File(currentBuild.getRootDir()+"/junitResult.xml"); ///+"/build/"+currentBuild.getNumber()+"/junitResult.xml"); // FIXME,
         // what about testng or custom?
         //logger.println("daFile: "+f.getAbsoluteFile());
         if (f.exists()) {
            //logger.println("parsing . . .");
            theLogger.info("parsing . . .");
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
         //logger.println("Parsing exception: "+e.getMessage());
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
/*
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
*/

   //
   // TMP RIPP FROM THR
   //
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
}
