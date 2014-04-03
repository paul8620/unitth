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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
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
      //PluginAction pa = new PluginAction(project);
      /*
      if (testCaseMatrix!=null) {
         pa.setBuildNumbers(buildNumbers);
         pa.setTheMatrix(testCaseMatrix);
         LOG_MESSAGE+="-> getProjectActions TCM.size: "+testCaseMatrix.size();
      }
      */
      Collection<PluginAction> collection = new ArrayList<PluginAction>();
      //collection.add(pa);
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
      generateMatrix(project.getBuildDir());
      publishReport();

      //String hudsonUrl = Hudson.getInstance().getRootUrl();
      //AbstractProject job = build.getProject();
      //reportLines.add("<script type=\"text/javascript\">test-matrix.html.innerHTML=\"Back to " + job.getName() + "\";</script>");
      // If the URL isn't configured in Hudson, the best we can do is attempt to go Back.
      //if (hudsonUrl == null) {
      //   reportLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").onclick = function() { history.go(-1); return false; };
      //</script>");
      //} else {
      //   String jobUrl = hudsonUrl + job.getUrl();
      //   reportLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").href=\"" + jobUrl + "\";</script>");
      //}
      //reportLines.add("<script type=\"text/javascript\">document.getElementById(\"zip_link\").href=\"*zip*/" + reportTarget.getSanitizedName() + ".zip\";"
      //   + "</script>");


      int diff = buildNumbers.last()-buildNumbers.first(); // To be able to find spread size
      String[][] ss = new String[testCaseMatrix.size()][diff];
      logger.println("Rows: "+testCaseMatrix.size()+" Columns: "+diff+" (from "+buildNumbers.last()+"-"+buildNumbers.first()+" )");
      logger.println(LOG_MESSAGE);

      // Build page PluginAction/summary.jelly
      // TODO: Configurable when setting up the job.
      if (true) {
//         PluginAction pa = new PluginAction(project);
//         build.addAction(pa);
//         build.save();
      }
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
      logger.println("called getTestFailingMatrixes");
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
   private final String LF = "\n";
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
      // HTML string
      StringBuffer sb = new StringBuffer();
      sb.append("<html>"+LF);
      sb.append("<head>"+LF);
      sb.append(theCss());
      sb.append("</head>"+LF);
      sb.append("<body>"+LF);
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
      sb.append("</body>"+LF);
      sb.append("</html>"+LF);

      // Write to file in the correct location
      File folder = new File(rootDir, "thx");
      folder.mkdir();
      File f = new File(folder, "test-matrix.html");
      f.createNewFile();
      BufferedWriter out = new BufferedWriter(new FileWriter(f));
      out.write(sb.toString());
      out.flush();
      logger.println("[unitth] Wrote test history matrix to '"+f.getCanonicalFile()+"'");
      out.close();
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
         sb.append(t(4));
         sb.append("<td class=\""
            +cssClass
            +"\" align=\"center\">&nbsp;&nbsp;"
            +"</td>"+LF);
      }
   }

   private void publishReport() {

   }
   /*
TD.graphPercent {
	BACKGROUND: #ffffff;
	BORDER-BOTTOM: #dcdcdc 1px solid;
	BORDER-RIGHT: #dcdcdc 1px solid;
}

TD.graphBarLeft {
	BACKGROUND: #ffffff;
	BORDER-BOTTOM: #dcdcdc 1px solid;
	FONT-WEIGHT: bold;
}

TD.graphBar {
	BACKGROUND: #ffffff;
	BORDER-BOTTOM: #dcdcdc 1px solid;
	BORDER-RIGHT: #dcdcdc 1px solid;
	WIDTH: 50%;
}

TABLE.barGraph {
	WIDTH: 100%;
}

TD.prpass {
	FONT-SIZE: 2px;
	BACKGROUND: #00df00;
	BORDER-LEFT: #9c9c9c 1px solid;
	BORDER-TOP: #9c9c9c 1px solid;
	BORDER-BOTTOM: #9c9c9c 1px solid;
}

TD.prfail {
	FONT-SIZE: 2px;
	BACKGROUND: #df0000;
	BORDER: #9c9c9c 1px solid;
}

TD.prNan {
	FONT-SIZE: 2px;
	BACKGROUND: #FFFF00;
	BORDER: #9c9c9c 1px solid;
}

TD.pass {
	FONT-SIZE: 2px;
	BACKGROUND: #00df00;
	BORDER-TOP: #4AA02C 1px solid;
	BORDER-BOTTOM: #347235 1px solid;
	BORDER-RIGHT: #347235 1px solid;
	BORDER-LEFT: #4AA02C 1px solid;
}
TD.fail {
	FONT-SIZE: 2px;
	BACKGROUND: #df0000;
	BORDER-TOP: #7E3117 1px solid;
	BORDER-BOTTOM: #7E2217 1px solid;
	BORDER-RIGHT: #7E2217 1px solid;
	BORDER-LEFT: #7E3117 1px solid;
}
TD.ignored {
	FONT-SIZE: 2px;
	BACKGROUND: #FFFF00;
	BORDER-TOP: #9c9c9c 1px solid;
	BORDER-BOTTOM: #717D7D 1px solid;
	BORDER-RIGHT: #717D7D 1px solid;
	BORDER-LEFT: #9c9c9c 1px solid;
}

TD.norun {
	FONT-SIZE: 2px;
	BACKGROUND: #C2DFFF;
	BORDER-TOP: #9c9c9c 1px solid;
	BORDER-BOTTOM: #717D7D 1px solid;
	BORDER-RIGHT: #717D7D 1px solid;
	BORDER-LEFT: #9c9c9c 1px solid;
}

}
    */

   private String theCss() {
      String s = "";
      s+="<style type=\"text/css\">"
         +"table {"
         +"border: 0px solid;"
         +"font-family:Verdana;"
         +"font-size: 10px;"
         +"border-spacing: 0px;"
         +"}"
         +"tr.graphHeaderLeft {"
         +"background: #eff7ff;"
         +"border: #dcdcdc 1px solid;"
         +"padding: 4px 12px 1px 1px;"
         +"text-align: left;"
         +"}"
         +"th.graphHeader {"
         +"background: #eff7ff;"
         +"border-bottom: #dcdcdc 1px solid;"
         +"border-top: #dcdcdc 1px solid;"
         +"border-right: #dcdcdc 1px solid;"
         +"padding: 4px 12px 1px 1px;text-align: left;"
         +"}"
         +"th.graphHeaderLeft {"
         +"background: #eff7ff;"
         +"border: #dcdcdc 1px solid;"
         +"padding: 4px 12px 1px 1px;"
         +"text-align: left;"
         +"}"
         +"td.graphItemLeft {"
         +"background: #ffffff;"
         +"border-bottom: #dcdcdc 1px solid;"
         +"border-left: #dcdcdc 1px solid;"
         +"border-right: #dcdcdc 1px solid;"
         +"padding-left: 15px;"
         +"padding-right: 15px;"
         +"font-weight: bold;"
         +"font-size: 10px;"
         +"}"
         +"td.graphItem {"
         +"background: #ffffff;"
         +"border-bottom: #dcdcdc 1px solid;"
         +"border-right: #dcdcdc 1px solid;"
         +"padding-left: 15px;"
         +"padding-right: 15px;"
         +"font-weight: bold;"
         +"font-size: 10px;"
         +"}"
         +"TD.pass {"
         +"FONT-SIZE: 2px;"
         +"BACKGROUND: #00df00;"
         +"BORDER-TOP: #4AA02C 1px solid;"
         +"BORDER-BOTTOM: #347235 1px solid;"
         +"BORDER-RIGHT: #347235 1px solid;"
         +"BORDER-LEFT: #4AA02C 1px solid;"
         +"height:10px;"
         +"width:6px;"
         +"}"
         +"TD.fail {"
         +"FONT-SIZE: 2px;"
         +"BACKGROUND: #df0000;"
         +"BORDER-TOP: #7E3117 1px solid;"
         +"BORDER-BOTTOM: #7E2217 1px solid;"
         +"BORDER-RIGHT: #7E2217 1px solid;"
         +"BORDER-LEFT: #7E3117 1px solid;"
         +"height:10px;"
         +"width:6px;"
         +"}"
         +"TD.ignored {"
         +"FONT-SIZE: 2px;"
         +"BACKGROUND: #FFFF00;"
         +"BORDER-TOP: #9c9c9c 1px solid;"
         +"BORDER-BOTTOM: #717D7D 1px solid;"
         +"BORDER-RIGHT: #717D7D 1px solid;"
         +"BORDER-LEFT: #9c9c9c 1px solid;"
         +"height:10px;"
         +"width:6px;"
         +"}"
         +"TD.norun {"
         +"FONT-SIZE: 2px;"
         +"BACKGROUND: #C2DFFF;"
         +"BORDER-TOP: #9c9c9c 1px solid;"
         +"BORDER-BOTTOM: #717D7D 1px solid;"
         +"BORDER-RIGHT: #717D7D 1px solid;"
         +"BORDER-LEFT: #9c9c9c 1px solid;"
         +"height:10px;"
         +"width:6px;"
         +"}"
         +"</style>";
      return s;
   }

   // R E M O V E 2 H E R E

   public String getName() {
      return name;
   }
}
