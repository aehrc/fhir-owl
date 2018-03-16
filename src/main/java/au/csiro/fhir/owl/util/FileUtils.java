/**
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to 
 * license terms and conditions.
 */

package au.csiro.fhir.owl.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * File utility methods.
 * 
 * @author Alejandro Metke Jimenez
 *
 */
public class FileUtils {
  
  /**
   * Deletes a file or recursively a folder and its contents. Returns a list of files that
   * could not be deleted.
   *
   * @param file The file to delete.
   * @return A list of files that could not be deleted.
   * @throws IOException If something goes wrong.
   */
  public static List<File> delete(File file) throws IOException {

    if (null == file) {
      return Collections.emptyList(); 
    }

    List<File> unableToDelete = new ArrayList<>();

    if (file.isDirectory()) {
      if (file.list().length == 0) {
        if (!file.delete()) {
          unableToDelete.add(file);
        }
      } else {
        String[] files = file.list();

        for (String temp : files) {
          File fileDelete = new File(file, temp);
          unableToDelete.addAll(delete(fileDelete));
        }
        if (file.list().length == 0) {
          if (!file.delete()) {
            unableToDelete.add(file);
          }
        }
      }
    } else {
      if (!file.delete()) {
        unableToDelete.add(file);
      }
    }
    return unableToDelete;
  }
}
