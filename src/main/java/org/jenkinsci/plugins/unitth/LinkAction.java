package org.jenkinsci.plugins.unitth;

import hudson.FilePath;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Hudson;
import hudson.model.Run;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
      File toReturn = null;
      if (this.project instanceof AbstractProject) {
         AbstractProject abstractProject = (AbstractProject) this.project;

         Run run = abstractProject.getLastSuccessfulBuild();
         if (run != null) {
            File javadocDir = getBuildArchiveDir(run);
            if (javadocDir.exists()) {
               toReturn = javadocDir;
            }
         }
      } else {
         toReturn = getProjectArchiveDir(this.project);
      }
      // Generate an empty report with notification for the first time the plugin is executed.
      File indexFile = new File(toReturn.getAbsolutePath()+"/index.html");
      if (!indexFile.exists()) {
         try {
            BufferedWriter out = null;
            indexFile.createNewFile();
            out = new BufferedWriter(new FileWriter(indexFile));
            try {
               out.write(Utils.readFile(this.getClass().getResourceAsStream("/org/jenkinsci/plugins/unitth/TestHistoryReporter/header.html")));
               StringBuffer sb = new StringBuffer();
               String hudsonUrl = Hudson.getInstance().getRootUrl();
               String jobUrl = hudsonUrl + project.getUrl();
               sb.append("<br><h3><a href=\""+jobUrl+"\">Back to "+project.getName()+"</a></h3>");
               sb.append("<br><br>/n");
               sb.append("<h4>You need to run the job at-least once with the test matrix plugin to get the results.</h4>/n");
               out.write(sb.toString());
               out.write(Utils.readFile(this.getClass().getResourceAsStream("/org/jenkinsci/plugins/unitth/TestHistoryReporter/footer.html")));
               out.flush();
            } catch (Exception e) {
               // Do what?
            } finally {
               out.close();
            }
         } catch(IOException ioe) {
            // Do what?
         }
      }
      return toReturn;
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
