/*
  Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to
  license terms and conditions.
 */
package au.csiro.fhir.owl;

import au.csiro.fhir.owl.util.GraphUtils;
import ca.uhn.fhir.context.FhirContext;

import com.google.common.base.Optional;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

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
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactDetail;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.SimpleIRIMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.manchester.cs.jfact.JFactFactory;

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
    log.info("Checking for IRI mappings in home directory " + System.getProperty("user.home"));
    InputStream input;
    try {
      input = FhirContext.class.getClassLoader().getResourceAsStream("iri_mappings.txt");
      if (input == null) {
        log.info("Did not find iri_mappings.txt in classpath.");
        return;
      }
      
      final String[] lines = getLinesFromInputStream(input);
      for (String line : lines) {
        if (line.startsWith("#")) {
          continue;
        }
        String[] parts = line.split("[,]");
        
        // Check file exists
        final File tgtFile = new File(System.getProperty("user.home") + parts[1]);
        if (tgtFile.exists()) {
          iriMap.put(IRI.create(parts[0]), IRI.create(tgtFile));
          log.info("Adding mapping " + parts[0] + " -> " +  tgtFile.toString());
        } else {
          log.warn("Mapping was not added because file " +  tgtFile.toString() + " does not exist.");
        }
      }
      
    } catch (Throwable t) {
      log.warn("There was a problem loading IRI mappings.", t);
    }
  }
  
  /**
   * Transforms an OWL file into a FHIR code system.
   * 
   * @param csp The code system properties.
   * @param cp The concept properties.
   * @param mainNamespaces A set of namespaces that correspond to the main ontology.
   * 
   * @throws IOException If an IO error ocurrs.
   * @throws OWLOntologyCreationException If there is a problem loading the ontology.
   */
  public void transform(CodeSystemProperties csp, ConceptProperties cp, Set<String> mainNamespaces)
      throws IOException, OWLOntologyCreationException {
    log.info("Creating code systems");
    
    final CodeSystem codeSystem = createCodeSystem(csp, cp, mainNamespaces);
    
    final File output = csp.getOutput();
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {
      log.info("Writing code system to file: " + output.getAbsolutePath());
      ctx.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(codeSystem, bw);
      log.info("Done!");
    }
  }
  
  /**
   * Transforms an OWL file into a FHIR code system.
   * 
   * @param csp The code system properties.
   * @param cp The concept properties.
   * 
   * @throws IOException If there is an I/O issue.
   * @throws OWLOntologyCreationException If there is a problem creating the ontology.
   */
  public void transform(CodeSystemProperties csp, ConceptProperties cp) 
      throws IOException, OWLOntologyCreationException {
    transform(csp, cp, null);
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
    return res.toArray(new String[0]);
  }
  
  private void addIriMappings(OWLOntologyManager manager) {
    for (IRI key : iriMap.keySet()) {
      manager.getIRIMappers().add(new SimpleIRIMapper(key, iriMap.get(key)));
    }
  }
  
  private Set<IRI> getIris(Set<OWLClass> classes) {
    final Set<IRI> res = new HashSet<>();
    for (OWLClass oc : classes) {
      res.add(oc.getIRI());
    }
    return res;
  }
  
  private CodeSystem createCodeSystem(CodeSystemProperties csp, ConceptProperties cp, 
      Set<String> mainNamespaces)
      throws OWLOntologyCreationException {
    
    final File input = csp.getInput();
    
    log.info("Loading ontology from file " + input.getAbsolutePath());
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    addIriMappings(manager);
    final OWLOntology rootOnt = manager.loadOntologyFromOntologyDocument(input);
    
    // We only need the preferred term property here
    final OWLDataFactory factory = manager.getOWLDataFactory();
    final OWLAnnotationProperty preferredTermProp = cp.getDisplay(factory);
    
    // We implement the two supported mechanisms to determine which concepts belong in the
    // main ontology. If the main namespaces are provided then those are used. Otherwise
    // we need to calculate which concepts are defined in the main file and are not
    // defined in the imported ontologies
    final Set<IRI> irisInMain = calculateIrisInMain(mainNamespaces, rootOnt);
    
    // Extract labels for all classes
    final Map<IRI, String> iriDisplayMap = new HashMap<>();
    for (OWLClass owlClass : rootOnt.getClassesInSignature(Imports.INCLUDED)) {
      iriDisplayMap.put(owlClass.getIRI(), null);
    }
    
    final Set<OWLOntology> closure = manager.getImportsClosure(rootOnt);
    for (OWLOntology ont : closure) {
      for (OWLClass oc : ont.getClassesInSignature()) {
        if (iriDisplayMap.containsKey(oc.getIRI())) {
          String pt = getPreferedTerm(oc, ont, preferredTermProp, Collections.emptyList());
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
    OWLReasonerFactory reasonerFactory;
    String reasonerName = csp.getReasoner();
    if (reasonerName.equals("elk")) {
      reasonerFactory = new ElkReasonerFactory();
    } else if (reasonerName.equals("jfact")) {
      reasonerFactory = new JFactFactory();
    } else {
      throw new RuntimeException("Invalid reasoner " + reasonerName);
    }

    log.info("Classifying ontology " + getOntologyName(csp, rootOnt, factory) + " with " + reasonerName);
    OWLReasoner reasoner = reasonerFactory.createReasoner(rootOnt);
    reasoner.precomputeInferences();

    // Create code system
    return createCodeSystem(rootOnt, manager.getOWLDataFactory(), reasoner, mainNamespaces, 
        irisInMain, iriDisplayMap, csp, cp);
  }

  Set<IRI> calculateIrisInMain(Set<String> mainNamespaces, OWLOntology rootOnt) {
    final Set<IRI> irisInMain = new HashSet<>();
    if (mainNamespaces == null || mainNamespaces.isEmpty()) {
      // Get concepts in main ontology
      irisInMain.addAll(getIris(rootOnt.getClassesInSignature(Imports.EXCLUDED)));

      // Get all concepts in imported ontologies
      final Set<IRI> importedIris = new HashSet<>();
      for (OWLOntology io : rootOnt.getImports()) {
        importedIris.addAll(getIris(io.getClassesInSignature(Imports.INCLUDED)));
      }

      // Remove concepts in imported ontologies from the ones in the main ontology
      irisInMain.removeAll(importedIris);
    }
    return irisInMain;
  }

  /**
   * Creates a code system from an ontology.
   * 
   * @param ont The ontology.
   * @param factory The OWL factory.
   * @param reasoner The OWL reasoner.
   * @param mainNamespaces The namespaces of concepts that belong in the main ontology. Might be 
   *     empty.
   * @param irisInMain The IRIs that belong in the main namespaces. Only populated if
   *     mainNamespaces is empty.
   * @param iriDisplayMap A map of IRIs to their display.
   * @param csp The code system properties.
   * @param cp The concept properties.
   * 
   * @return The code system.
   */
  CodeSystem createCodeSystem(
      OWLOntology ont, 
      final OWLDataFactory factory, 
      OWLReasoner reasoner, 
      Set<String> mainNamespaces, 
      Set<IRI> irisInMain,
      Map<IRI, String> iriDisplayMap,
      CodeSystemProperties csp,
      ConceptProperties cp) {
    
    // Populate basic code system info
    final CodeSystem cs = new CodeSystem();
    
    // Id
    final String id = csp.getId();
    if (id != null) {
      cs.setId(id);
    }
    
    // Language
    final String language = csp.getLanguage();
    if (language != null) {
      cs.setLanguage(language);
    }
    
    final OWLOntologyID ontId = ont.getOntologyID();
    final Optional<IRI> iri = ontId.getOntologyIRI();
    final Optional<IRI> v = ontId.getVersionIRI();
    
    // URL
    final String url = csp.getUrl();
    if (url != null) {
      cs.setUrl(url);
    } else {
      if (iri.isPresent()) {
        String iriString = iri.get().toString();
        if (iriString.endsWith(".owl") && csp.isUseFhirExtension()) {
          cs.setUrl(iriString.substring(0, iriString.length() - 3) + "fhir");
        } else {
          cs.setUrl(iriString);
        }
      } else {
        throw new NoIdException();
      }
    }
    
    // Identifier
    cs.setIdentifier(csp.getIdentifiers());
    
    // Version
    String version = csp.getVersion();
    if (version == null) {
      if (v.isPresent()) {
        version = v.get().toString();
      } else {
        version = "NA";
      }
    }
    String dateRegex = csp.getDateRegex();
    if (dateRegex != null) {
      try {
        final Matcher matcher = Pattern.compile(dateRegex).matcher(version);
        if (matcher.find() && matcher.groupCount() == 3) {
          StringBuilder sb = new StringBuilder();
          try {
            sb.append(matcher.group("year"));
            sb.append(matcher.group("month"));
            sb.append(matcher.group("day"));
          } catch (IllegalArgumentException e) {
            log.warn("Could not extract date using regex " + dateRegex + ": " + e.getLocalizedMessage());
          }
          version = sb.toString();
        }
      } catch (PatternSyntaxException e) {
        log.warn("The date regex '" + dateRegex + "' is invalid.");
      } catch (Exception e) {
        log.warn("There was a problem with date regex '" + dateRegex + "': " + e.getLocalizedMessage());
      }
    }
    cs.setVersion(version);
    
    // Name
    final String name = getOntologyName(csp, ont, factory);
    cs.setName(name);
    
    // Title
    final String title = csp.getTitle();
    if (title != null) {
      cs.setTitle(title);
    }
    
    // Status
    final String status = csp.getStatus();
    if (status != null) {
      cs.setStatus(PublicationStatus.fromCode(status));
    }
    
    // Experimental
    cs.setExperimental(csp.isExperimental());
    
    // Date
    final Calendar date = csp.getDate();
    if (date != null) {
      cs.setDate(date.getTime());
    }
    
    // Publisher
    final String publisher = csp.getPublisher();
    if (publisher != null) {
      cs.setPublisher(publisher);
    } else {
      final List<OWLAnnotationProperty> publisherProps = csp.getPublisherProps(factory);
      final String publisherFromProp = getOntologyAnnotationValue(ont, publisherProps);
      if (publisherFromProp != null) { 
        cs.setPublisher(publisherFromProp);
      }
    }
    
    // Contact
    final List<ContactDetail> contacts = csp.getContacts();
    if (!contacts.isEmpty()) {
      cs.setContact(contacts);
    }
    
    // Description
    final String description = csp.getDescription();
    if (description != null) {
      cs.setDescription(description);
    } else {
      final List<OWLAnnotationProperty> descriptionProps = csp.getDescriptionProps(factory);
      final String descriptionFromProp = getOntologyAnnotationValue(ont, descriptionProps);
      if (descriptionFromProp != null) { 
        cs.setDescription(descriptionFromProp);
      }
    }
    
    // Purpose
    final String purpose = csp.getPurpose();
    if (purpose != null) {
      cs.setPurpose(purpose);
    }
  
    // Jurisdictions
    final List<CodeableConcept> jurisdictions = csp.getJurisdictions();
    if (!jurisdictions.isEmpty()) {
      cs.setJurisdiction(jurisdictions);
    }
    
    // Copyright
    final String copyright = csp.getCopyright();
    if (copyright != null) {
      cs.setCopyright(copyright);
    }
    
    // Value set
    final String valueset = csp.getValueSet();
    cs.setValueSet(Objects.requireNonNullElseGet(valueset, () -> createVsUrl(cs.getUrl())));
  
    // HeirarchyMeaning
    final String heirarchyMeaning = csp.getHeirarchyMeaning();
    if (heirarchyMeaning != null) {
      cs.setHierarchyMeaning(CodeSystem.CodeSystemHierarchyMeaning.fromCode(heirarchyMeaning));
    } else {
      cs.setHierarchyMeaning(CodeSystemHierarchyMeaning.ISA);
    }
    
    // Compositional
    cs.setCompositional(csp.isCompositional());
    
    // Version needed
    cs.setVersionNeeded(csp.isVersionNeeded());
    
    // Content
    final String content = csp.getContent();
    if (content != null) {
      cs.setContent(CodeSystemContentMode.fromCode(content));
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
    final boolean hasImports = !ont.getImportsDeclarations().isEmpty();
    
    final boolean includeDeprecated = csp.isIncludeDeprecated();
    final OWLAnnotationProperty codeProp = cp.getCode(factory);
    final OWLAnnotationProperty preferredTermProp = cp.getDisplay(factory);
    final List<OWLAnnotationProperty> synonymProps = cp.getDesignations(factory);
    final String stringToReplaceInCodes = cp.getStringToReplaceInCodes();
    final String replacementStringInCodes = cp.getReplacementStringInCodes();
    final List<String> labelsToExclude = cp.getLabelsToExclude();
    
    int count = 0;

    // Add classes
    Set<OWLClass> classes = ont.getClassesInSignature(Imports.INCLUDED);
    classes.add(factory.getOWLThing());
    Node<OWLClass> nothing = reasoner.getEquivalentClasses(factory.getOWLNothing());

    // Need to filter equivalents to OWLNothing
    classes = classes.stream()
      .filter(x -> {
        return !nothing.contains(x);
      })
      .collect(Collectors.toSet());

    /* Shouldn't need to calculate transitive reduction because the reasoner should take care of it. However, JFact
     * doesn't seem to do this properly with object properties calculating for everything just in case.
     */
    Map<OWLClass, Set<OWLClass>> classParents = GraphUtils.transitiveReduction(
      classes, c -> reasoner.getSuperClasses(c, false).getFlattened());
    
    for (OWLClass owlClass : classes) {
      if (processEntity(owlClass, cs, ont, mainNamespaces, irisInMain, iriDisplayMap,
          includeDeprecated, codeProp, preferredTermProp, synonymProps, hasImports,
          stringToReplaceInCodes, replacementStringInCodes, labelsToExclude, classParents)) {
        count++;
      }
    }

    if (csp.getReasoner().equals("jfact")) {
      // Add object properties
      final Set<OWLObjectProperty> objectProps = ont.getObjectPropertiesInSignature(Imports.INCLUDED);
      if (!objectProps.isEmpty()) {
        objectProps.add(factory.getOWLTopObjectProperty());

        Map<OWLObjectProperty, Set<OWLObjectProperty>> opParents =
          GraphUtils.transitiveReduction(objectProps, p -> reasoner.getSuperObjectProperties(p, false).getFlattened()
            .stream()
            .filter(o -> !o.isAnonymous())
            .map(OWLObjectPropertyExpression::asOWLObjectProperty)
            .collect(Collectors.toSet()));

        for (OWLObjectProperty prop : objectProps) {
          if (processEntity(prop, cs, ont, mainNamespaces, irisInMain, iriDisplayMap,
            includeDeprecated, codeProp, preferredTermProp, synonymProps, hasImports,
            stringToReplaceInCodes, replacementStringInCodes, labelsToExclude, opParents)) {
            count++;
          }
        }
      }

      // Add data properties
      final Set<OWLDataProperty> dataProps = ont.getDataPropertiesInSignature(Imports.INCLUDED);
      if (!dataProps.isEmpty()) {
        dataProps.add(factory.getOWLTopDataProperty());

        Map<OWLDataProperty, Set<OWLDataProperty>> dpParents =
          GraphUtils.transitiveReduction(dataProps, p -> reasoner.getSuperDataProperties(p, false).getFlattened()
            .stream()
            .filter(o -> !o.isAnonymous())
            .map(OWLDataPropertyExpression::asOWLDataProperty)
            .collect(Collectors.toSet()));

        for (OWLDataProperty prop : dataProps) {
          if (processEntity(prop, cs, ont, mainNamespaces, irisInMain, iriDisplayMap,
            includeDeprecated, codeProp, preferredTermProp, synonymProps, hasImports,
            stringToReplaceInCodes, replacementStringInCodes, labelsToExclude, dpParents)) {
            count++;
          }
        }
      }
    }
    
    // Count
    cs.setCount(count);

    return cs;
  }

  private String createVsUrl(String url) {    
    if (url.contains("?")) {
      return url + "&vs";
    } else {
      return url + "?vs";
    }
  }

  private <T extends OWLEntity> boolean addHierarchyFields(OWLEntity owlEntity,
                                                           Set<T> parents,
                                                           OWLOntology rootOntology,
                                                           ConceptDefinitionComponent cdc,
                                                           Set<String> mainNamespaces,
                                                           Set<IRI> irisInMain,
                                                           boolean includeDeprecated,
                                                           String stringToReplaceInCodes,
                                                           String replacementStringInCodes,
                                                           boolean hasImports) {
    if (owlEntity.isTopEntity()) {
      return true;
    }

    if (parents == null) {
      throw new RuntimeException("Got null parents. This should not happen!");
    }
    
    log.debug("Found " + parents.size() + " parents for concept " + owlEntity.getIRI());
    for (OWLEntity parent : parents) {
      if (parent.isBottomEntity()) {
        continue;
      }
      
      // If excluding deprecated class then also exclude from parents. In some ontologies
      // deprecated classes are still in the hierarchy, e.g. MONDO.
      if (!includeDeprecated) {
        if (isDeprecated(parent, rootOntology)) {
          continue;
        }
      }
      
      final IRI iri = parent.getIRI();
      final ConceptPropertyComponent parentProp = cdc.addProperty();
      parentProp.setCode("parent");
      
      final boolean imported = isImported(iri, mainNamespaces, irisInMain, hasImports);
      if (!imported) {
        String code = iri.getShortForm();
        if (stringToReplaceInCodes != null && replacementStringInCodes != null) {
          code = code.replace(stringToReplaceInCodes, replacementStringInCodes);
        }
        parentProp.setValue(new CodeType(code));
      } else {
        final String code = iri.toString();
        parentProp.setValue(new CodeType(code));
      }
    }

    // If we get here then this concept is not a root concept
    return false;
  }
  
  /**
   * Determines if an OWL class is deprecated based on annotations.
   * 
   * @param owlEntity The OWL entity.
   * @param ont The ontology it belongs to.
   * @return boolean True if deprecated, false otherwise.
   */
  private boolean isDeprecated(OWLEntity owlEntity, OWLOntology ont) {
    boolean isDeprecated = false;
    for (OWLAnnotation ann : EntitySearcher.getAnnotationObjects(owlEntity, ont)) {
      OWLAnnotationProperty prop = ann.getProperty();
      if (prop.getIRI().getShortForm().equals("deprecated")) {
        OWLAnnotationValue val = ann.getValue();
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
  
  
  private String getCode(OWLEntity owlEntity, OWLOntology ont, OWLAnnotationProperty prop) {
    for (OWLAnnotation a : EntitySearcher.getAnnotations(owlEntity, ont, prop)) {
      OWLAnnotationValue val = a.getValue();
      if (val instanceof OWLLiteral) {
        return ((OWLLiteral) val).getLiteral();
      }
    }
    
    return null;
  }
  
  private String getPreferedTerm(OWLEntity owlEntity, OWLOntology ont,
      OWLAnnotationProperty preferredTermAnnotationProperty, List<String> labelsToExclude) {
    
    SortedSet<String> candidates = new TreeSet<>();
    for (OWLAnnotation a : EntitySearcher.getAnnotations(owlEntity, ont, preferredTermAnnotationProperty)) {
      OWLAnnotationValue val = a.getValue();
      if (val instanceof OWLLiteral) {
        String label = ((OWLLiteral) val).getLiteral();
        if (!labelsToExclude.contains(label)) {
          candidates.add(label);
        }
      }
    }
    if (!candidates.isEmpty()) {
      return candidates.first();
    } else {
      return null;
    }
  }
  
  private Set<String> getSynonyms(OWLEntity owlEntity, OWLOntology ont, String preferredTerm,
      List<OWLAnnotationProperty> synonymAnnotationProperties, List<String> labelsToExclude) {
    final Set<String> synonyms = new HashSet<>();
    for (OWLAnnotationProperty prop : synonymAnnotationProperties) {
      for (OWLAnnotation a : EntitySearcher.getAnnotations(owlEntity, ont, prop)) {
        OWLAnnotationValue val = a.getValue();
        if (val instanceof OWLLiteral) {
          final String label = ((OWLLiteral) val).getLiteral();
          if (!labelsToExclude.contains(label)) {
            synonyms.add(label);
          }
        }
      }
    }
    synonyms.remove(preferredTerm);
    return synonyms;
  }

  /**
   * Returns the name of an ontology.
   * 
   * @param ont The ontology.
   * @return The name of the ontology or null if it has no name.
   */
  private String getOntologyName(CodeSystemProperties csp, OWLOntology ont, 
      OWLDataFactory factory) {
    final String name = csp.getName();
    if (name != null) {
      return name;
    } else {
      String label = null;
      OWLAnnotationProperty prop = csp.getNameProp(factory);
      if (prop != null) {
        label = getOntologyAnnotationValue(ont, Collections.singletonList(prop));
      }
      
      if (label == null) {
        final IRI iri = getOntologyIri(ont);
        if (iri == null) {
          throw new RuntimeException("The ontology has no IRI!");
        }
        label = iri.toString();
      }
      return label;
    }
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
  
  private boolean isImported(IRI iri, Set<String> mainNamespaces, Set<IRI> irisInMain, 
      boolean hasImports) {
    if (mainNamespaces != null && ! mainNamespaces.isEmpty()) {
      String s = iri.toString();
      for (String ns : mainNamespaces) {
        if (s.startsWith(ns)) {
          return false;
        }
      }
      return true;
    } else {
      if (!hasImports) {
        return false;
      } else {
        return !irisInMain.contains(iri);
      }
    }
  }
  
  private <T extends OWLEntity> boolean processEntity(
    OWLEntity owlEntity,
    CodeSystem cs,
    OWLOntology ont,
    Set<String> mainNamespaces,
    Set<IRI> irisInMain,
    Map<IRI, String> iriDisplayMap,
    boolean includeDeprecated,
    OWLAnnotationProperty codeProp,
    OWLAnnotationProperty preferredTermProp,
    List<OWLAnnotationProperty> synonymProps,
    boolean hasImports,
    String stringToReplaceInCodes,
    String replacementStringInCodes,
    List<String> labelsToExclude,
    Map<T, Set<T>> parents) {

    if (owlEntity.isBottomEntity()) {
      return false;
    }
    
    final boolean isDeprecated = isDeprecated(owlEntity, ont);
    if (!includeDeprecated && isDeprecated) {
      return false; // Skip this concept because it is deprecated
    }
    
    final IRI iri = owlEntity.getIRI();
    
    // Determine if concept is imported or not
    boolean imported = isImported(iri, mainNamespaces, irisInMain, hasImports);
    
    // The code might come from an annotation property
    String code = null;
    if (codeProp != null) {
      code = getCode(owlEntity, ont, codeProp);
    } 
    if (code == null) {
      code = imported ? iri.toString() : iri.getShortForm();
    }
    
    // We only do code replacements for local codes - not for imported
    if (!imported && stringToReplaceInCodes != null && replacementStringInCodes != null) {
      code = code.replace(stringToReplaceInCodes, replacementStringInCodes);
    }
    
    final ConceptDefinitionComponent cdc = new ConceptDefinitionComponent();
    cdc.setCode(code);

    // Special cases: OWL:Thing, top object property and top data property
    if ("http://www.w3.org/2002/07/owl#Thing".equals(cdc.getCode())) {
      cdc.setDisplay("Thing");
    } else if ("http://www.w3.org/2002/07/owl#topObjectProperty".equals(cdc.getCode())) {
      cdc.setDisplay("Top Object Property");
    } else if ("http://www.w3.org/2002/07/owl#topDataProperty".equals(cdc.getCode())) {
      cdc.setDisplay("Top Data Property");
    }

    final ConceptPropertyComponent importedProp = cdc.addProperty();
    importedProp.setCode("imported");
    // This is hard to detect appropriately because the classes declared in an ontology
    // can be declared with an arbitrary namespace.
    importedProp.setValue(new BooleanType(imported));


    boolean isRoot = addHierarchyFields(owlEntity, parents.get(owlEntity), ont, cdc, mainNamespaces, irisInMain,
        includeDeprecated, stringToReplaceInCodes, replacementStringInCodes, hasImports);

    ConceptPropertyComponent prop = cdc.addProperty();
    prop.setCode("root");
    prop.setValue(new BooleanType(isRoot));

    prop = cdc.addProperty();
    prop.setCode("deprecated");
    prop.setValue(new BooleanType(isDeprecated));
    
    String preferredTerm = getPreferedTerm(owlEntity, ont, preferredTermProp, labelsToExclude);
    final Set<String> synonyms = getSynonyms(owlEntity, ont, preferredTerm, synonymProps,
        labelsToExclude);
    
    if (preferredTerm == null && synonyms.isEmpty()) {
      String label = iriDisplayMap.get(iri);
      if (label != null) {
        cdc.setDisplay(label);
      } else if(!cdc.hasDisplay()) {
        cdc.setDisplay(code);
      }
    } else if (preferredTerm == null) {
      // No preferred term but there are synonyms so pick any one as the display
      preferredTerm = synonyms.iterator().next();
      synonyms.remove(preferredTerm);
      
      cdc.setDisplay(preferredTerm);
      addSynonyms(synonyms, cdc);
    } else {
      cdc.setDisplay(preferredTerm);
      addSynonyms(synonyms, cdc);
    }
    
    cs.addConcept(cdc);
    return true;
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

  public void setCtx(FhirContext ctx) {
    this.ctx = ctx;
  }
}
