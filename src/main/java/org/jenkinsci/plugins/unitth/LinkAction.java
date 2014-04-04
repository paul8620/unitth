package org.jenkinsci.plugins.unitth;

import hudson.model.Action;
import org.kohsuke.stapler.DataBoundConstructor;

public class LinkAction implements Action {
   private String url;
   private String text = "Test matrix";
   private String icon = "graph.gif";

   @DataBoundConstructor
   public LinkAction(String urlName) {
      this.url = urlName;
   }

   public String getUrlName() { return url; }
   public String getDisplayName() { return text; }
   public String getIconFileName() { return icon; }
}
