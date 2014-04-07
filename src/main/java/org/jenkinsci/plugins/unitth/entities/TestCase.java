package org.jenkinsci.plugins.unitth.entities;

public class TestCase {

   private TestCaseVerdict verdict = TestCaseVerdict.PASSED; // We only know abt fail or skipped. All others are passed.
   private double duration = 0.0;
   private String name = null;
   private String className = null;
   private String packageName = null;

   public void setDuration(String duration) {
      this.duration = Double.parseDouble(duration);
   }

   public double getDuration() {
      return duration;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public void setVerdict(TestCaseVerdict verdict) {
      this.verdict = verdict;
   }

   public TestCaseVerdict getVerdict() {
      return verdict;
   }

   public void setClassName(String className) {
      this.className = className;
   }

   public String getQualifiedName() {
      return packageName+"."+className+"."+name;
   }

   public void setPackageName(String packageName) {
      this.packageName = packageName;
   }

   public String getPackageName() {
      return packageName;
   }

   public String getClassName() {
      return className;
   }
}
