package org.jenkinsci.plugins.unitth;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by andreasnyberg on 17/06/14.
 */
public class Utils {
   public static String readFile(InputStream file) throws Exception {
      BufferedReader reader = new BufferedReader(new InputStreamReader(file));
      String line;
      StringBuffer sb = new StringBuffer();
      String ls = System.getProperty("line.separator");

      while( ( line = reader.readLine() ) != null ) {
         sb.append(line);
         sb.append(ls);
      }
      return sb.toString();
   }
}
