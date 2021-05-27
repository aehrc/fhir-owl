/*
  Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to
  license terms and conditions.
 */
package au.csiro.fhir.owl;

import ca.uhn.fhir.context.FhirContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import uk.ac.manchester.cs.jfact.JFactFactory;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FhirOwlService.
 * 
 * @author Alejandro Metke Jimenez
 *
 */
public class FhirOwlServiceTest {

  private static final Log log = LogFactory.getLog(FhirOwlServiceTest.class);

  /**
   * Tests the creation of a code system from an OWL ontology in the DL profile.
   */
  @Test
  public void testCreateCodeSystemDl() throws OWLOntologyCreationException {
    FhirOwlService fos = new FhirOwlService();
    FhirContext ctx = FhirContext.forR4();
    fos.setCtx(ctx);

    // Load pizza ontology
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    File input = new File("src/test/resources/pizza.owl");
    log.info("Loading pizza ontology from " + input.getAbsolutePath());
    final OWLOntology rootOnt = manager.loadOntologyFromOntologyDocument(input);

    log.info("Classifying ontology");
    OWLReasonerFactory reasonerFactory = new JFactFactory();
    OWLReasoner reasoner = reasonerFactory.createReasoner(rootOnt);
    reasoner.precomputeInferences();

    log.info("Creating code system");
    CodeSystemProperties csp = new CodeSystemProperties();
    csp.setReasoner("jfact");
    ConceptProperties cp = new ConceptProperties();
    CodeSystem cs = fos.createCodeSystem(
      rootOnt,
      manager.getOWLDataFactory(),
      reasoner,
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptyMap(),
      csp,
      cp
    );

    //System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(cs));

    // Make sure Thing and TopObjectProperty are present, and TopDataProperty is absent
    assertNotNull(getConcept("Thing", cs));
    assertNotNull(getConcept("topObjectProperty", cs));
    assertNull(getConcept("topDataProperty", cs));

    // Check IceCream is not present (it is equivalent to Nothing)
    assertNull(getConcept("IceCream", cs));

    // Check this class hierarchy: PrawnsTopping -> SeafoodTopping -> PizzaTopping -> Food -> DomainThing -> Thing
    CodeSystem.ConceptDefinitionComponent prawnsTopping = getConcept("PrawnsTopping", cs);
    assertNotNull(prawnsTopping);
    CodeSystem.ConceptDefinitionComponent fishTopping = getConcept("FishTopping", cs);
    assertNotNull(fishTopping);
    CodeSystem.ConceptDefinitionComponent pizzaTopping = getConcept("PizzaTopping", cs);
    assertNotNull(pizzaTopping);
    CodeSystem.ConceptDefinitionComponent food = getConcept("Food", cs);
    assertNotNull(food);
    CodeSystem.ConceptDefinitionComponent domainConcept = getConcept("DomainConcept", cs);
    assertNotNull(domainConcept);
    CodeSystem.ConceptDefinitionComponent thing = getConcept("Thing", cs);
    assertNotNull(thing);

    assertTrue(isDirectParent(prawnsTopping, fishTopping));
    assertTrue(isDirectParent(fishTopping, pizzaTopping));
    assertTrue(isDirectParent(pizzaTopping, food));
    assertTrue(isDirectParent(food, domainConcept));
    assertTrue(isDirectParent(domainConcept, thing));

    // Check this object property hierarchy: isToppingOf -> isIngredientOf -> topObjectProperty
    CodeSystem.ConceptDefinitionComponent isToppingOf = getConcept("isToppingOf", cs);
    assertNotNull(isToppingOf);
    CodeSystem.ConceptDefinitionComponent isIngredientOf = getConcept("isIngredientOf", cs);
    assertNotNull(isIngredientOf);
    CodeSystem.ConceptDefinitionComponent topObjectProperty = getConcept("topObjectProperty", cs);
    assertNotNull(topObjectProperty);

    assertTrue(isDirectParent(isToppingOf, isIngredientOf));
    assertTrue(isDirectParent(isIngredientOf, topObjectProperty));
  }

  /**
   * Tests the creation of a code system from an OWL ontology in the EL profile.
   */
  @Test
  public void testCreateCodeSystemEl() throws OWLOntologyCreationException {
    FhirOwlService fos = new FhirOwlService();
    FhirContext ctx = FhirContext.forR4();
    fos.setCtx(ctx);

    // Load DUO ontology
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    File input = new File("src/test/resources/duo.owl");
    log.info("Loading DUO ontology from " + input.getAbsolutePath());
    final OWLOntology rootOnt = manager.loadOntologyFromOntologyDocument(input);

    log.info("Classifying ontology");
    OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
    OWLReasoner reasoner = reasonerFactory.createReasoner(rootOnt);
    reasoner.precomputeInferences();

    log.info("Creating code system");
    CodeSystemProperties csp = new CodeSystemProperties();
    csp.setReasoner("elk");
    csp.setUseFhirExtension(true);
    csp.setDateRegex("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})");
    ConceptProperties cp = new ConceptProperties();
    CodeSystem cs = fos.createCodeSystem(
      rootOnt,
      manager.getOWLDataFactory(),
      reasoner,
      Collections.emptySet(),
      fos.calculateIrisInMain(Collections.emptySet(), rootOnt),
      Collections.emptyMap(),
      csp,
      cp
    );

    assertEquals("http://purl.obolibrary.org/obo/duo.fhir", cs.getUrl());
    assertEquals("http://purl.obolibrary.org/obo/duo.fhir?vs", cs.getValueSet());
    assertEquals("20210223", cs.getVersion());

    // Make sure Thing is present, and TopObjectProperty and TopDataProperty are absent
    assertNotNull(getConcept("http://www.w3.org/2002/07/owl#Thing", cs));
    assertNull(getConcept("http://www.w3.org/2002/07/owl#topObjectProperty", cs));
    assertNull(getConcept("http://www.w3.org/2002/07/owl#topDataProperty", cs));

    //System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(cs));

    // Check this class hierarchy: DUO_0000007 -> DUO_0000006 -> DUO_0000042 -> DUO_0000001
    CodeSystem.ConceptDefinitionComponent diseaseSpecificResearch = getConcept("DUO_0000007", cs);
    assertNotNull(diseaseSpecificResearch);
    CodeSystem.ConceptDefinitionComponent healthOrMedicalOrBiomedicalResearch = getConcept("DUO_0000006", cs);
    assertNotNull(healthOrMedicalOrBiomedicalResearch);
    CodeSystem.ConceptDefinitionComponent generalResearchUse = getConcept("DUO_0000042", cs);
    assertNotNull(generalResearchUse);
    CodeSystem.ConceptDefinitionComponent dataUsePermission = getConcept("DUO_0000001", cs);
    assertNotNull(dataUsePermission);

    assertTrue(isDirectParent(diseaseSpecificResearch, healthOrMedicalOrBiomedicalResearch));
    assertTrue(isDirectParent(healthOrMedicalOrBiomedicalResearch, generalResearchUse));
    assertTrue(isDirectParent(generalResearchUse, dataUsePermission));
  }

  private boolean isDirectParent(CodeSystem.ConceptDefinitionComponent child,
                                 CodeSystem.ConceptDefinitionComponent parent) {
    return !child.getProperty().stream()
      .filter(p -> p.getCode().equals("parent") && p.getValueCodeType().getCode().equals(parent.getCode()))
      .collect(Collectors.toSet())
      .isEmpty();
  }

  private CodeSystem.ConceptDefinitionComponent getConcept(String code, CodeSystem cs) {
    Set<CodeSystem.ConceptDefinitionComponent> res =
      cs.getConcept().stream().filter(c -> c.getCode().equals(code)).collect(Collectors.toSet());
    if (res.isEmpty()) {
      return null;
    } else if(res.size() > 1) {
      fail("Found duplicate code " + code + " in code system " + cs.getName());
      return null;
    } else {
      return res.iterator().next();
    }
  }
  
}
