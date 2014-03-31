package org.jenkinsci.plugins.unitth;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ProminentProjectAction;
import org.jenkinsci.plugins.unitth.entities.TestCaseMatrix;
import org.jenkinsci.plugins.unitth.entities.TestCaseVerdict;
import org.kohsuke.stapler.StaplerProxy;

import java.io.Serializable;
import java.util.TreeMap;
import java.util.TreeSet;

//
// Action performed on the build page.
//
public class PluginAction implements ProminentProjectAction,
   Serializable, StaplerProxy {

   private TreeMap<String,TestCaseMatrix> matrix;
   private TreeSet<Integer> buildNumbers;

   @SuppressWarnings("rawtypes")
   private AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project;

   public PluginAction() {}

   public PluginAction(AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project) {
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
   public String[][] getSpreads() {
      int diff = buildNumbers.last()-buildNumbers.first(); // To be able to find spread size
      String[][] ss = new String[matrix.size()][diff+2]; // +2 since it is a range and we need the name
      int row = 0;
      TestHistoryReporter.logger.println("GetSpreads.size:"+matrix.size());
      for (TestCaseMatrix tcm : matrix.values()) {
         ss[row][0] = tcm.getQName();
         for (int column=1; column<diff+2; column++) {
            String verdictString = "-";
            if (tcm.getSpread().get(column)==null) {
               verdictString = ".";
            }
            else if (tcm.getSpread().get(column).getVerdict()==TestCaseVerdict.FAILED) {
               verdictString = "x";
            }
            ss[row][column] = verdictString;
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
}
