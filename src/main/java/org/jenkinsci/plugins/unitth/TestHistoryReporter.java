package org.jenkinsci.plugins.unitth;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Hudson;
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

import javax.imageio.ImageIO;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.image.BufferedImage;
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

// TODO, configurable: no runs back. 0-no-of-available
public class TestHistoryReporter extends Publisher {

   private AbstractProject project = null;
   private PrintStream logger = null;
   private String name;

   private ArrayList<TestReport> buildReports = new ArrayList<TestReport>();
   private TreeMap<String,TestCaseMatrix> testCaseMatrix = new TreeMap<String,TestCaseMatrix>();
   private TreeSet<Integer> buildNumbers = new TreeSet<Integer>();

   public BuildStepMonitor getRequiredMonitorService() {
      return BuildStepMonitor.NONE;
   }

   @DataBoundConstructor
   public TestHistoryReporter(final String name) {
      this.name = name;
   }

   @Override
   public Collection<? extends Action> getProjectActions(final AbstractProject<?, ?> project) {
      Collection<Action> collection = new ArrayList<Action>();
      LinkAction la = new LinkAction(project, "thx");
      collection.add(la);
      return collection;
   }

   @Override
   public boolean needsToRunAfterFinalized() {
      return true;
   }

   @Override
   public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
      throws InterruptedException, IOException {
      testCaseMatrix = new TreeMap<String,TestCaseMatrix>();
      buildReports = new ArrayList<TestReport>();
      buildNumbers = new TreeSet<Integer>();
      logger = listener.getLogger();
      logger.println("[unitth] Calculating test matrix...");
      project = build.getProject();
      readBuildTestReports();
      populateMatrix();

      // failureMatrixToConsole();

      generateMatrix(project.getRootDir());
      buildReports = null;
      buildNumbers = null;
      return true;
   }

   private void readBuildTestReports() throws FileNotFoundException {
      final List<? extends AbstractBuild<?, ?>> builds = project.getBuilds();
      for (AbstractBuild<?, ?> currentBuild : builds) {
         File f = new File(currentBuild.getRootDir()+"/junitResult.xml");
         // TODO, what about testng or custom?
         if (f.exists()) {
            parseReport(f);
            buildReports.get(buildReports.size()-1).setBuildNumber(currentBuild.getNumber()); // Get the last one and set the build number.
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
         logger.println("[unitth] Parsing exception: "+e.getMessage());
         e.printStackTrace();
      }
   }

   private void populateMatrix() {
      for (TestReport tr : buildReports) {
         for (TestSuite ts : tr.getTestSuites().values()) {
            for (TestCase tc : ts.getTestCases().values()) {
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

   public class ReportHandler extends DefaultHandler {

      private TestReport tr = null; // Created on document start
      private TestSuite testSuite = null; // Currently parsed test suite
      private TestCase testCase = null; // Currently parsed test case

      private Stack elementStack = new Stack();

      private final String SUITE = "suite";
      private final String FILE = "file";
      private final String NAME = "name";
      private final String DURATION = "duration"; // SUITE, CASE, ALL levels
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
            String chars = new String(ch, start, length);
            if (chars.contains(".")) {
               testCase.setClassName(chars.substring(chars.lastIndexOf(".")+1));
               testCase.setPackageName(chars.substring(0, chars.lastIndexOf(".")));
            } else {
               testCase.setClassName(chars);
            }
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
            if (this.elementStack.peek().equals(SUITE)) {
               testSuite.setDuration(new String(ch, start, length));
            }
            else if (this.elementStack.peek().equals(CASE)) {
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

   public TreeMap<String,TestCaseMatrix> getTestFailingMatrixes() {
      TreeMap<String,TestCaseMatrix> failsOnlySpread = new TreeMap<String,TestCaseMatrix>();
      for (TestCaseMatrix tcm : testCaseMatrix.values()) {
         if (tcm.hasFailed()) {
            failsOnlySpread.put(tcm.getQName(), tcm);
         }
      }
      return failsOnlySpread;
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
      String hudsonUrl = Hudson.getInstance().getRootUrl();
      String jobUrl = hudsonUrl + project.getUrl();
      sb.append("<br><h3><a href=\""+jobUrl+"\">Back to "+project.getName()+"</a></h3>");
      sb.append("<br><br>"+LF);
      sb.append("<table>"+LF);
      sb.append(t(++i)+"<thead>"+LF);
      sb.append(t(++i)+"<tr>"+LF);
      sb.append(t(++i)+"<th class=\"graphHeaderLeft\">ClassName.TestName</th>"
         + "<th class=\"graphHeader\">Runs</th>"
         + "<th class=\"graphHeader\">Passed</th>"
         + "<th class=\"graphHeader\">Failed</th>"
         + "<th class=\"graphHeader\">Skipped</th>"
         + "<th class=\"graphHeader\" align=\"left\" colspan=\"2\">Spread</th>"+LF);
      sb.append(t(--i)+"</tr>"+LF);
      sb.append(t(--i)+"<thead>"+LF);
      sb.append(t(i)+"<tbody>"+LF);
      for(TestCaseMatrix tcm : getTestFailingMatrixes().values()) {
         sb.append(t(++i)+"<tr>"+LF);
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
         i--;
      }
      sb.append(t(--i)+"</tbody>"+LF);
      sb.append("</table>"+LF);

      // Write to file in the correct location
      File folder = new File(rootDir, "thx");
      folder.mkdir();
      File f = new File(folder, "index.html");
      f.createNewFile();

      BufferedWriter out = new BufferedWriter(new FileWriter(f));
      try {
         out.write(Utils.readFile(this.getClass().getResourceAsStream("/org/jenkinsci/plugins/unitth/TestHistoryReporter/header.html")));
         out.write(sb.toString());
         out.write(Utils.readFile(this.getClass().getResourceAsStream("/org/jenkinsci/plugins/unitth/TestHistoryReporter/footer.html")));
         out.flush();
      } catch (Exception e) {
         logger.println("[unitth] Exception thrown when trying to write the report file."+e.getMessage()+e.getStackTrace());
         e.printStackTrace();
      } finally {
         out.close();
      }
      generatePixelImage(folder);
      logger.println("[unitth] Wrote test history matrix to '"+f.getCanonicalFile()+"'");
   }

   private void generatePixelImage(File directory) {
      BufferedImage singlePixelImage = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);

      // then I'm saving the generated image in file:
      File file = new File(directory, "pixel.png");
      try {
         ImageIO.write(singlePixelImage, "png", file);
      } catch (IOException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
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

         if (tc!=null && tc.getVerdict() == TestCaseVerdict.FAILED) {
            // Link to job/test/report in format: http://localhost:8080/job/unitth-matrix/91/testReport/unitth.dummytests.pack1/RandomPassFail1Test/test7/
            String link = Hudson.getInstance().getRootUrl()+project.getUrl()+buildNumber+"/testReport/"+tc.getPackageName()+"/"+tc.getClassName()+"/"+tc
               .getName().replace('.', '_').replace(' ','_').replace('>','_').replace(':','_')+"/";

            sb.append("<td class=\""
               +cssClass
               +"\" align=\"center\"><a href=\""+link+"\"><img src=\"pixel.png\" alt=\""+buildNumber+"\" title=\""+buildNumber+"\" height=\"10\" "
               + "width=\"8\"/></a>"
               +"</td>"+LF);
         } else {
            sb.append("<td class=\""
               +cssClass
               +"\" align=\"center\"><img src=\"pixel.png\" alt=\""+buildNumber+"\" title=\""+buildNumber+"\" height=\"10\" width=\"8\"/></td>"+LF);
         }
      }
   }

   public String getName() {
      return name;
   }
}
