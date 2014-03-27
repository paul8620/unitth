package org.jenkinsci.plugins.unitth;

import hudson.model.Action;
import org.jenkinsci.plugins.unitth.entities.TestCaseMatrix;
import org.kohsuke.stapler.StaplerProxy;

import java.io.Serializable;
import java.util.TreeMap;

//
// Action performed on the build page.
//
public class PluginAction implements Action,
   Serializable, StaplerProxy {

   private TreeMap<String,TestCaseMatrix> testCaseMatrix;
   private String theMatrix;
/*
   public PluginAction(TreeMap<String,TestCaseMatrix> testCaseMatrix) {
      this.testCaseMatrix = testCaseMatrix;



   }
*/

   public PluginAction(String matrixTable) {
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
}
