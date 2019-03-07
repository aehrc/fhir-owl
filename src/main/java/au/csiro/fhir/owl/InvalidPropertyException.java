/**
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to 
 * license terms and conditions.
 */

package au.csiro.fhir.owl;

/**
 * Thrown when there is a problem parsing a property from the command line.
 * 
 * @author Alejandro Metke
 *
 */
public class InvalidPropertyException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidPropertyException() {
    super();
  }

  public InvalidPropertyException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public InvalidPropertyException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidPropertyException(String message) {
    super(message);
  }

  public InvalidPropertyException(Throwable cause) {
    super(cause);
  }

}
