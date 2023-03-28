/*
  Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to
  license terms and conditions.
 */
package au.csiro.fhir.owl;


import static au.csiro.fhir.owl.util.ArgConstants.CONTACT_EXAMPLE_WITH_EMAIL;
import static au.csiro.fhir.owl.util.ArgConstants.CONTACT_EXAMPLE_WITH_PHONE;
import static au.csiro.fhir.owl.util.ArgConstants.CONTACT_FLAG;
import static au.csiro.fhir.owl.util.ArgConstants.EMAIL;
import static au.csiro.fhir.owl.util.ArgConstants.EXAMPLE_EMAIL;
import static au.csiro.fhir.owl.util.ArgConstants.EXAMPLE_NAME_1;
import static au.csiro.fhir.owl.util.ArgConstants.EXAMPLE_NAME_2;
import static au.csiro.fhir.owl.util.ArgConstants.EXAMPLE_PHONE;
import static au.csiro.fhir.owl.util.ArgConstants.HEIRARCHY_MEANING_FLAG;
import static au.csiro.fhir.owl.util.ArgConstants.HEIRARCHY_MEANING_GROUPED_BY;
import static au.csiro.fhir.owl.util.ArgConstants.INPUT_FILE;
import static au.csiro.fhir.owl.util.ArgConstants.INPUT_FLAG;
import static au.csiro.fhir.owl.util.ArgConstants.JURISDICTION_EXAMPLE_US_ARG;
import static au.csiro.fhir.owl.util.ArgConstants.JURISDICTION_FLAG;
import static au.csiro.fhir.owl.util.ArgConstants.JURISDICTION_URN;
import static au.csiro.fhir.owl.util.ArgConstants.OUTPUT_FILE;
import static au.csiro.fhir.owl.util.ArgConstants.OUTPUT_FLAG;
import static au.csiro.fhir.owl.util.ArgConstants.PHONE;
import static au.csiro.fhir.owl.util.ArgConstants.TEST_FLAG;
import static au.csiro.fhir.owl.util.ArgConstants.UNITED_STATES_OF_AMERICA;
import static au.csiro.fhir.owl.util.ArgConstants.US;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ContactDetail;
import org.hl7.fhir.r4.model.ContactPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;


/**
 * E2E tests for fhir-owl that validate that CodeSystem metadata values are properly represented when given as input.
 */

@SpringBootTest(args = {
    TEST_FLAG,
    INPUT_FLAG, INPUT_FILE, 
    OUTPUT_FLAG, OUTPUT_FILE,
    HEIRARCHY_MEANING_FLAG, HEIRARCHY_MEANING_GROUPED_BY,
    JURISDICTION_FLAG, JURISDICTION_EXAMPLE_US_ARG,
    CONTACT_FLAG, CONTACT_EXAMPLE_WITH_EMAIL+","+CONTACT_EXAMPLE_WITH_PHONE
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetadataFromInputTest {

  private CodeSystem codeSystem;
  
  @BeforeAll
  private void parseCodeSystemFromFile() throws FileNotFoundException {
    FhirContext ctx = FhirContext.forR4();
    File initialFile = new File(OUTPUT_FILE);
    InputStream inputStream = new FileInputStream(initialFile);
    IParser parser = ctx.newJsonParser();
    codeSystem = parser.parseResource(CodeSystem.class, inputStream);
  }
  
  @Test
  public void testMetadateFromArgs() {
    Assertions.assertEquals(CodeSystem.CodeSystemHierarchyMeaning.GROUPEDBY, codeSystem.getHierarchyMeaning());
    Assertions.assertTrue(codeSystem.getJurisdiction().size()==1);
    Assertions.assertEquals(US, codeSystem.getJurisdiction().get(0).getCodingFirstRep().getCode());
    Assertions.assertEquals(UNITED_STATES_OF_AMERICA, codeSystem.getJurisdiction().get(0).getCodingFirstRep().getDisplay());
    Assertions.assertEquals(JURISDICTION_URN, codeSystem.getJurisdiction().get(0).getCodingFirstRep().getSystem());
  }
  
  @Test
  public void testContactsFromArgs() {
    Assertions.assertTrue(codeSystem.getContact().size() == 2);
  
    ContactDetail contactDetail1 = codeSystem.getContact().stream()
        .filter(contactDetail -> contactDetail.getName().equals(EXAMPLE_NAME_1))
        .collect(Collectors.toList())
        .get(0);
    Assertions.assertEquals(EXAMPLE_NAME_1, contactDetail1.getName());
    Assertions.assertEquals(ContactPoint.ContactPointSystem.fromCode(EMAIL), contactDetail1.getTelecomFirstRep().getSystem());
    Assertions.assertEquals(EXAMPLE_EMAIL, contactDetail1.getTelecomFirstRep().getValue());
  
    ContactDetail contactDetail2 = codeSystem.getContact().stream()
        .filter(contactDetail -> contactDetail.getName().equals(EXAMPLE_NAME_2))
        .collect(Collectors.toList())
        .get(0);
    Assertions.assertEquals(EXAMPLE_NAME_2, contactDetail1.getName());
    Assertions.assertEquals(ContactPoint.ContactPointSystem.fromCode(PHONE), contactDetail1.getTelecomFirstRep().getSystem());
    Assertions.assertEquals(EXAMPLE_PHONE, contactDetail1.getTelecomFirstRep().getValue());
  }
}
