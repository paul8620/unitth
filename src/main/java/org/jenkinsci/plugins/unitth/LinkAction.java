package org.jenkinsci.plugins.unitth;

import hudson.FilePath;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Run;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

public class LinkAction implements Action {
   private String url;
   private String text = "Test matrix";
   private String icon = "graph.gif";
   private AbstractProject project;

   @DataBoundConstructor
   public LinkAction(AbstractProject project, String urlName) {
      this.project = project;
      this.url = urlName;
   }

   public String getUrlName() { return url; }
   public String getDisplayName() { return text; }
   public String getIconFileName() { return icon; }

   public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
      DirectoryBrowserSupport dbs = new DirectoryBrowserSupport(this, new FilePath(this.dir()), "Test matrix", "graph.gif", false);
      dbs.setIndexFileName("index.html");
      dbs.generateResponse(req, rsp, this);
   }

   protected File dir() {
      if (this.project instanceof AbstractProject) {
         AbstractProject abstractProject = (AbstractProject) this.project;

         Run run = abstractProject.getLastSuccessfulBuild();
         if (run != null) {
            File javadocDir = getBuildArchiveDir(run);

            if (javadocDir.exists()) {
               return javadocDir;
            }
         }
      }

      return getProjectArchiveDir(this.project);
   }

   private File getProjectArchiveDir(AbstractItem project) {
      return new File(project.getRootDir(), "thx");
   }
   /**
    * Gets the directory where the HTML report is stored for the given build.
    */
   private File getBuildArchiveDir(Run run) {
      return new File(run.getRootDir(), "thx");
   }
}
