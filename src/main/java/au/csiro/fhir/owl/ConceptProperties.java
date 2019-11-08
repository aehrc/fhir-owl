/**
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to 
 * license terms and conditions.
 */

package au.csiro.fhir.owl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;

/**
 * The configuration properties to populate the concept of a code system based on the contents of 
 * the source OWL file.
 * 
 * @author Alejandro Metke
 *
 */
public class ConceptProperties extends OwlProperties {
  
  private String code = null;
  private String display = null;
  private String definition = null;
  private List<String> designations = new ArrayList<>();
  private String stringToReplaceInCodes = null;
  private String replacementStringInCodes = null;
  private List<String> labelsToExclude = new ArrayList<>();
  
  private final List<String> defaultDesignationProps = Arrays.asList(
      new String[] { RDFS_LABEL });
  
  /**
   * Returns the annotation property that contains the concept's code.
   * 
   * @param factory
   * 
   * @return the code
   */
  public OWLAnnotationProperty getCode(OWLDataFactory factory) {
    return loadProp(factory, code, null, null);
  }
  
  /**
   * Sets the annotation property that contains the concept's code.
   * 
   * @param code the code to set
   */
  public void setCode(String code) {
    this.code = code;
  }
  
  /**
   * Returns the annotation property that contains the concept's display.
   * 
   * @param factory
   * 
   * @return the display
   */
  public OWLAnnotationProperty getDisplay(OWLDataFactory factory) {
    return loadProp(factory, display, RDFS_LABEL, null);
  }
  
  /**
   * Sets the annotation property that contains the concept's display.
   * 
   * @param display the display to set
   */
  public void setDisplay(String display) {
    this.display = display;
  }
  
  /**
   * Returns the annotation property that contains the concept's definition.
   * 
   * @param factory
   * 
   * @return the definition
   */
  public OWLAnnotationProperty getDefinition(OWLDataFactory factory) {
    return loadProp(factory, definition, null, null);
  }
  
  /**
   * Sets the annotation property that contains the concept's definition.
   * 
   * @param definition the definition to set
   */
  public void setDefinition(String definition) {
    this.definition = definition;
  }

  /**
   * Returns the annotation properties that contain the concept's synonyms.
   * 
   * @param factory
   * 
   * @return the designations
   */
  public List<OWLAnnotationProperty> getDesignations(OWLDataFactory factory) {
    return loadProps(factory, designations, defaultDesignationProps, null);
  }
  
  /**
   * Sets the annotation properties that contain the concept's synonyms.
   * 
   * @param designation A comma-separated list of annotation properties that contain the synonyms.
   */
  public void setDesignations(String designation) {
    designations.clear();
    final List<String> args = Arrays.asList(designation.split("[,]"));
    designations.addAll(args);
  }

  /**
   * Returns the string to look for in all codes to replace.
   * 
   * @return the stringToReplaceInCodes
   */
  public String getStringToReplaceInCodes() {
    return stringToReplaceInCodes;
  }

  /**
   * Sets the string to look for in all codes to replace.
   * 
   * @param stringToReplaceInCodes the codeReplaceSource to set
   */
  public void setStringToReplaceInCodes(String stringToReplaceInCodes) {
    this.stringToReplaceInCodes = stringToReplaceInCodes;
  }

  /**
   * Returns the string to replace all the matches of codeReplaceSource in all codes.
   * 
   * @return the replacementStringInCodes
   */
  public String getReplacementStringInCodes() {
    return replacementStringInCodes;
  }

  /**
   * Sets the string to replace all the matches of codeReplaceSource in all codes.
   * 
   * @param replacementStringInCodes the codeReplaceTarget to set
   */
  public void setReplacementStringInCodes(String replacementStringInCodes) {
    this.replacementStringInCodes = replacementStringInCodes;
  }
  
  /**
   * Sets the labels to exclude.
   * @param s
   */
  public void setLabelsToExclude(String s) {
    labelsToExclude.clear();
    final List<String> args = Arrays.asList(s.split("[,]"));
    labelsToExclude.addAll(args);
  }
  
  /**
   * Returns the labels to exclude.
   * 
   * @return the labelsToExclude
   */
  public List<String> getLabelsToExclude() {
    return labelsToExclude;
  }
  
}
