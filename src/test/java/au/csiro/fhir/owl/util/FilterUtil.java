package au.csiro.fhir.owl.util;

import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.Assertions;

public class FilterUtil {
    public static void codeSystemFilterExists(CodeSystem codeSystem, String filterValue) {
        CodeSystem.CodeSystemFilterComponent filter = codeSystem.getFilter().stream()
            .filter(x -> x.getCode().equals(filterValue))
            .collect(Collectors.toList())
            .get(0);
        
        Assertions.assertEquals(filterValue, filter.getCode());
        Assertions.assertEquals(1, filter.getOperator().size());
        Assertions.assertTrue(filter.hasOperator(CodeSystem.FilterOperator.EQUAL));
    }
}
