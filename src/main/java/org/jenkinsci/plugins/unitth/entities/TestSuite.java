package org.jenkinsci.plugins.unitth.entities;

import java.util.HashMap;

public class TestSuite {

   private String file = null;
   private String name = null;
   private double duration = 0.0;

   private HashMap<String, TestCase> testCases = new HashMap<String, TestCase>();

   public void setFile(String file) {
      this.file = file;
   }

   public String getFile() {
      return file;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getName() {
      return name;
   }

   public void setDuration(String duration) {
      this.duration = Double.parseDouble(duration);
   }

   public double getDuration() {
      return duration;
   }

   public int getNoTests() {
      return testCases.size();
   }

   public void addTestCase(TestCase tc) {
      testCases.put(tc.getName(), tc);
   }

   public int getNoSkippedTests() {
      int noSkipped = 0;
      for (TestCase tc : testCases.values()) {
         if (tc.getVerdict()==TestCaseVerdict.SKIPPED) {
            noSkipped++;
         }
      }
      return noSkipped;
   }

   public HashMap<String, TestCase> getTestCases() {
      return testCases;
   }
}
