package au.csiro.fhir.owl.web;

import static org.semanticweb.owlapi.search.EntitySearcher.getAnnotationObjects;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.CodeSystem.CodeSystemHierarchyMeaning;
import org.hl7.fhir.dstu3.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.dstu3.model.CodeSystem.ConceptDefinitionDesignationComponent;
import org.hl7.fhir.dstu3.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.dstu3.model.CodeSystem.PropertyComponent;
import org.hl7.fhir.dstu3.model.CodeSystem.PropertyType;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Enumerations.ConformanceResourceStatus;
import org.hl7.fhir.dstu3.model.StringType;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObjectVisitor;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
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
     * Tranforms an OWL file into a FHIR code system resouce in JSON format.
     * 
     * @param file
     * @return
     * @throws IOException
     */
    @RequestMapping(value="/transform", method=RequestMethod.POST, produces="application/json")
    public ResponseEntity<String> transform(@RequestParam("file") MultipartFile file) {
        
        try {
            if (!file.isEmpty()) {
                byte[] bytes = file.getBytes();
                
                OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
                final OWLOntology ont;
                
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
                cs.setUrl(codeSystemUrl);
                cs.setVersion(codeSystemVersion);
                cs.setName(codeSystemName);
                if(publisher != null) cs.setPublisher(publisher);
                if(description != null) cs.setDescription(description);
                cs.setStatus(ConformanceResourceStatus.ACTIVE);
                cs.setHierarchyMeaning(CodeSystemHierarchyMeaning.SUBSUMES);
                
                PropertyComponent parentProp = cs.addProperty();
                parentProp.setCode("parent");
                parentProp.setType(PropertyType.STRING);
                parentProp.setDescription("Parent codes.");
            
                // Now classify the ontology
                OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
                if(log.isDebugEnabled()) log.debug("Classifying ontology");
                    
                final OWLReasoner reasoner = reasonerFactory.createReasoner(ont);
                reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
                    
                if(log.isDebugEnabled()) log.debug("Visiting nodes");
                ont.accept(new OWLNamedObjectVisitor() {
        
                    @Override
                    public void visit(OWLClass owlClass) {
                        
                        ConceptDefinitionComponent cdc = cs.addConcept();
                        cdc.setCode(owlClass.getIRI().toString());
                        
                        // Add preferred term and synonyms
                        for(OWLAnnotation ann : getAnnotationObjects(owlClass, ont)) {
                            OWLAnnotationProperty prop = ann.getProperty();
                            if(prop != null && prop.getIRI().getShortForm().equals("label")) {
                                OWLAnnotationValue val = ann.getValue();
                                if(val != null) {
                                    Optional<OWLLiteral> lit = val.asLiteral();
                                    if(lit.isPresent()) {
                                        String label = lit.get().getLiteral();
                                        // This is the preferred term
                                        cdc.setDisplay(label);
                                    }
                                }
                            } else if(prop != null && prop.getIRI().getShortForm().equals("hasExactSynonym")) {
                                // This is an oboInOwl extension TODO review
                                OWLAnnotationValue val = ann.getValue();
                                if(val != null) {
                                    Optional<OWLLiteral> lit = val.asLiteral();
                                    if(lit.isPresent()) {
                                        String label = lit.get().getLiteral();
                                        // This is a synonym - but we don't know the language
                                        ConceptDefinitionDesignationComponent cddc = cdc.addDesignation();
                                        cddc.setValue(label);
                                        cddc.setUse(new Coding("http://snomed.info/sct", "900000000000013009", 
                                                "Synonym (core metadata concept)"));
                                    }
                                }
                            }
                        }
                        // Add hierarchy-related fields
                        for(Node<OWLClass> parent : reasoner.getSuperClasses(owlClass, true)) {
                            for(OWLClass ent : parent.getEntities()) {
                                if(ent.isOWLThing() || ent.isOWLNothing()) continue;
                                ConceptPropertyComponent prop = cdc.addProperty();
                                prop.setCode("parent");
                                prop.setValue(new StringType(ent.getIRI().toString()));
                            }
                        }
                    }
        
                    @Override
                    public void visit(OWLObjectProperty property) {
                        // nothing to do - we do not index properties for now
                    }
        
                    @Override
                    public void visit(OWLDataProperty property) {
                        // nothing to do - we do not index data properties for now
                    }
        
                    @Override
                    public void visit(OWLNamedIndividual owlIndividual) {
                        // nothing to do - we do not index individuals for now
                    }
        
                    @Override
                    public void visit(OWLOntology ontology) {
                        for(OWLClass owlClass : ontology.getClassesInSignature(Imports.INCLUDED)) {
                            visit(owlClass);
                        }
                    }
        
                    @Override
                    public void visit(OWLDatatype datatype) {
                        // nothing to do - we do not index data types for now
                    }
        
                    @Override
                    public void visit(OWLAnnotationProperty property) {
                        // TODO need to deal with these to produce concept maps
                    }
                });

                JsonParser jp = (JsonParser) fhirContext.newJsonParser();
                return new ResponseEntity<String>(jp.encodeResourceToString(cs), HttpStatus.OK);
                
            } else {
                return new ResponseEntity<String>("No file was uploaded", HttpStatus.BAD_REQUEST);
            }
        } catch(IOException e) {
            return new ResponseEntity<String>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
}
