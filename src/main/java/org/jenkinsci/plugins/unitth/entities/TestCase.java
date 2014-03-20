package org.jenkinsci.plugins.unitth.entities;

public class TestCase {

   private boolean isSkipped = false;
   private double duration = 0.0;

   public void setDuration(String duration) {
      this.duration = Double.parseDouble(duration);
   }

   public double getDuration() {
      return duration;
   }
}
