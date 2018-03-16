/**
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to 
 * license terms and conditions.
 */

package au.csiro.fhir.owl;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.base.Optional;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.dstu3.model.BooleanType;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.CodeSystem.CodeSystemHierarchyMeaning;
import org.hl7.fhir.dstu3.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.dstu3.model.CodeSystem.ConceptDefinitionDesignationComponent;
import org.hl7.fhir.dstu3.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.dstu3.model.CodeSystem.FilterOperator;
import org.hl7.fhir.dstu3.model.CodeSystem.PropertyComponent;
import org.hl7.fhir.dstu3.model.CodeSystem.PropertyType;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Enumerations.PublicationStatus;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Main service.
 * 
 * @author Alejandro Metke Jimenez
 *
 */
@Service
public class FhirOwlService {
  
  private static final Log log = LogFactory.getLog(FhirOwlService.class);
  
  private static final String ONTOLOGY_UNAVAILABLE = "ONTOLOGY_UNAVAILABLE";
  
  @Value("#{'${ontoserver.owl.publisher}'.split(',')}")
  private List<String> publisherElems;

  @Value("#{'${ontoserver.owl.description}'.split(',')}")
  private List<String> descriptionElems;
  
  @Autowired
  private FhirContext ctx;
  
  
  /**
   * Transforms an OWL file into a bundle of FHIR code systems.
   * 
   * @param input The input OWL file.
   * @param output The output FHIR bundle.
   * @throws IOException If there is an I/O issue.
   * @throws OWLOntologyCreationException If there is a problem creating the ontology.
   */
  public void transform(File input, File output) throws IOException, OWLOntologyCreationException {
    log.info("Loading ontology from file " + input.getAbsolutePath());
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    final OWLOntology ont = manager.loadOntologyFromOntologyDocument(input);
    
    log.info("Creating code systems");
    final List<CodeSystem> codeSystems = createCodeSystems(ont, manager);
    final Bundle b = new Bundle();
    b.setType(BundleType.TRANSACTION);
    for (CodeSystem cs : codeSystems) {
      int numConcepts = cs.getConcept().size();
      if (numConcepts > 0) {
        log.info("Adding code system " + cs.getName() + " [" + numConcepts + "]");
        BundleEntryComponent bec = b.addEntry();
        bec.setResource(cs);
      } else {
        log.info("Excluding code system " + cs.getName() + " because it has no codes");
      }
    }
    
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {
      log.info("Writing bundle to file: " + output.getAbsolutePath());
      ctx.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(b, bw);
      log.info("Done!");
    }
  }
  
  /**
   * Looks for dc:source annotations and replaces derived ontologies with the source ones.
   * 
   * @param onts The ontologies.
   * @return The new ontologies, excluding derived ones and including the corresponding sources.
   * @throws OWLOntologyCreationException If something goes wrong creating an ontology.
   */
  private Set<OWLOntology> replaceWithSources(Set<OWLOntology> onts, OWLOntologyManager manager) {
    final Set<OWLOntology> res = new HashSet<>();
    
    final Set<OWLOntology> toProcess = new HashSet<>(); 
    for (OWLOntology ont : onts) {
      final IRI ontIri = getOntologyIri(ont);
      final String fullIri = ontIri != null ? ontIri.toString() : "ANONYMOUS";
      final IRI sourceIri = getSource(ont);
      if (sourceIri != null) {
        log.info("Ontology " + fullIri + " is derived from " + sourceIri + ". Loading.");
        // We assume there is only one level of derivation
        try {
          OWLOntology o = manager.loadOntology(sourceIri);
          log.info("Excluding " + ontIri + " and adding imports closure of " + sourceIri);
          toProcess.addAll(manager.getImportsClosure(o));
        } catch (Throwable t) {
          log.error("Unable to load defining ontology for " + fullIri + " so will keep "
              + "derived version");
          res.add(ont);
        }
      } else {
        res.add(ont);
      }
    }
    
    if (!toProcess.isEmpty()) {
      res.addAll(replaceWithSources(toProcess, manager));
    }
    
    return res;
  }

  private String getOntologiesNames(Set<OWLOntology> onts) {
    StringBuilder sb = new StringBuilder();
    for (OWLOntology ont : onts) {
      sb.append(getOntologyName(ont));
      sb.append("\n");
    }
    return sb.toString();
  }
  
  /**
   * Creates code systems for an ontology and its imports.
   * 
   * @param ont The ontology to transform.
   * @param manager The manager for the ontology.
   * @return A list of generated code systems.
   * @throws OWLOntologyCreationException If something goes wrong creating the ontologies.
   */
  private List<CodeSystem> createCodeSystems(OWLOntology rootOnt, OWLOntologyManager manager) 
      throws OWLOntologyCreationException {
    
    // 1. Get IRI -> system map - might contain null values
    Set<OWLOntology> closure = new HashSet<>();
    final Map<IRI, String> iriSystemMap = getIriSystemMap(rootOnt, manager, closure);
    
    // 2. For each ontology classify and create a code system
    final List<CodeSystem> res = new ArrayList<>();
    for (OWLOntology ont : closure) {
      // Now, classify this ontology
      log.info("Classifying ontology " + getOntologyName(ont));
      final OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
      final OWLReasoner reasoner = reasonerFactory.createReasoner(ont);
      reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
      
      log.info("Creating code system for ontology " + getOntologyName(ont));
      try {
        res.add(createCodeSystem(ont, manager.getOWLDataFactory(), reasoner, iriSystemMap));
      } catch (NoIdException e) {
        log.warn("Could not create a Code System for ontology " + getOntologyName(ont) 
            + " because it has no IRI.");
      }
    }

    return res;
  }
  
  /**
   * Iterates over all the concepts in the closure of an ontology an attempts to determine
   * the system for each one.
   * 
   * @param rootOnt The root ontology.
   * @param manager The ontology manager.
   * @param closure Used to return the ontology closure. Note that this might not be the same
   *     as the ontologies in the manager because some derived ontologies (e.g., subsets used
   *     for performance reasons) might be replaced with the source ontology.
   * @return A map of IRIs to systems.
   * 
   * @throws OWLOntologyCreationException If something goes wrong creating the ontologies.
   */
  private Map<IRI, String> getIriSystemMap(OWLOntology rootOnt, OWLOntologyManager manager, 
      Set<OWLOntology> closure) {
    log.info("Getting IRI -> system map for ontology " + getOntologyName(rootOnt));
    
    // 1. Get the set of all ontologies in the imports closure
    closure.addAll(manager.getImportsClosure(rootOnt));
    log.info("Found the following ontologies in the closure: \n" + getOntologiesNames(closure));
    
    // 2. Replace any derived ontologies with the full versions (following dc:source)
    closure = replaceWithSources(closure, manager);
    log.info("Replace derived ontologies: \n" + getOntologiesNames(closure));
    
    // 3. For each ontology, check IRI, create OBO-like prefix and build prefix -> system map
    log.info("Building prefix -> system map");
    final Map<String, String> prefixSystemMap = new HashMap<>();
    for (OWLOntology ont : closure) {
      final IRI iri = getOntologyIri(ont);
      if (iri != null) {
        final String shortForm = iri.getShortForm();
        if (shortForm.endsWith(".owl")) {
          log.info("Found OBO-like IRI: " + shortForm);
          prefixSystemMap.put(shortForm.substring(0, shortForm.length() - 4).toLowerCase(), 
              iri.toString());
        } else {
          log.info("IRI is not OBO-like:" + shortForm);
        }
      } else {
        log.warn("Ontology " + getOntologyName(ont) + " has no IRI.");
      }
    }
    
    // 4. Create a IRI -> system map for all classes in the closure
    log.info("Building IRI -> system map");
    final Map<IRI, String> iriSystemMap = new HashMap<>();
    for (OWLOntology ont : closure) {
      final IRI ontIri = getOntologyIri(ont);
      final String ontologyIri = ontIri != null ? ontIri.toString() : "ANONYMOUS";
      for (OWLClass owlClass : ont.getClassesInSignature(Imports.EXCLUDED)) {
        final IRI classIri = owlClass.getIRI();
        String system = getSystem(owlClass, ontologyIri, prefixSystemMap);
        iriSystemMap.put(classIri, system);
      }
    }
    
    // 5. Try to find any ontologies that haven't been imported
    log.info("Finding ontologies that haven't been imported.");
    IRI iri = findNullValue(iriSystemMap);
    while (iri != null) {
      
      log.info("Trying to find ontology for class " + iri.toString());
      final List<String> possibleIris = findOntologyIri(iri);
      
      log.info("Possible IRIs are: " + possibleIris);
      
      // We need a new manager here
      Map<IRI, String> newIriSystemMap = null;
      for (String possibleIri : possibleIris) {
        OWLOntologyManager om = OWLManager.createOWLOntologyManager();
        try {
          final OWLOntology ont = om.loadOntology(IRI.create(possibleIri));
          
          // Check if concept is in fact in this ontology (it is unlikely but it might not be)
          if (ont.containsClassInSignature(iri)) {
            log.info("Ontology was loaded successfully and contains the class.");
            final Set<OWLOntology> cs = new HashSet<>();
            newIriSystemMap = getIriSystemMap(ont, om, cs);
            break;
          } else {
            log.info("Ontology loaded successfully but did not contain class.");
          }
        } catch (OWLOntologyCreationException e) {
          log.warn("Attempted loading ontology " + possibleIri + " but failed: " 
              + e.getLocalizedMessage());
        }
      }
      
      if (newIriSystemMap == null) {
        // Replace null value with constant if unable to find ontology
        log.info("Unable to find ontology for class " + iri.toString());
        iriSystemMap.put(iri, ONTOLOGY_UNAVAILABLE);
      } else {
        // Carefully merge the maps, i.e., do not copy entries with value ONTOLOGY_UNAVAILABLE
        for (IRI key : newIriSystemMap.keySet()) {
          final String value = newIriSystemMap.get(key);
          if (!ONTOLOGY_UNAVAILABLE.equals(value)) {
            iriSystemMap.put(key, value);
          }
        }
      }
      
      iri = findNullValue(iriSystemMap);
    }
    
    return iriSystemMap;
  }
  
  /**
   * Tries to derive the ontology IRI of a class. Does the following:
   * 
   *  <ol>
   *    <li>If the class IRI has a hash symbol then the prefix is used as the IRI.</li>
   *    <li>If the class IRI uses the OBO conventions then the corresponding IRI is derived. For 
   *        example, the class IRI http://purl.obolibrary.org/obo/BFO_0000002 would produce the 
   *        ontology IRI http://purl.obolibrary.org/obo/bfo.owl.</li>
   *  </ol>
   * 
   * @param iri The class IRI.
   * @return The ontology IRI or null if none can be derived.
   */
  private List<String> findOntologyIri(IRI iri) {
    final List<String> res = new ArrayList<>();
    
    final String namespace = iri.getNamespace();
    final String shortForm = iri.getShortForm();
    
    if (namespace.endsWith("#")) {
      res.add(namespace.substring(0, namespace.length() - 1));
    }
    
    // See if fragment is OBO-like
    if (matchesOboConventions(shortForm)) {
      final String prefix = getOboPrefix(shortForm);
      // Build ontology IRI using heuristic based on OBO naming conventions,
      // e.g., http://purl.obolibrary.org/obo/IAO_0000115 -> 
      // http://purl.obolibrary.org/obo/iao.owl
      res.add(namespace + prefix.toLowerCase() + ".owl");
    }
    
    return res;
  }
  
  private IRI findNullValue(Map<IRI, String> map) {
    for (IRI key : map.keySet()) {
      if (map.get(key) == null) {
        return key;
      }
    }
    return null;
  }

  /**
   * Creates a code system from an ontology.
   * 
   * @param ont The ontology.
   * @param factory The OWL factory.
   * @param reasoner The OWL reasoner.
   * @param iriSystemMap  
   * 
   * @return The code system.
   */
  private CodeSystem createCodeSystem(OWLOntology ont, final OWLDataFactory factory, 
      OWLReasoner reasoner, Map<IRI, String> iriSystemMap) {
    // Extract ontology information
    final String codeSystemUrl;
    final String codeSystemVersion;

    final OWLOntologyID ontId = ont.getOntologyID();
    final Optional<IRI> iri = ontId.getOntologyIRI();
    final Optional<IRI> version = ontId.getVersionIRI();

    if (iri.isPresent()) {
      codeSystemUrl = iri.get().toString();
    } else {
      throw new NoIdException();
    }

    if (version.isPresent()) {
      codeSystemVersion = version.get().toString();
    } else {
      codeSystemVersion = "NA";
    }
    
    String codeSystemName = codeSystemUrl;
    String publisher = null;
    String description = null;
    
    // Index annotations
    final Map<String, List<String>> annMap = new HashMap<>();

    for (OWLAnnotation ann : ont.getAnnotations()) {
      final String prop = ann.getProperty().getIRI().toString();
      Optional<OWLLiteral> val = ann.getValue().asLiteral();
      if (val.isPresent()) {
        List<String> vals = annMap.get(prop);
        if (vals == null) {
          vals = new ArrayList<>();
          annMap.put(prop, vals);
        }
        vals.add(val.get().getLiteral());
      }
    }

    if (annMap.containsKey("http://www.w3.org/2000/01/rdf-schema#label")) {
      // This is the name of the ontology
      codeSystemName = annMap.get("http://www.w3.org/2000/01/rdf-schema#label").get(0);
    }

    for (String publisherElem : publisherElems) {
      if (annMap.containsKey(publisherElem)) {
        // This is the publisher of the ontology
        // Get first publisher - FHIR spec only supports one
        publisher = annMap.get(publisherElem).get(0); 
        break;
      }
    }

    for (String descriptionElem : descriptionElems) {
      if (annMap.containsKey(descriptionElem)) {
        // This is the description of the ontology
        description = annMap.get(descriptionElem).get(0);
        break;
      }
    }

    // Populate basic code system info
    final CodeSystem cs = new CodeSystem();
    cs.setUrl(codeSystemUrl);
    cs.setVersion(codeSystemVersion);
    cs.setName(codeSystemName);
    if (publisher != null) { 
      cs.setPublisher(publisher);
    }
    if (description != null) {
      cs.setDescription(description);
    }
    cs.setStatus(PublicationStatus.ACTIVE);
    // Create default value set
    cs.setValueSet(codeSystemUrl);
    cs.setHierarchyMeaning(CodeSystemHierarchyMeaning.ISA);

    PropertyComponent parentProp = cs.addProperty();
    parentProp.setCode("parent");
    parentProp.setType(PropertyType.CODE);
    parentProp.setDescription("Parent codes.");

    PropertyComponent rootProp = cs.addProperty();
    rootProp.setCode("root");
    rootProp.setType(PropertyType.BOOLEAN);
    rootProp.setDescription("Indicates if this concept is a root concept (i.e. Thing is "
        + "equivalent or a direct parent)");

    PropertyComponent depProp = cs.addProperty();
    depProp.setCode("deprecated");
    depProp.setType(PropertyType.BOOLEAN);
    depProp.setDescription("Indicates if this concept is deprecated.");
    
    // This property indicates if a concept is meant to represent a root, i.e. it's child of Thing.
    cs.addFilter().setCode("root").addOperator(FilterOperator.EQUAL).setValue("True or false.");
    cs.addFilter().setCode("deprecated").addOperator(FilterOperator.EQUAL)
      .setValue("True or false.");

    for (OWLClass owlClass : ont.getClassesInSignature(Imports.EXCLUDED)) {
      processClass(owlClass, cs, ont, reasoner, iriSystemMap);
    }

    return cs;
  }

  private boolean addHierarchyFields(final OWLReasoner reasoner, OWLClass owlClass, 
      ConceptDefinitionComponent cdc, boolean isRoot, Map<IRI, String> iriSystemMap) {
    // Add hierarchy-related fields
    for (Node<OWLClass> parent : reasoner.getSuperClasses(owlClass, true)) {
      for (OWLClass ent : parent.getEntities()) {
        if (ent.isOWLNothing()) { 
          continue;
        }
        final IRI iri = ent.getIRI();
        final String system = iriSystemMap.get(iri);
        if (system != null) {
          final ConceptPropertyComponent prop = cdc.addProperty();
          prop.setCode("parent");
          final String code = iri.getShortForm();
          prop.setValue(new Coding().setSystem(system).setCode(code));
        }
      }
    }

    // Check if this concept is equivalent to Thing - in this case it is a root
    for (OWLClass eq : reasoner.getEquivalentClasses(owlClass)) {
      if (eq.isOWLThing()) {
        isRoot = true;
        break;
      }
    }
    return isRoot;
  }
  
  /**
   * Determines if an OWL class is deprecated based on annotations.
   * 
   * @param owlClass The OWL class.
   * @param ont The ontology it belongs to.
   * @return
   */
  private boolean isDeprecated(OWLClass owlClass, OWLOntology ont) {
    boolean isDeprecated = false;
    for (OWLAnnotation ann : EntitySearcher.getAnnotationObjects(owlClass, ont)) {
      OWLAnnotationProperty prop = ann.getProperty();
      if (prop != null && prop.getIRI().getShortForm().equals("deprecated")) {
        OWLAnnotationValue val = ann.getValue();
        if (val != null) {
          Optional<OWLLiteral> lit = val.asLiteral();
          if (lit.isPresent()) {
            final OWLLiteral l = lit.get();
            if (l.isBoolean()) {
              isDeprecated = l.parseBoolean();
            } else {
              log.warn("Found deprecated attribute but it is not boolean: " + l.toString());
            }
          }
        }
      }
    }
    return isDeprecated;
  }
  
  private String getPreferedTerm(OWLClass owlClass, OWLOntology ont) {
    final OWLDataFactory factory = ont.getOWLOntologyManager().getOWLDataFactory();
    for (OWLAnnotation a : EntitySearcher.getAnnotations(owlClass, ont, 
        factory.getRDFSLabel())) {
      OWLAnnotationValue val = a.getValue();
      if (val instanceof OWLLiteral) {
        return ((OWLLiteral) val).getLiteral();
      }
    }
    
    return null;
  }
  
  private Set<String> getSynonyms(OWLClass owlClass, OWLOntology ont, String preferredTerm) {
    final OWLDataFactory factory = ont.getOWLOntologyManager().getOWLDataFactory();
    
    final Set<String> synonyms = new HashSet<>();
    for (OWLAnnotation a : EntitySearcher.getAnnotations(owlClass, ont, 
        factory.getRDFSLabel())) {
      OWLAnnotationValue val = a.getValue();
      if (val instanceof OWLLiteral) {
        final String label = ((OWLLiteral) val).getLiteral();
        synonyms.add(label);
      }
    }
    
    for (OWLAnnotation ann : EntitySearcher.getAnnotationObjects(owlClass, ont)) {
      OWLAnnotationProperty prop = ann.getProperty();
      if (prop != null && prop.getIRI().getShortForm().equals("hasExactSynonym")) {
        // This is an oboInOwl extension
        OWLAnnotationValue val = ann.getValue();
        if (val != null) {
          Optional<OWLLiteral> lit = val.asLiteral();
          if (lit.isPresent()) {
            String label = lit.get().getLiteral();
            synonyms.add(label);
          }
        }
      }
    }
    
    synonyms.remove(preferredTerm);
    return synonyms;
  }

  /**
   * Returns the name of an ontology. Uses an rdfs:label or the ontology's IRI if one if not 
   * present. If the ontology has no IRI then it returns null.
   * 
   * @param ont The ontology.
   * @return The name of the ontology or null if it has no name.
   */
  private String getOntologyName(OWLOntology ont) {
    for (OWLAnnotation ann : ont.getAnnotations()) {
      final String prop = ann.getProperty().getIRI().toString();
      Optional<OWLLiteral> val = ann.getValue().asLiteral();
      if (val.isPresent() && "http://www.w3.org/2000/01/rdf-schema#label".equals(prop)) {
        return val.get().getLiteral();
      }
    }
    
    IRI iri = getOntologyIri(ont);
    
    return iri != null ? iri.toString() : null;
  }
  
  /**
   * If this ontology is derived from another ontology then it returns the source IRI.
   * Otherwise returns null.
   * 
   * @param ont The ontology.
   * @return The IRI of the source ontology or null if this ontology is not derived from
   *     another ontology.
   */
  private IRI getSource(OWLOntology ont) {
    for (OWLAnnotation ann : ont.getAnnotations()) {
      final String prop = ann.getProperty().getIRI().toString();
      Optional<IRI> val = ann.getValue().asIRI();
      if (val.isPresent() && "http://purl.org/dc/elements/1.1/source".equals(prop)) {
        return val.get();
      }
    }
    return null;
  }
  
  private IRI getOntologyIri(OWLOntology ont) {
    OWLOntologyID ontId = ont.getOntologyID();
    Optional<IRI> iri = ontId.getOntologyIRI();

    if (iri.isPresent()) {
      return iri.get();
    } else {
      return null;
    }
  }
  
  /**
   * Attempts to get the system for an OWL class. Assumes that the class uses the ontology's IRI as
   * prefix or that is uses the OBO naming conventions. Returns the system or null if unable to
   * find it.
   * 
   * @param owlClass The OWL class.
   * @param ontologyIri The IRI of the ontology where the class came from.
   * @param prefixSystemMap A map of OBO prefixes to systems.
   * @return The system or null if unable to find it.
   */
  private String getSystem(OWLClass owlClass, String ontologyIri, 
      Map<String, String> prefixSystemMap) {
    final IRI iri = owlClass.getIRI();
    final String fullIri = iri.toString();
    // Check if class IRI has ontology IRI as prefix
    if (fullIri.startsWith(ontologyIri)) {
      return ontologyIri;
    }
    
    // Check short name to see if it matches OBO conventions
    final String shortForm = iri.getShortForm();
    if (matchesOboConventions(shortForm)) {
      final String prefix = getOboPrefix(shortForm);
      return prefixSystemMap.get(prefix.toLowerCase());
    }
    return null;
  }
  
  private boolean matchesOboConventions(String shortForm) {
    return shortForm.matches("^[a-zA-Z]*[_][0-9]*");
  }
  
  private String getOboPrefix(String shortForm) {
    return shortForm.split("[_]")[0];
  }
  
  /**
   * Adds this concept to the code system.
   * 
   * @param owlClass The concept to add.
   * @param cs The code system where the concept will be added.
   * @param ont The ontology. Needed to search for the labels of the concept.
   * @param reasoner The reasoner. Required to get the parents of a concept.
   * @param iriSystemMap 
   * 
   */
  private void processClass(OWLClass owlClass, CodeSystem cs, OWLOntology ont, 
      OWLReasoner reasoner, Map<IRI, String> iriSystemMap) {
    final IRI iri = owlClass.getIRI();
    final String code = iri.getShortForm();
    final String classSystem = iriSystemMap.get(iri);
    
    final String codeSytemUrl = cs.getUrl();
    
    if (classSystem != null && classSystem.equals(codeSytemUrl)) {
      // This class is defined in this ontology so create code
      final ConceptDefinitionComponent cdc = new ConceptDefinitionComponent();
      cdc.setCode(code);

      final boolean isDeprecated = isDeprecated(owlClass, ont);

      // Special case: OWL:Thing
      if ("http://www.w3.org/2002/07/owl#Thing".equals(cdc.getCode())) {
        cdc.setDisplay("Thing");
      }

      boolean isRoot = false;
      isRoot = addHierarchyFields(reasoner, owlClass, cdc, isRoot, iriSystemMap);

      ConceptPropertyComponent prop = cdc.addProperty();
      prop.setCode("root");
      prop.setValue(new BooleanType(isRoot));

      prop = cdc.addProperty();
      prop.setCode("deprecated");
      prop.setValue(new BooleanType(isDeprecated));
      
      String preferredTerm = getPreferedTerm(owlClass, ont);
      final Set<String> synonyms = getSynonyms(owlClass, ont, preferredTerm);
      
      if (preferredTerm == null && synonyms.isEmpty()) {
        // No labels so display is just the code
        cdc.setDisplay(code);
      } else if (preferredTerm == null) {
        // No prefererd term but there are synonyms so pick any one as the display
        preferredTerm = synonyms.iterator().next();
        synonyms.remove(preferredTerm);
        
        cdc.setDisplay(preferredTerm);
        addSynonyms(synonyms, cdc);
      } else {
        cdc.setDisplay(preferredTerm);
        addSynonyms(synonyms, cdc);
      }
      
      cs.addConcept(cdc);
    }
  }
  
  private void addSynonyms(Set<String> synonyms, ConceptDefinitionComponent cdc) {
    for (String syn : synonyms) {
      // This is a synonym - but we don't know the language
      ConceptDefinitionDesignationComponent cddc = cdc.addDesignation();
      cddc.setValue(syn);
      cddc.setUse(new Coding("http://snomed.info/sct", "900000000000013009", 
              "Synonym (core metadata concept)"));
    }
  }
  
}
