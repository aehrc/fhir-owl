/**
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to 
 * license terms and conditions.
 */

package au.csiro.fhir.owl;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.base.Optional;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemHierarchyMeaning;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionDesignationComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r4.model.CodeSystem.FilterOperator;
import org.hl7.fhir.r4.model.CodeSystem.PropertyComponent;
import org.hl7.fhir.r4.model.CodeSystem.PropertyType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.Identifier;
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
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.SimpleIRIMapper;
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
  
  @Value("#{'${ontoserver.owl.defaults.publisher}'.split(',')}")
  private List<String> defaultPublisherProps;

  @Value("#{'${ontoserver.owl.defaults.description}'.split(',')}")
  private List<String> defaultDescriptionProps;
  
  @Value("${ontoserver.owl.defaults.preferredTerm}")
  private String defaultPreferredTermProp;
  
  @Value("#{'${ontoserver.owl.defaults.synonym}'.split(',')}")
  private List<String> defautSynonymProps;
  
  @Autowired
  private FhirContext ctx;
  
  private final Map<IRI, IRI> iriMap = new HashMap<>();
  
  @PostConstruct
  private void init() {
    log.info("Checking for IRI mappings");
    InputStream input = null;
    try {
      input = FhirContext.class.getClassLoader().getResourceAsStream("iri_mappings.txt");
      if (input == null) {
        log.info("Did not find iri_mappings.txt in classpath.");
        return;
      }
      
      final String[] lines = getLinesFromInputStream(input);
      for (String line : lines) {
        String[] parts = line.split("[,]");
        iriMap.put(IRI.create(parts[0]), IRI.create(new File(parts[1])));
      }
      
      for (IRI key : iriMap.keySet()) {
        log.info("Loaded IRI mapping " + key.toString() + " -> " + iriMap.get(key).toString());
      }
      
    } catch (Throwable t) {
      log.warn("There was a problem loading IRI mappings.", t);
    }
  }
  
  /**
   * Transforms an OWL file into a bundle of FHIR code systems.
   * 
   * @param input The input OWL file.
   * @param output The output FHIR code system.
   * @param url The URL of the code system.
   * @param identifiers Additional business identifiers of the code system.
   * @param version The business version of the code system/
   * @param name The name of the code system. If not present the system will try to extract from 
   *     the OWL file.
   * @param includeDeprecated If true includes deprecated concepts.
   * @param publisherProperties The OWL annotation properties that contain the code system 
   *     publisher.
   * @param descriptionProperties The OWL annotation properties that contain the code system 
   *     description.
   * @param codeProperty The OWL annotation property that contains the codes for each concept.
   * @param displayProperty The OWL annotation property that contains the displays for each 
   *     concept.
   * @param synonymProperties A list of OWL annotation properties that contain synonyms for each 
   *     concept.
   * 
   * @throws IOException If there is an I/O issue.
   * @throws OWLOntologyCreationException If there is a problem creating the ontology.
   */
  public void transform(
      File input, 
      File output, 
      String url, 
      List<Identifier> identifiers,
      String version,
      String name, 
      boolean includeDeprecated, 
      List<String> publisherProperties, 
      List<String> descriptionProperties, 
      String codeProperty, String displayProperty, 
      List<String> synonymProperties) 
      throws IOException, OWLOntologyCreationException {
    log.info("Creating code systems");
    final CodeSystem codeSystem = createCodeSystem(input, url, identifiers, version, name, 
        includeDeprecated, publisherProperties, descriptionProperties, codeProperty, 
        displayProperty, synonymProperties);
    
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {
      log.info("Writing code system to file: " + output.getAbsolutePath());
      ctx.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(codeSystem, bw);
      log.info("Done!");
    }
  }
  
  private String[] getLinesFromInputStream(InputStream is) throws IOException {
    final List<String> res = new ArrayList<>();
    BufferedReader br = null;
    String line;
    try {
      br = new BufferedReader(new InputStreamReader(is));
      while ((line = br.readLine()) != null) {
        res.add(line);
      }
    } finally {
      if (br != null) {
        try {
          br.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return res.toArray(new String[res.size()]);
  }
  
  private void addIriMappings(OWLOntologyManager manager) {
    for (IRI key : iriMap.keySet()) {
      manager.getIRIMappers().add(new SimpleIRIMapper(key, iriMap.get(key)));
    }
  }

  private String getOntologiesNames(Set<OWLOntology> onts) {
    StringBuilder sb = new StringBuilder();
    for (OWLOntology ont : onts) {
      sb.append(getOntologyName(ont));
      sb.append("\n");
    }
    return sb.toString();
  }
  
  private List<OWLAnnotationProperty> loadProps(OWLDataFactory factory, List<String> args, 
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
  
  private OWLAnnotationProperty loadProp(OWLDataFactory factory, String arg, 
      String defaultProp, OWLAnnotationProperty lastResort) {
    OWLAnnotationProperty res = null;
    if (arg != null) {
      res = factory.getOWLAnnotationProperty(IRI.create(arg));
      log.info("Loading synonyms from OWL annotation property " + arg);
    } else if (defaultProp != null) {
      res = factory.getOWLAnnotationProperty(IRI.create(defaultProp));
      log.info("Loading synonyms from OWL annotation property " + defaultProp); 
    } else {
      if (lastResort != null) {
        res = lastResort;
        log.info("Loading synonyms from " + lastResort.toStringID());
      }
    }
    return res;
  }
  
  private CodeSystem createCodeSystem(
      File input, 
      String url,
      List<Identifier> identifiers,
      String version,
      String name, 
      boolean includeDeprecated, 
      List<String> publisherProperties, 
      List<String> descriptionProperties, 
      String codeProperty, 
      String displayProperty, 
      List<String> synonymProperties) throws OWLOntologyCreationException {
    
    log.info("Loading ontology from file " + input.getAbsolutePath());
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    addIriMappings(manager);
    final OWLOntology rootOnt = manager.loadOntologyFromOntologyDocument(input);
    
    // Load annotation properties to populate code-system-level properties
    
    // Publisher
    final OWLDataFactory factory = manager.getOWLDataFactory();
    final List<OWLAnnotationProperty> publisherProps = loadProps(factory, publisherProperties, 
        defaultPublisherProps, null);
    
    // Description
    final List<OWLAnnotationProperty> descriptionProps = loadProps(factory, descriptionProperties, 
        defaultDescriptionProps, null);
    
    // Load annotation properties to extract concept-level attributes
    log.info("Loading annotation properties");
    // Code
    final OWLAnnotationProperty codeProp = loadProp(factory, codeProperty, null, null);
    
    // Preferred terms, a.k.a. display
    final OWLAnnotationProperty preferredTermProp = loadProp(factory, displayProperty, 
        defaultPreferredTermProp, factory.getRDFSLabel());
    
    // Synonyms
    final List<OWLAnnotationProperty> synomynProps = loadProps(factory, synonymProperties, 
        defautSynonymProps, factory.getRDFSLabel());
    
    // Get IRI -> system map - might contain null values
    Set<OWLOntology> closure = manager.getImportsClosure(rootOnt);
    final Map<IRI, String> iriSystemMap = getIriSystemMap(closure);
    
    // Extract labels for all classes
    final Map<IRI, String> iriDisplayMap = new HashMap<>();
    for (OWLClass owlClass : rootOnt.getClassesInSignature(Imports.INCLUDED)) {
      iriDisplayMap.put(owlClass.getIRI(), null);
    }
    
    for (OWLOntology ont : closure) {
      for (OWLClass oc : ont.getClassesInSignature()) {
        if (iriDisplayMap.containsKey(oc.getIRI())) {
          String pt = getPreferedTerm(oc, ont, preferredTermProp);
          if (pt != null) {
            iriDisplayMap.put(oc.getIRI(), pt);
          }
        }
      }
    }
    
    // Make sure there are no null labels
    final Set<IRI> unnamed = new HashSet<>();
    for (IRI key : iriDisplayMap.keySet()) {
      String display = iriDisplayMap.get(key);
      if (display == null) {
        log.warn("Could not find label for class " + key.toString());
        unnamed.add(key);
      }
    }
    
    for (IRI un : unnamed) {
      iriDisplayMap.put(un, un.toString());
    }
    
    // Classify root ontology
    log.info("Classifying ontology " + getOntologyName(rootOnt));
    OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
    OWLReasoner reasoner = reasonerFactory.createReasoner(rootOnt);
    reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

    // Create code system
    return createCodeSystem(rootOnt, manager.getOWLDataFactory(), reasoner, iriSystemMap, 
        iriDisplayMap, url, identifiers, version, name, includeDeprecated, publisherProps, 
        descriptionProps, codeProp, preferredTermProp, synomynProps);
  }
  
  /**
   * Creates a code system from an ontology.
   * 
   * @param ont The ontology.
   * @param factory The OWL factory.
   * @param reasoner The OWL reasoner.
   * @param iriSystemMap A map of IRIs to thier system. 
   * @param iriDisplayMap A map of IRIs to their display.
   * @param name The name of the code system.
   * @param includeDeprecated If true, deprecated OWL classes are included.
   * @param codeProp OWL annotation property that contains the code or null.
   * @param preferredTermProp OWL annotation property that contains the preferred 
   *     term.
   * @param synonymProps List of OWL annotation properties that contain synonyms.
   * 
   * @return The code system.
   */
  private CodeSystem createCodeSystem(
      OWLOntology ont, 
      final OWLDataFactory factory, 
      OWLReasoner reasoner, 
      Map<IRI, String> iriSystemMap, 
      Map<IRI, String> iriDisplayMap, 
      String url,
      List<Identifier> identifiers,
      String version,
      String name, 
      boolean includeDperecated, 
      List<OWLAnnotationProperty> publisherProps, 
      List<OWLAnnotationProperty> descriptionProps, 
      OWLAnnotationProperty codeProp, 
      OWLAnnotationProperty preferredTermProp, 
      List<OWLAnnotationProperty> synonymProps) {
    
    // Extract ontology information
    final String codeSystemUrl;

    final OWLOntologyID ontId = ont.getOntologyID();
    final Optional<IRI> iri = ontId.getOntologyIRI();
    if (version == null) {
      final Optional<IRI> v = ontId.getVersionIRI();
      if (v.isPresent()) {
        version = v.get().toString();
      } else {
        version = "NA";
      }
    }
    
    if (url != null) {
      codeSystemUrl = url;
    } else if (iri.isPresent()) {
      codeSystemUrl = iri.get().toString();
    } else {
      throw new NoIdException();
    }

    
    
    String codeSystemName = null;

    // Extract code system name
    if (name != null) {
      codeSystemName = name;
    } else {
      String label = getOntologyAnnotationValue(ont, 
          Arrays.asList(new OWLAnnotationProperty[] { factory.getRDFSLabel() }));
      if (label != null) {
        codeSystemName = label;
      } else {
        codeSystemName = codeSystemUrl;
      }
    }
    
    // Determine if ontology is derived to set the content attribute
    final IRI sourceIri = getSource(ont);

    // Populate basic code system info
    final CodeSystem cs = new CodeSystem();
    cs.setUrl(codeSystemUrl);
    // TODO: add identifiers
    
    cs.setVersion(version);
    cs.setName(codeSystemName);
    
    final String publisher = getOntologyAnnotationValue(ont, publisherProps);
    if (publisher != null) { 
      cs.setPublisher(publisher);
    }
    
    String description = getOntologyAnnotationValue(ont, descriptionProps);
    if (description != null) {
      cs.setDescription(description);
    }
    cs.setStatus(PublicationStatus.ACTIVE);
    // Create default value set
    cs.setValueSet(codeSystemUrl);
    cs.setHierarchyMeaning(CodeSystemHierarchyMeaning.ISA);
    
    boolean derived = sourceIri != null;
    if (derived) {
      cs.setContent(CodeSystemContentMode.FRAGMENT);
    } else {
      cs.setContent(CodeSystemContentMode.COMPLETE);
    }

    PropertyComponent parentProp = cs.addProperty();
    parentProp.setCode("parent");
    parentProp.setType(PropertyType.CODE);
    parentProp.setDescription("Parent codes.");
    
    PropertyComponent importedProp = cs.addProperty();
    importedProp.setCode("imported");
    importedProp.setType(PropertyType.BOOLEAN);
    importedProp.setDescription("Indicates if the concept is imported from another code system.");

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
    cs.addFilter().setCode("imported").addOperator(FilterOperator.EQUAL).setValue("True or false");
    
    // Determine if there are imports
    boolean hasImports = !ont.getImportsDeclarations().isEmpty();
    
    for (OWLClass owlClass : ont.getClassesInSignature(Imports.INCLUDED)) {
      processClass(owlClass, cs, ont, reasoner, iriSystemMap, codeSystemUrl, iriDisplayMap, 
          includeDperecated, codeProp, preferredTermProp, synonymProps, hasImports);
    }

    return cs;
  }
  
  /**
   * Iterates over all the concepts in the closure of an ontology an attempts to determine
   * the system for each one.
   * 
   * @param closure Used to return the ontology closure.
   * @return A map of IRIs to systems.
   * 
   * @throws OWLOntologyCreationException If something goes wrong creating the ontologies.
   */
  private Map<IRI, String> getIriSystemMap(Set<OWLOntology> closure) {
    log.info("Getting IRI -> system map for ontologies: \n" + getOntologiesNames(closure));
    
    // 1. For each ontology, check IRI, create OBO-like prefix and build prefix -> system map
    log.info("Building prefix -> system map");
    final Map<String, String> prefixSystemMap = new HashMap<>();
    for (OWLOntology ont : closure) {
      final IRI iri = getOntologyIri(ont);
      if (iri != null) {
        final String shortForm = iri.getShortForm();
        if (shortForm.endsWith(".owl")) {
          log.info("Found OBO-like IRI: " + iri);
          prefixSystemMap.put(shortForm.substring(0, shortForm.length() - 4).toLowerCase(), 
              iri.toString());
        } else {
          log.info("IRI is not OBO-like:" + iri);
        }
      } else {
        log.warn("Ontology " + getOntologyName(ont) + " has no IRI.");
      }
    }
    
    // 2. Create a IRI -> system map for all classes in the closure
    log.info("Building IRI -> system map");
    final Map<IRI, String> iriSystemMap = new HashMap<>();
    for (OWLOntology ont : closure) {
      final IRI ontIri = getOntologyIri(ont);
      final String ontologyIri = ontIri != null ? ontIri.toString() : "ANONYMOUS";
      for (OWLClass owlClass : ont.getClassesInSignature(Imports.EXCLUDED)) {
        final IRI classIri = owlClass.getIRI();
        String system = getSystem(owlClass, ontologyIri, prefixSystemMap);
        if (system == null) {
          // If the system cannot be determined we assume the system is the ontology where
          // the concept is declared
          iriSystemMap.put(classIri, ontologyIri);
        } else {
          iriSystemMap.put(classIri, system);
        }
      }
    }

    return iriSystemMap;
  }

  private boolean addHierarchyFields(final OWLReasoner reasoner, OWLClass owlClass, 
      ConceptDefinitionComponent cdc, boolean isRoot, Map<IRI, String> iriSystemMap, 
      String rootSystem, boolean includeDeprecated) {
    // Add hierarchy-related fields
    final Set<OWLClass> parents = reasoner.getSuperClasses(owlClass, true).getFlattened();
    
    log.debug("Found " + parents.size() + " parents for concept " + owlClass.getIRI());
    for (OWLClass parent : parents) {
      if (parent.isOWLNothing()) { 
        continue;
      }
      
      // If excluding deprecated class then also exclude from parents. In some ontologies
      // deprecated classes are still in the hierarchy, e.g. MONDO.
      if (!includeDeprecated) {
        if (isDeprecated(parent, reasoner.getRootOntology())) {
          continue;
        }
      }
      
      final IRI iri = parent.getIRI();
      final String system = iriSystemMap.get(iri);
      if (system != null) {
        if (system.equals(rootSystem)) {
          final ConceptPropertyComponent parentProp = cdc.addProperty();
          parentProp.setCode("parent");
          final String code = iri.getShortForm();
          parentProp.setValue(new CodeType(code));
        } else {
          // Use foreignParent property
          final ConceptPropertyComponent parentProp = cdc.addProperty();
          parentProp.setCode("parent");
          final String code = iri.toString();
          parentProp.setValue(new CodeType(code));
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
  
  private String getOntologyAnnotationValue(OWLOntology ont, 
      Collection<OWLAnnotationProperty> props) {
    for (OWLAnnotation ann : ont.getAnnotations()) {
      if (props.contains(ann.getProperty())) {
        Optional<OWLLiteral> val = ann.getValue().asLiteral();
        if (val.isPresent()) {
          return val.get().getLiteral();
        }
      }
    }
    return null;
  }
  
  
  private String getCode(OWLClass owlClass, OWLOntology ont, OWLAnnotationProperty prop) {
    for (OWLAnnotation a : EntitySearcher.getAnnotations(owlClass, ont, prop)) {
      OWLAnnotationValue val = a.getValue();
      if (val instanceof OWLLiteral) {
        return ((OWLLiteral) val).getLiteral();
      }
    }
    
    return null;
  }
  
  private String getPreferedTerm(OWLClass owlClass, OWLOntology ont, 
      OWLAnnotationProperty preferredTermAnnotationProperty) {
    
    for (OWLAnnotation a : EntitySearcher.getAnnotations(owlClass, ont, 
        preferredTermAnnotationProperty)) {
      OWLAnnotationValue val = a.getValue();
      if (val instanceof OWLLiteral) {
        return ((OWLLiteral) val).getLiteral();
      }
    }
    
    return null;
  }
  
  private Set<String> getSynonyms(OWLClass owlClass, OWLOntology ont, String preferredTerm, 
      List<OWLAnnotationProperty> synonymAnnotationProperties) {
    
    final Set<String> synonyms = new HashSet<>();
    for (OWLAnnotationProperty prop : synonymAnnotationProperties) {
      for (OWLAnnotation a : EntitySearcher.getAnnotations(owlClass, ont, prop)) {
        OWLAnnotationValue val = a.getValue();
        if (val instanceof OWLLiteral) {
          final String label = ((OWLLiteral) val).getLiteral();
          synonyms.add(label);
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
  
  private void processClass(OWLClass owlClass, CodeSystem cs, OWLOntology ont, 
      OWLReasoner reasoner, Map<IRI, String> iriSystemMap, String rootSystem, 
      Map<IRI, String> iriDisplayMap, boolean includeDeprecated, 
      OWLAnnotationProperty codeProp,
      OWLAnnotationProperty preferredTermProp, 
      List<OWLAnnotationProperty> synonymProps,
      boolean hasImports) {
    
    if (owlClass.isOWLNothing()) {
      return;
    }
    
    final boolean isDeprecated = isDeprecated(owlClass, ont);
    if (!includeDeprecated && isDeprecated) {
      return; // Skip this concept because it is deprecated
    }
    
    final IRI iri = owlClass.getIRI();
    final String classSystem = iriSystemMap.get(iri);
    
    // The code might come from an annotation property
    String code = null;
    if (codeProp != null) {
      code = getCode(owlClass, ont, codeProp);
    } 
    if (code == null) {
      code = classSystem.equals(rootSystem) ? iri.getShortForm() : iri.toString();
    }
    
    final ConceptDefinitionComponent cdc = new ConceptDefinitionComponent();
    cdc.setCode(code);

    // Special case: OWL:Thing
    if ("http://www.w3.org/2002/07/owl#Thing".equals(cdc.getCode())) {
      cdc.setDisplay("Thing");
    }
    
    final ConceptPropertyComponent importedProp = cdc.addProperty();
    importedProp.setCode("imported");
    // This is hard to detect appropriately because the classes declared in an ontology
    // can be declared with an arbitrary namespace.
    if (!hasImports) {
      importedProp.setValue(new BooleanType(false));
    } else {
      importedProp.setValue(new BooleanType(!classSystem.equals(rootSystem)));
    }

    boolean isRoot = false;
    isRoot = addHierarchyFields(reasoner, owlClass, cdc, isRoot, iriSystemMap, rootSystem, 
        includeDeprecated);

    ConceptPropertyComponent prop = cdc.addProperty();
    prop.setCode("root");
    prop.setValue(new BooleanType(isRoot));

    prop = cdc.addProperty();
    prop.setCode("deprecated");
    prop.setValue(new BooleanType(isDeprecated));
    
    String preferredTerm = getPreferedTerm(owlClass, ont, preferredTermProp);
    final Set<String> synonyms = getSynonyms(owlClass, ont, preferredTerm, synonymProps);
    
    if (preferredTerm == null && synonyms.isEmpty()) {
      String label = iriDisplayMap.get(iri);
      if (label != null) {
        cdc.setDisplay(label);
      } else {
        cdc.setDisplay(code);
      }
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
