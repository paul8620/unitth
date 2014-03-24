package org.jenkinsci.plugins.unitth.entities;

import java.util.HashMap;

public class TestReport {

   private double executionTime = 0.0;

   private HashMap<String, TestSuite> testSuites = new HashMap<String, TestSuite>();

   public int getNoTests() {
      int tests = 0;
      for (TestSuite ts :testSuites.values()) {
         tests += ts.getNoTests();
      }
      return tests;
   }

   public int getNoSuites() {
      return testSuites.size();
   }

   public void addSuite(TestSuite suite) {
      testSuites.put(suite.getName(), suite);
   }

   public TestSuite getSuiteByName(String name) {
      return testSuites.get(name);
   }
}
