/**
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to 
 * license terms and conditions.
 */

package au.csiro.fhir.owl;

/**
 * Thrown when an input OLW ontology does not have an identifier.
 * 
 * @author Alejandro Metke Jimenez
 *
 */
public class NoIdException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NoIdException() {
    super();
  }

  public NoIdException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public NoIdException(String message, Throwable cause) {
    super(message, cause);
  }

  public NoIdException(String message) {
    super(message);
  }

  public NoIdException(Throwable cause) {
    super(cause);
  }
  
}
