package au.csiro.fhir.owl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r4.model.CodeSystem.PropertyComponent;
import org.hl7.fhir.r4.model.CodeSystem.PropertyType;
import org.hl7.fhir.r4.model.CodeType;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import uk.ac.manchester.cs.owl.owlapi.OWLObjectSomeValuesFromImpl;

public class ObjectPropertyProcessor {
    
    /**
     * 
     * @param ont Source Ontology
     * @return a list of CodeSystem Properties based on the Object Properties defined in the Source Ontology
     */
    public List<PropertyComponent> extractCodeSystemProperties(OWLOntology ont) {
        return ont.getObjectPropertiesInSignature().stream()
            .map(this::createFhirProperty)
            .collect(Collectors.toList());
    }
    
    /**
     * 
     * @param objectProperty OWL Object Property
     * @return CodeSystem Property
     */
    private PropertyComponent createFhirProperty(OWLObjectProperty objectProperty) {
        PropertyComponent propertyComponent = new PropertyComponent();
        propertyComponent.setCode(objectProperty.getIRI().getShortForm());
        propertyComponent.setType(PropertyType.CODE);
        propertyComponent.setDescription("Object Property");
        return propertyComponent;
    }
    
    /**
     * 
     * @param owlEntity Input Term
     * @param ont Source Ontology
     * @return List of FHIR Code Properties extracted from the Object Properties defined for the input term from the OWL Source
     */
    public List<ConceptPropertyComponent> extractFhirCodeProperties(OWLEntity owlEntity, OWLOntology ont) {
        if(owlEntity.isOWLClass()) {
            return ont.getSubClassAxiomsForSubClass(owlEntity.asOWLClass())
                .stream()
                .filter(owlSubClassOfAxiom -> owlSubClassOfAxiom.getSuperClass().getClass() == OWLObjectSomeValuesFromImpl.class)
                .map(this::createFhirCodeProperty)
                .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }
    
    /**
     * 
     * @param owlSubClassOfAxiom Object Property from Source Ontology
     * @return CodeSystem code property
     */
    private ConceptPropertyComponent createFhirCodeProperty(OWLSubClassOfAxiom owlSubClassOfAxiom) {
        ConceptPropertyComponent conceptPropertyComponent = new ConceptPropertyComponent();
        OWLObjectSomeValuesFrom owlObjectSomeValuesFrom = (OWLObjectSomeValuesFrom) owlSubClassOfAxiom.getSuperClass();
        //TODO: handle complex Object Properties with subproperties
        
        final String objectPropertyPredicateIri = owlObjectSomeValuesFrom.getProperty().getNamedProperty().getIRI().getShortForm();
        conceptPropertyComponent.setCode(objectPropertyPredicateIri);
        owlSubClassOfAxiom.getSuperClass();
        
        final String objectPropertyObjectIri = ((OWLClass) owlObjectSomeValuesFrom.getFiller()).getIRI().getShortForm();
        CodeType code = new CodeType(objectPropertyObjectIri);
        conceptPropertyComponent.setValue(code);
        return conceptPropertyComponent;
    }
}
