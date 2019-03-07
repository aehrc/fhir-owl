/**
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to 
 * license terms and conditions.
 */

package au.csiro.fhir.owl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;

/**
 * Parent properties class with methods to return OWL annotation properties.
 * 
 * @author Alejandro Metke
 *
 */
public abstract class OwlProperties {
  
  private static final Log log = LogFactory.getLog(OwlProperties.class);
  
  protected static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
  
  protected static final String RDFS_COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment";
  
  protected static final String DC_TITLE = "http://purl.org/dc/elements/1.1/title";
  
  protected static final String DC_SUBJECT = "http://purl.org/dc/elements/1.1/subject";
  
  protected static final String DC_PUBLISHER = "http://purl.org/dc/elements/1.1/publisher";
  
  protected List<OWLAnnotationProperty> loadProps(OWLDataFactory factory, List<String> args, 
      List<String> defaults, OWLAnnotationProperty lastResort) {
    final List<OWLAnnotationProperty> res = new ArrayList<>();
    if (args != null && !args.isEmpty()) {
      for (String prop : args) {
        res.add(factory.getOWLAnnotationProperty(IRI.create(prop)));
        log.info("Loading synonyms from OWL annotation property " + prop);
      }      
    } else if (defaults != null && !defaults.isEmpty()) {
      for (String prop : defaults) {
        res.add(factory.getOWLAnnotationProperty(IRI.create(prop)));
        log.info("Loading synonyms from OWL annotation property " + prop);
      }  
    } else {
      if (lastResort != null) {
        res.add(lastResort);
        log.info("Loading synonyms from " + lastResort.toStringID());
      }
    }
    return res;
  }
  
  protected OWLAnnotationProperty loadProp(OWLDataFactory factory, String arg, 
      String defaultProp, OWLAnnotationProperty lastResort) {
    OWLAnnotationProperty res = null;
    if (arg != null) {
      res = factory.getOWLAnnotationProperty(IRI.create(arg));
      log.info("Loading value from OWL annotation property " + arg);
    } else if (defaultProp != null) {
      res = factory.getOWLAnnotationProperty(IRI.create(defaultProp));
      log.info("Loading value from OWL annotation property " + defaultProp); 
    } else {
      if (lastResort != null) {
        res = lastResort;
        log.info("Loading value from " + lastResort.toStringID());
      }
    }
    return res;
  }
  
}
