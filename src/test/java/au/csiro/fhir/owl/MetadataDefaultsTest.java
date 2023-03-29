/*
  Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to
  license terms and conditions.
 */
package au.csiro.fhir.owl;


import static au.csiro.fhir.owl.util.ArgConstants.DEPRECATED;
import static au.csiro.fhir.owl.util.ArgConstants.IMPORTED;
import static au.csiro.fhir.owl.util.ArgConstants.INPUT_FILE;
import static au.csiro.fhir.owl.util.ArgConstants.INPUT_FLAG;
import static au.csiro.fhir.owl.util.ArgConstants.OUTPUT_FILE;
import static au.csiro.fhir.owl.util.ArgConstants.OUTPUT_FLAG;
import static au.csiro.fhir.owl.util.ArgConstants.PARENT;
import static au.csiro.fhir.owl.util.ArgConstants.ROOT;
import static au.csiro.fhir.owl.util.ArgConstants.TEST_FLAG;

import au.csiro.fhir.owl.util.FilterUtil;
import au.csiro.fhir.owl.util.OutputFileManager;
import java.io.FileNotFoundException;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;


/**
 * E2E tests for fhir-owl that validate the default CodeSystem metadata values when no input flags are specified.
 */

@SpringBootTest(args = {
    TEST_FLAG,
    INPUT_FLAG, INPUT_FILE, 
    OUTPUT_FLAG, OUTPUT_FILE
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetadataDefaultsTest {

  private CodeSystem codeSystem;
  
  @BeforeAll
  private void parseCodeSystemFromFile() throws FileNotFoundException {
    codeSystem = OutputFileManager.getCodeSystemFromFile(OUTPUT_FILE);
  }
  
  @Test
  public void testDefaultMetadataFieldValues() {
    Assertions.assertEquals(codeSystem.getHierarchyMeaning(), CodeSystem.CodeSystemHierarchyMeaning.ISA);
    Assertions.assertTrue(codeSystem.getJurisdiction().isEmpty());
    Assertions.assertFalse(codeSystem.hasContact());
  }
  
  @Test
  public void testDefaultFilters() {
    Assertions.assertEquals(4, codeSystem.getFilter().size());
    
    FilterUtil.codeSystemFilterExists(codeSystem, DEPRECATED);
    FilterUtil.codeSystemFilterExists(codeSystem, IMPORTED);
    FilterUtil.codeSystemFilterExists(codeSystem, ROOT);
    FilterUtil.codeSystemFilterExists(codeSystem, PARENT);
  }
  
  @AfterAll
  private void cleanup(){
    OutputFileManager.deleteFileIfExists(OUTPUT_FILE);
  }
  
}
