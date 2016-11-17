package au.csiro.fhir.owl.web;

import java.io.ByteArrayInputStream;
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
import org.hl7.fhir.dstu3.model.CodeSystem.PropertyComponent;
import org.hl7.fhir.dstu3.model.CodeSystem.PropertyType;
import org.hl7.fhir.dstu3.model.CodeType;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Enumerations.ConformanceResourceStatus;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.dstu3.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.dstu3.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.dstu3.model.ValueSet.ValueSetComposeComponent;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.base.Optional;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.JsonParser;

@RestController
public class MainController {
    
    /** Logger */
    private final static Log log = LogFactory.getLog(MainController.class);
    
    @Autowired
    private FhirContext fhirContext;
    
    @Value("#{'${ontoserver.owl.publisher}'.split(',')}")
    private List<String> publisherElems;
    
    @Value("#{'${ontoserver.owl.description}'.split(',')}")
    private List<String> descriptionElems;
    
    /**
     * Returns the name of an ontology. Uses an rdfs:label or the ontology's IRI if one if not present. If the ontology
     * has no IRI then it returns null.
     * 
     * @param ont
     * @return
     */
    private String getOntologyName(OWLOntology ont) {
        for(OWLAnnotation ann : ont.getAnnotations()) {
            final String prop = ann.getProperty().getIRI().toString();
            Optional<OWLLiteral> val = ann.getValue().asLiteral();
            if(val.isPresent() && "http://www.w3.org/2000/01/rdf-schema#label".equals(prop)) {
                return val.get().getLiteral();
            }
        }
        
        return getOntologyIri(ont);
    }
    
    private String getOntologyIri(OWLOntology ont) {
        OWLOntologyID ontId = ont.getOntologyID();
        Optional<IRI> iri = ontId.getOntologyIRI();
        
        if(iri.isPresent()) {
            return iri.get().toString();
        } else {
            return null;
        }
    }
    
    /**
     * Adds this concept to the uber-code-system and to the include of the value set that represents just this ontology
     * (not the closure of all ontologies).
     * 
     * @param factory
     * @param cs
     * @param reasoner
     * @param conceptsWithNoLabel
     * @param processedConcepts
     * @param o
     * @param inc
     * @param owlClass
     */
    private void processClass(final OWLDataFactory factory, final CodeSystem cs,
            final OWLReasoner reasoner, final Set<String> conceptsWithNoLabel,
            final Set<String> processedConcepts, OWLOntology o, ConceptSetComponent inc,
            OWLClass owlClass) {
        final String code = owlClass.getIRI().toString();
        ConceptReferenceComponent crc = inc.addConcept();
        crc.setCode(code);
        
        if(!processedConcepts.contains(code)) {
            ConceptDefinitionComponent cdc = new ConceptDefinitionComponent();
            cdc.setCode(owlClass.getIRI().toString());
            
            boolean isDeprecated = false;
            isDeprecated = getLabels(factory, o, owlClass, cdc, isDeprecated);
            
            // Special case: OWL:Thing
            if("http://www.w3.org/2002/07/owl#Thing".equals(cdc.getCode())) {
                cdc.setDisplay("Thing");
            }
            
            if(!cdc.hasDisplay()) {
                conceptsWithNoLabel.add(cdc.getCode());
                return;                
            }
            
            processedConcepts.add(owlClass.getIRI().toString());
            conceptsWithNoLabel.remove(cdc.getCode());
            cs.addConcept(cdc);
            
            boolean isRoot = false;
            isRoot = addHierarchyFields(reasoner, owlClass, cdc, isRoot);
            
            ConceptPropertyComponent prop = cdc.addProperty();
            prop.setCode("root");
            prop.setValue(new BooleanType(isRoot));
            
            prop = cdc.addProperty();
            prop.setCode("deprecated");
            prop.setValue(new BooleanType(isDeprecated));
        }
    }
    
    private ValueSet createValueSet(OWLOntology ont, String csId, String csUrl, int vsNum) {
        final ValueSet vs = new ValueSet();
        vs.setStatus(ConformanceResourceStatus.ACTIVE);
        
        
        if(csId != null) vs.setId(csId + "_vs_" + vsNum);
        String oIri = getOntologyIri(ont);
        if(oIri == null) {
            return null;
        }
        vs.setUrl(csUrl + "?vs=" + oIri);
        String ontName = getOntologyName(ont);
        if(ontName == null) {
            return null;
        }
        vs.setName(ontName + " value set");
        return vs;
    }
    
    /**
     * Tranforms an OWL file into a FHIR code system resouce in JSON format.
     * 
     * @param file The OWL file.
     * @param id The id of the resource if you intent to PUT it or null if you intend to POST it.
     * @return
     * @throws IOException
     */
    @RequestMapping(value="/transform", method=RequestMethod.POST, produces="application/json")
    public ResponseEntity<String> transform(@RequestParam("file") MultipartFile file, @RequestParam("id") String id) {
        
        try {
            if (!file.isEmpty()) {
                byte[] bytes = file.getBytes();
                
                OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
                final OWLOntology ont;
                final OWLDataFactory factory = OWLManager.getOWLDataFactory();
                
                try {
                    ont = manager.loadOntologyFromOntologyDocument(new ByteArrayInputStream(bytes));
                } catch (OWLOntologyCreationException e) {
                    return new ResponseEntity<String>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                }
                
                // Extract ontology information
                final String codeSystemUrl;
                final String codeSystemVersion;
                
                OWLOntologyID ontId = ont.getOntologyID();
                Optional<IRI> iri = ontId.getOntologyIRI();
                Optional<IRI> version = ontId.getVersionIRI();
                
                if(iri.isPresent()) {
                    codeSystemUrl = iri.get().toString();
                } else {
                    return new ResponseEntity<String>("An ontology with no IRI cannot be indexed.", 
                            HttpStatus.BAD_REQUEST);
                }
                
                if(version.isPresent()) {
                    codeSystemVersion = version.get().toString();
                } else {
                    codeSystemVersion = "NA";
                }
                
                // Extract ontology level info
                String codeSystemName = codeSystemUrl;
                String publisher = null;
                String description = null;
                
                Map<String, List<String>> annMap = new HashMap<>();
                
                for(OWLAnnotation ann : ont.getAnnotations()) {
                    final String prop = ann.getProperty().getIRI().toString();
                    Optional<OWLLiteral> val = ann.getValue().asLiteral();
                    if(val.isPresent()) {
                        List<String> vals = annMap.get(prop);
                        if(vals == null) {
                            vals = new ArrayList<>();
                            annMap.put(prop, vals);
                        }
                        vals.add(val.get().getLiteral());
                    }
                }
                
                if(annMap.containsKey("http://www.w3.org/2000/01/rdf-schema#label")) {
                    // This is the name of the ontology
                    codeSystemName = annMap.get("http://www.w3.org/2000/01/rdf-schema#label").get(0);
                }
                
                for(String publisherElem : publisherElems) {
                    if(annMap.containsKey(publisherElem)) {
                        // This is the publisher of the ontology
                        publisher = annMap.get(publisherElem).get(0); // Get first publisher - FHIR spec only supports one
                        break;
                    }
                }
               
                for(String descriptionElem : descriptionElems) {
                    if(annMap.containsKey(descriptionElem)) {
                        // This is the description of the ontology
                        description = annMap.get(descriptionElem).get(0);
                        break;
                    }
                }
                
                // Populate basic code system info
                final CodeSystem cs = new CodeSystem();
                if(id != null) cs.setId(id);
                cs.setUrl(codeSystemUrl);
                cs.setVersion(codeSystemVersion);
                cs.setName(codeSystemName);
                if(publisher != null) cs.setPublisher(publisher);
                if(description != null) cs.setDescription(description);
                cs.setStatus(ConformanceResourceStatus.ACTIVE);
                cs.setValueSet("http://purl.obolibrary.org/obo/hp.owl");
                cs.setHierarchyMeaning(CodeSystemHierarchyMeaning.SUBSUMES);
                
                PropertyComponent parentProp = cs.addProperty();
                parentProp.setCode("parent");
                parentProp.setType(PropertyType.CODE);
                parentProp.setDescription("Parent codes.");
                
                PropertyComponent rootProp = cs.addProperty();
                rootProp.setCode("root");
                rootProp.setType(PropertyType.BOOLEAN);
                rootProp.setDescription("Indicates if this concept is a root concept (i.e. Thing is equivalent or a "
                        + "direct parent)");
                
                PropertyComponent depProp = cs.addProperty();
                depProp.setCode("deprecated");
                depProp.setType(PropertyType.BOOLEAN);
                depProp.setDescription("Indicates if this concept is deprecated.");
            
                // Now classify the ontology
                OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
                if(log.isDebugEnabled()) log.debug("Classifying ontology");
                    
                final OWLReasoner reasoner = reasonerFactory.createReasoner(ont);
                reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
                    
                if(log.isDebugEnabled()) log.debug("Visiting nodes");
                
                final Set<String> conceptsWithNoLabel = new HashSet<>();
                final Set<String> processedConcepts = new HashSet<>();
                
                Bundle b = new Bundle();
                b.setType(BundleType.COLLECTION);
                BundleEntryComponent bec = b.addEntry();
                bec.setResource(cs);
                
                // Process main ontology - don't process imports
                log.info("Processing main ontology (" + ont.getClassesInSignature(Imports.EXCLUDED).size() + 
                        " classes)");
                ValueSet vs = createValueSet(ont, id, codeSystemUrl, 0);
                ValueSetComposeComponent comp = new ValueSetComposeComponent();
                vs.setCompose(comp);
                ConceptSetComponent inc = comp.addInclude();
                inc.setSystem(codeSystemUrl);
                inc.setVersion(codeSystemVersion);
                bec = b.addEntry();
                bec.setResource(vs);
                for(OWLClass c : ont.getClassesInSignature(Imports.EXCLUDED)) {
                    processClass(factory, cs, reasoner, conceptsWithNoLabel, processedConcepts, ont, inc, c);
                }
                
                // Process imported ontologies
                int i = 1;
                for(OWLOntology o : manager.getImports(ont)) {
                    vs = createValueSet(o, id, codeSystemUrl, i);
                    comp = new ValueSetComposeComponent();
                    vs.setCompose(comp);
                    inc = comp.addInclude();
                    inc.setSystem(codeSystemUrl);
                    inc.setVersion(codeSystemVersion);
                    bec = b.addEntry();
                    bec.setResource(vs);
                    
                    String ontologyName = getOntologyName(o);
                    log.info("Processing imported ontology " + ontologyName + " (" + 
                            o.getClassesInSignature(Imports.INCLUDED).size() + " classes)");
                    for(OWLClass c : o.getClassesInSignature(Imports.INCLUDED)) {
                        processClass(factory, cs, reasoner, conceptsWithNoLabel, processedConcepts, o, inc, c);
                    }
                    i++;
                }
                
                log.info("Concepts with no label: " + conceptsWithNoLabel);

                JsonParser jp = (JsonParser) fhirContext.newJsonParser();
                jp.setPrettyPrint(true);
                return new ResponseEntity<String>(jp.encodeResourceToString(b), HttpStatus.OK);
                
            } else {
                return new ResponseEntity<String>("No file was uploaded", HttpStatus.BAD_REQUEST);
            }
        } catch(IOException e) {
            return new ResponseEntity<String>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean addHierarchyFields(final OWLReasoner reasoner, OWLClass owlClass, ConceptDefinitionComponent cdc,
            boolean isRoot) {
        // Add hierarchy-related fields
        for(Node<OWLClass> parent : reasoner.getSuperClasses(owlClass, true)) {
            for(OWLClass ent : parent.getEntities()) {
                /*if(ent.isOWLThing()){
                    isRoot = true;
                    continue;
                }*/
                if(ent.isOWLNothing()) continue;
                ConceptPropertyComponent prop = cdc.addProperty();
                prop.setCode("parent");
                prop.setValue(new CodeType(ent.getIRI().toString()));
            }
        }
        
        // Check if this concept is equivalent to Thing - in this case it is a root
        for(OWLClass eq : reasoner.getEquivalentClasses(owlClass)) {
            if(eq.isOWLThing()){
                isRoot = true;
                break;
            }
        }
        return isRoot;
    }
    
    private boolean getLabels(final OWLDataFactory factory, OWLOntology o, OWLClass owlClass,
            ConceptDefinitionComponent cdc, boolean isDeprecated) {
        // Add preferred term (from rdfs:label)
        final List<String> labels = new ArrayList<>();
        final Set<String> synonyms = new HashSet<>();
        for(OWLAnnotation a : EntitySearcher.getAnnotations(owlClass, o, 
                factory.getRDFSLabel())) {
            OWLAnnotationValue val = a.getValue();
            if(val instanceof OWLLiteral) {
                final String label = ((OWLLiteral) val).getLiteral();
                labels.add(label);
            }
        }
        
        if(labels.size() == 1) {
            cdc.setDisplay((String) labels.toArray()[0]);
        } else if(labels.size() > 1){
            cdc.setDisplay(labels.get(0));
            labels.remove(0);
            synonyms.addAll(labels);
        }
        
        // Add synonyms
        for(OWLAnnotation ann : EntitySearcher.getAnnotationObjects(owlClass, o)) {
            OWLAnnotationProperty prop = ann.getProperty();
            if(prop != null && prop.getIRI().getShortForm().equals("hasExactSynonym")) {
                // This is an oboInOwl extension TODO review
                OWLAnnotationValue val = ann.getValue();
                if(val != null) {
                    Optional<OWLLiteral> lit = val.asLiteral();
                    if(lit.isPresent()) {
                        String label = lit.get().getLiteral();
                        synonyms.add(label);
                    }
                }
            } else if(prop != null && prop.getIRI().getShortForm().equals("deprecated")) {
                OWLAnnotationValue val = ann.getValue();
                if(val != null) {
                    Optional<OWLLiteral> lit = val.asLiteral();
                    if(lit.isPresent()) {
                        isDeprecated = lit.get().parseBoolean();
                    }
                }
            }
        }
        
        for(String syn : synonyms) {
            // This is a synonym - but we don't know the language
            ConceptDefinitionDesignationComponent cddc = cdc.addDesignation();
            cddc.setValue(syn);
            cddc.setUse(new Coding("http://snomed.info/sct", "900000000000013009", 
                    "Synonym (core metadata concept)"));
        }
        
        return isDeprecated;
    }
    
}
