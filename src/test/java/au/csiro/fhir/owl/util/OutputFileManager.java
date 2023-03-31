package au.csiro.fhir.owl.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import org.hl7.fhir.r4.model.CodeSystem;

public class OutputFileManager {
    
    public static boolean deleteFileIfExists(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }
    
    public static CodeSystem getCodeSystemFromFile(String filePath) throws FileNotFoundException {
        FhirContext ctx = FhirContext.forR4();
        File initialFile = new File(filePath);
        InputStream inputStream = new FileInputStream(initialFile);
        IParser parser = ctx.newJsonParser();
        return parser.parseResource(CodeSystem.class, inputStream);
    }
}
