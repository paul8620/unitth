package org.jenkinsci.plugins.unitth;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ProminentProjectAction;
import org.jenkinsci.plugins.unitth.entities.TestCaseMatrix;
import org.kohsuke.stapler.StaplerProxy;

import java.io.Serializable;
import java.util.TreeMap;

//
// Action performed on the build page.
//
public class PluginAction implements ProminentProjectAction,
   Serializable, StaplerProxy {

   private TreeMap<String,TestCaseMatrix> testCaseMatrix;
   private String theMatrix;
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
   public void setTheMatrix(String matrixTable) {
      theMatrix = matrixTable;
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

   public String getTheMatrix() {
      return theMatrix;
   }

   public String getSomething() {
      return "SOMETHING";
   }
}
