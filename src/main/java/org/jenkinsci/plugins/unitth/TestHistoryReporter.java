package org.jenkinsci.plugins.unitth;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.unitth.entities.TestCase;
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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

// TODO, configurable: no runs back. 0-no-of-available
// TODO, configurable, progress counter....
public class TestHistoryReporter extends Recorder {

   private AbstractProject project = null;
   private static PrintStream logger = null;

   private static ArrayList<TestReport> buildReports = new ArrayList<TestReport>();

   public BuildStepMonitor getRequiredMonitorService() {
      return BuildStepMonitor.NONE;
   }

   @DataBoundConstructor
   public TestHistoryReporter() {}

   @Override
   public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
      throws InterruptedException, FileNotFoundException {
      project = build.getProject();
      logger = listener.getLogger();


      logger.println("[unitth] Calculating history report...");
      // Loop through all builds
      //logger.println("build.getPreviousBuild().getTestResultAction().getDisplayName()" + build.getPreviousBuild().getTestResultAction().getDisplayName());
      //logger.println("build.getPreviousBuild().getTestResultAction().getFailedTests()" + build.getPreviousBuild().getTestResultAction()
      //   .getFailedTests());
      //logger.println("A" + build.getPreviousBuild().getAggregatedTestResultAction());
      //logger.println("B" + build.getPreviousBuild().getAggregatedTestResultAction().getResult());
      //listener.getLogger().println(build.getPreviousBuild().getTestResultAction().getDisplayName());

      readBuildTestReports();
      
      return true;
   }

   private void readBuildTestReports() throws FileNotFoundException {
      final List<? extends AbstractBuild<?, ?>> builds = project.getBuilds();
      for (AbstractBuild<?, ?> currentBuild : builds) {
         logger.println("BUILD: "+currentBuild.getNumber());
         logger.println("jenks: "+Jenkins.getInstance().getRootUrl());
         logger.println("build: "+currentBuild.getRootDir());
         File f = new File(currentBuild.getRootDir()+"/junitResult.xml"); ///+"/build/"+currentBuild.getNumber()+"/junitResult.xml"); // FIXME,
         // what about testng or custom?
         logger.println("daFile: "+f.getAbsoluteFile());
         if (f.exists()) {
            parseReport(f);
         }
         logger.println("junit: "+currentBuild.getRootDir());
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

      @Override
      public void startElement(String uri, String localName, String qName,
         Attributes attributes) throws SAXException {
         this.elementStack.push(qName);
         System.out.println("startElem: "+qName);
         if (qName.equals(SUITE)) {
            testSuite = new TestSuite();
         }
         else if (qName.equals(CASE)) {
            logger.println("startElement:case");
            testCase = new TestCase();
         }
         else if (qName.equals(CLASS_NAME)) {
            logger.println("startElement:className");
            testCase = new TestCase();
         }
         else if (qName.equals(TEST_NAME)) {
            logger.println("startElement:testName");
         }
         else if (qName.equals(SKIPPED)) {
            logger.println("startElement:skipped");
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
         else if (qName.equals(TEST_NAME)) {
            testCase.setName(new String(ch, start, length));
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
         if (qName.equals(CLASS_NAME)) {
            logger.println("endElement:className");
         }
         else if (qName.equals(TEST_NAME)) {
            logger.println("endElement:testName");
         }
         else if (qName.equals(SKIPPED)) {
            logger.println("endElement:skipped");
         }
         else if (qName.equals(CASES)) {

         }
         else if (qName.equals(CASE)) {
            testSuite.addTestCase(testCase);
         }
         else if (qName.equals(SUITE)) {
            tr.addSuite(testSuite);
            testSuite = null; // Resetting
         }
      }

      @Override
      public void endDocument() throws SAXException {
         System.out.println("Adding tr: "+tr.toString());
         buildReports.add(tr);
      }

      @Override
      public void startDocument() throws SAXException {
         tr = new TestReport();
         System.out.println("Adding tr: "+tr.toString());
      }
   }
}
