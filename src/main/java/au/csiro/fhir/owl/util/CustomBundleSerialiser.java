package au.csiro.fhir.owl.util;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import org.hl7.fhir.dstu3.model.Bundle;

public class CustomBundleSerialiser implements JsonSerializer<Bundle> {

  private FhirContext ctx;
  
  public CustomBundleSerialiser(FhirContext ctx) {
    this.ctx = ctx;
  }
  
  @Override
  public JsonElement serialize(Bundle src, Type typeOfSrc, JsonSerializationContext context) {
    final String jsonString = ctx.newJsonParser().encodeResourceToString(src);
    final Gson gson = new Gson();
    return gson.fromJson(jsonString, JsonElement.class);
  }

}
