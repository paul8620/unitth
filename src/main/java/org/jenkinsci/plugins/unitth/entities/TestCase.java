package org.jenkinsci.plugins.unitth.entities;

public class TestCase {

   private boolean isSkipped = false;
   private double duration = 0.0;
   private String name = null;

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
}
