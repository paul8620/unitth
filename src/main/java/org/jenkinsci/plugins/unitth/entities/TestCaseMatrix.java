package org.jenkinsci.plugins.unitth.entities;

import java.util.TreeMap;

public class TestCaseMatrix {
   private String name = null;
   private String className = null;
   private String packageName = null;
   private TreeMap<Integer, TestCase> spread = null;

   private int runs = 0;
   private int noPassed = 0;
   private int noFailed = 0;
   private int noSkipped = 0;
   private double totalDuration = 0.0;

   public TestCaseMatrix(TestCase tc, int i) {
      name = tc.getName();
      className = tc.getClassName();
      packageName = tc.getPackageName();
      runs = 1;
      if (tc.getVerdict() == TestCaseVerdict.PASSED) {
         noPassed = 1;
      } else if (tc.getVerdict() == TestCaseVerdict.FAILED) {
         noFailed = 1;
      } else if (tc.getVerdict() == TestCaseVerdict.SKIPPED) {
         noSkipped = 1;
      }
      totalDuration = tc.getDuration();
      spread = new TreeMap<Integer, TestCase>();
      spread.put(i, tc);
   }

   public void increment(TestCase tc, int i) {
      runs++;
      if (tc.getVerdict() == TestCaseVerdict.PASSED) {
         noPassed++;
      } else if (tc.getVerdict() == TestCaseVerdict.FAILED) {
         noFailed++;
      } else if (tc.getVerdict() == TestCaseVerdict.SKIPPED) {
         noSkipped++;
      }
      totalDuration += tc.getDuration();
      spread.put(i, tc);
   }

   public TreeMap<Integer, TestCase> getSpread() {
      return spread;
   }

   public TestCase getSpreadAt(int i) {
      return spread.get(i);
   }

   public boolean hasFailed() {
      if (noFailed > 0) {
         return true;
      }
      return false;
   }

   public String getQName() {
      return packageName+"."+className+"."+name;
   }

   public int getNoRuns() {
      return runs;
   }

   public int getNoPassed() {
      return noPassed;
   }

   public int getNoFailed() {
      return noFailed;
   }

   public int getNoSkipped() {
      return noSkipped;
   }
}
