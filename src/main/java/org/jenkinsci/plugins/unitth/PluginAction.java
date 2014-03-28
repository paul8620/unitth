package org.jenkinsci.plugins.unitth;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ProminentProjectAction;
import org.jenkinsci.plugins.unitth.entities.TestCaseMatrix;
import org.kohsuke.stapler.StaplerProxy;

import java.io.Serializable;
import java.util.TreeMap;
import java.util.TreeSet;

//
// Action performed on the build page.
//
public class PluginAction implements ProminentProjectAction,
   Serializable, StaplerProxy {

   private String theMatrix; // REMOVABLE
   private TreeMap<String,TestCaseMatrix> matrix;
   private TreeSet<Integer> buildNumbers;
/*
   public PluginAction(TreeMap<String,TestCaseMatrix> testCaseMatrix) {
      this.testCaseMatrix = testCaseMatrix;



   }
*/

   @SuppressWarnings("rawtypes")
   private AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project;

   public PluginAction(AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project) {
      this.project = project;
   }
/*
   public PluginAction(String matrixTable) {
      theMatrix = matrixTable;
   }
*/

   public void setTheMatrix(TreeMap<String,TestCaseMatrix> matrix) {
      this.matrix = matrix;
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

   /*
   public ArrayList<TestCaseMatrix> getTheMatrix() {
      return theMatrix;
   }
   */

   // Map to array of nulls and

   public String[][] getSpreads() {
      int diff = buildNumbers.last()-buildNumbers.first(); // To be able to find spread size
      String[][] ss = new String[matrix.size()][diff+1]; // +1 since it is a range
      // populate-it
      return ss;
   }

   public String getSomething() {
      return "SOMETHING";
   }

   public void setBuildNumbers(TreeSet<Integer> buildNumbers) {
      this.buildNumbers = buildNumbers;
   }

   public Object[] getBuildNumbers() {
      return buildNumbers.toArray();
   }
}
