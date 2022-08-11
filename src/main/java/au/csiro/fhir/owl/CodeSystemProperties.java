/*
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com). All rights reserved. Use is subject to 
 * license terms and conditions.
 */

package au.csiro.fhir.owl;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactDetail;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Identifier;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;

/**
 * The configuration properties to populate the code system properties based on parameters and 
 * possibly the contents of the source OWL file.
 * 
 * @author Alejandro Metke
 *
 */
public class CodeSystemProperties extends OwlProperties {
  private File input;
  private File output;
  private boolean includeDeprecated = false;
  
  private String id = null;
  private String language = null;
  private String url = null;
  private final List<Identifier> identifiers = new ArrayList<>();
  private String version = null;
  private String name = null;
  private String nameProp = null;
  private String title = null;
  private String status = "draft";
  private boolean experimental = false;
  private final Calendar date = null;
  private String publisher = null;
  private final List<String> publisherProps = new ArrayList<>();
  private final List<ContactDetail> contacts = new ArrayList<>();
  private final List<CodeableConcept> jurisdictions = new ArrayList<>();
  private String description = null;
  private final List<String> descriptionProps = new ArrayList<>();
  private String purpose = null;
  private String heirarchyMeaning = null;
  private String copyright = null;
  private String valueSet = null;
  private boolean compositional = false;
  private boolean versionNeeded = false;
  private String content = "complete";
  private String reasoner = "elk";
  private boolean useFhirExtension = false;
  private String dateRegex = null;

  private final Set<String> reasonerValues = new HashSet<>(Arrays.asList("elk", "jfact"));

  private final Set<String> contentValues = new HashSet<>(Arrays.asList(
    "not-present", "example", "fragment", "complete", "supplement")
  );
  
  private final Set<String> statusValues = new HashSet<>(Arrays.asList("draft", "active", "retired", "unknown"));
  
  private final Set<String> contactSystemValues = new HashSet<>(Arrays.asList(
    "phone", "fax", "email", "pager", "url", "sms", "other")
  );

  private final List<String> defaultPublisherProps = Collections.singletonList(DC_PUBLISHER);
  
  private final List<String> defaultDescriptionProps = Arrays.asList(DC_SUBJECT, RDFS_COMMENT);

  /**
   * Parses the arguments of the identifiers parameter.
   * 
   * @param idents The string that contains the identifiers.
   * @throws InvalidPropertyException If the string is not well formed.
   */
  public void setIdentifiers(String idents) {
    identifiers.clear();
    
    if (idents == null) {
      return;
    }
    
    final String[] parts = idents.split("[,]");
    for (String part : parts) {
      String[] innerPart = part.split("[|]");
      if (innerPart.length != 2) {
        throw new InvalidPropertyException("Inavlid identifier argument: " + part 
            + ". Valid format is [system]|[value].");
      }
      Identifier i = new Identifier();
      if (innerPart[0] != null && !innerPart[0].isEmpty()) {
        i.setSystem(innerPart[0]);
      }
      if (innerPart[1] == null || innerPart[1].isEmpty()) {
        throw new InvalidPropertyException("Inavlid identifier argument: " + part 
            + ". Valid format is [system]|[value] and value cannot be empty.");
      }
      i.setValue(innerPart[1]);
      identifiers.add(i);
    }
  }


  /**
   * Sets and validates the <i>status</i> property.
   * 
   * @param status The status property.
   * @throws InvalidPropertyException If the string is not a valid status.
   */
  public void setStatus(String status) {
    if (!statusValues.contains(status)) {
      throw new InvalidPropertyException("Invalid status value '" + status 
          + "'. Valid values are: " + statusValues);
    }
    this.status = status;
  }

  /**
   * Parses and sets the date.
   * 
   * @param dt The date string.
   * @throws InvalidPropertyException If the date string is not well formed.
   */
  public void setDate(String dt) {
    if (dt == null || dt.isEmpty()) {
      return;
    }
    
    Calendar cal = Calendar.getInstance();
    // Test supported formats
    final SimpleDateFormat yyyy = new SimpleDateFormat("yyyy");
    try {
      cal.setTime(yyyy.parse(dt));
      return;
    } catch (ParseException e) {
      // Do nothing
    }
    
    final SimpleDateFormat yyyymm = new SimpleDateFormat("yyyy-MM");
    try {
      cal.setTime(yyyymm.parse(dt));
      return;
    } catch (ParseException e) {
      // Do nothing
    }
    
    final SimpleDateFormat yyyymmdd = new SimpleDateFormat("yyyy-MM-dd");
    try {
      cal.setTime(yyyymmdd.parse(dt));
      return;
    } catch (ParseException e) {
      // Do nothing
    }
    
    final SimpleDateFormat yyyymmddhhmmss = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
    try {
      cal.setTime(yyyymmddhhmmss.parse(dt));
      return;
    } catch (ParseException e) {
      // Do nothing
    }
    
    final SimpleDateFormat yyyymmddhhmmsssss = 
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    try {
      cal.setTime(yyyymmddhhmmsssss.parse(dt));
      return;
    } catch (ParseException e) {
      // Do nothing
    }
    
    throw new InvalidPropertyException("Invalid date value '" + dt + "'. Valid formats are: "
        + "YYYY, YYYY-MM, YYYY-MM-DD and YYYY-MM-DDThh:mm:ss+zz:zz.");
    
  }

  /**
   * Parses the arguments of the contacts parameter.
   * 
   * @param cts The string that contains the contacts.
   * @throws InvalidPropertyException If the string is not well formed.
   */
  public void setContacts(String cts) {
    for (String ct : cts.split("[,]")) {
      String[] parts = ct.split("[|]");
      if (parts.length != 3) {
        throw new InvalidPropertyException("Invalid contact '" + ct 
            + "'. Valid format is [name|system|value].");
      }
      if (!contactSystemValues.contains(parts[1])) {
        throw new InvalidPropertyException("Invalid system contact '" + parts[1] 
            + "'. Valid values are: " + contactSystemValues);
      }
    }
  }
  
  /**
   * Parses the arguments of the jurisdiction parameter.
   *
   * @param jds The string that contains the jurisdiction information.
   * @throws InvalidPropertyException If the string is not well formed.
   */
  public void setJurisdiction(String jds) {
    for (String jd : jds.split("[,]")) {
      String[] parts = jd.split("[|]");
      if (parts.length != 3) {
        throw new InvalidPropertyException("Invalid jurisdiction '" + jd
                                               + "'. Valid format is [code|system|display] from https://hl7.org/fhir/valueset-jurisdiction.html.");
      }
      CodeableConcept jurisdiction = new CodeableConcept();
      jurisdiction.addCoding(new Coding(parts[1], parts[0], parts[2]));
      jurisdictions.add(jurisdiction);
    }
  }
  
  /**
   * Returns the jurisdictions property.
   *
   * @return the jurisdictions
   */
  public List<CodeableConcept> getJurisdictions() {
    return jurisdictions;
  }
  
  
  /**
   * Returns the content property.
   *
   * @return the content
   */
  public String getContent() {
    return content;
  }
  
  /**
   * Sets and validates the <i>content</i> property.
   * 
   * @param content The content property.
   * @throws InvalidPropertyException If the string is not a valid content.
   */
  public void setContent(String content) {
    if (!contentValues.contains(content)) {
      throw new InvalidPropertyException("Invalid content value '" + content 
          + "'. Valid values are: " + contentValues);
    }
    this.content = content;
  }

  /**
   * Returns the reasoner property.
   *
   * @return the reasoner
   */
  public String getReasoner() {
    return reasoner;
  }

  /**
   * Sets and validates the <i>reasoner</i> property.
   *
   * @param reasoner The reasoner property.
   * @throws InvalidPropertyException If the string is not a valid content.
   */
  public void setReasoner(String reasoner) {
    if (!reasonerValues.contains(reasoner)) {
      throw new InvalidPropertyException("Invalid content value '" + reasoner
        + "'. Valid values are: " + reasonerValues);
    }
    this.reasoner = reasoner;
  }
  
  /**
   * Returns the input file.
   * 
   * @return the input
   */
  public File getInput() {
    return input;
  }

  /**
   * Sets the input file.
   * 
   * @param input the input to set
   */
  public void setInput(File input) {
    this.input = input;
  }

  /**
   * Returns the output file.
   * 
   * @return the output
   */
  public File getOutput() {
    return output;
  }

  /**
   * Sets the output file.
   * 
   * @param output the output to set
   */
  public void setOutput(File output) {
    this.output = output;
  }

  /**
   * Indicates if deprecated concepts should be included.
   * 
   * @return the includeDeprecated
   */
  public boolean isIncludeDeprecated() {
    return includeDeprecated;
  }

  /**
   * Sets the flag that indicates if deprecated concepts should be included.
   * 
   * @param includeDeprecated the includeDeprecated to set
   */
  public void setIncludeDeprecated(boolean includeDeprecated) {
    this.includeDeprecated = includeDeprecated;
  }

  /**
   * Returns the technical id.
   * 
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the technical id.
   * 
   * @param id the id to set
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Returns the URL.
   * 
   * @return the url
   */
  public String getUrl() {
    return url;
  }

  /**
   * Sets the URL.
   * 
   * @param url the url to set
   */
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * Returns the version.
   * 
   * @return the version
   */
  public String getVersion() {
    return version;
  }

  /**
   * Sets the version.
   * 
   * @param version the version to set
   */
  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * Returns the name.
   * 
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name.
   * 
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the title.
   * 
   * @return the title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Sets the title.
   * 
   * @param title the title to set
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Returns the flag that indicates if the code system is experimental.
   * 
   * @return the experimental
   */
  public boolean isExperimental() {
    return experimental;
  }

  /**
   * Sets the flag that indicates if the code system is experimental.
   * 
   * @param experimental the experimental to set
   */
  public void setExperimental(boolean experimental) {
    this.experimental = experimental;
  }

  /**
   * Returns the publisher.
   * 
   * @return the publisher
   */
  public String getPublisher() {
    return publisher;
  }

  /**
   * Sets the publisher.
   * 
   * @param publisher the publisher to set
   */
  public void setPublisher(String publisher) {
    this.publisher = publisher;
  }

  /**
   * Returns the description.
   * 
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the description.
   * 
   * @param description the description to set
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Returns the purpose.
   * 
   * @return the purpose
   */
  public String getPurpose() {
    return purpose;
  }

  /**
   * Sets the purpose.
   * 
   * @param purpose the purpose to set
   */
  public void setPurpose(String purpose) {
    this.purpose = purpose;
  }
  
  /**
   * Returns the heirarchyMeaning.
   *
   * @return the heirarchyMeaning
   */
  public String getHeirarchyMeaning() {
    return heirarchyMeaning;
  }
  
  /**
   * Sets the heirarchyMeaning.
   *
   * @param heirarchyMeaning the heirarchyMeaning to set
   */
  public void setHeirarchyMeaning(String heirarchyMeaning) {
    this.heirarchyMeaning = heirarchyMeaning;
  }

  /**
   * Gets the copyright.
   * 
   * @return the copyright
   */
  public String getCopyright() {
    return copyright;
  }

  /**
   * Sets the copyright.
   * 
   * @param copyright the copyright to set
   */
  public void setCopyright(String copyright) {
    this.copyright = copyright;
  }

  /**
   * Returns the value set URL.
   * 
   * @return the valueSet
   */
  public String getValueSet() {
    return valueSet;
  }

  /**
   * Sets the value set URL.
   * 
   * @param valueSet the valueSet to set
   */
  public void setValueSet(String valueSet) {
    this.valueSet = valueSet;
  }

  /**
   * Returns the flag that indicates if the code system has a post-coordination synatx.
   * 
   * @return the compositional
   */
  public boolean isCompositional() {
    return compositional;
  }

  /**
   * Sets the flag that indicates if the code system has a post-coordination synatx.
   * 
   * @param compositional the compositional to set
   */
  public void setCompositional(boolean compositional) {
    this.compositional = compositional;
  }

  /**
   * Returns the flag that indicates if the code system has concept permanence.
   * 
   * @return the versionNeeded
   */
  public boolean isVersionNeeded() {
    return versionNeeded;
  }

  /**
   * Sets the flag that indicates if the code system has concept permanence.
   * 
   * @param versionNeeded the versionNeeded to set
   */
  public void setVersionNeeded(boolean versionNeeded) {
    this.versionNeeded = versionNeeded;
  }

  /**
   * Returns the additional business identifiers.
   * 
   * @return the identifiers
   */
  public List<Identifier> getIdentifiers() {
    return identifiers;
  }

  /**
   * Returns the status.
   * 
   * @return the status
   */
  public String getStatus() {
    return status;
  }

  /**
   * Returns the date.
   * 
   * @return the date
   */
  public Calendar getDate() {
    return date;
  }

  /**
   * Returns the contacts.
   * 
   * @return the contacts
   */
  public List<ContactDetail> getContacts() {
    return contacts;
  }

  /**
   * Returns the OWL annotation properties where the publisher of the code system can be found.
   * 
   *  @param factory The OWL data factory.
   * 
   * @return the publisherProps
   */
  public List<OWLAnnotationProperty> getPublisherProps(OWLDataFactory factory) {
    return loadProps(factory, this.publisherProps, defaultPublisherProps, null);
  }

  /**
   * Sets the OWL annotation properties where the publisher of the code system can be found.
   * 
   * @param props the publisherProps to set
   */
  public void setPublisherProps(String props) {
    publisherProps.clear();
    final List<String> args = Arrays.asList(props.split("[,]"));
    publisherProps.addAll(args);
  }

  /**
   * Returns the OWL annotation properties where the description of the code system can be found.
   * 
   * @param factory The OWL data factory.
   * 
   * @return the descriptionProps
   */
  public List<OWLAnnotationProperty> getDescriptionProps(OWLDataFactory factory) {
    return loadProps(factory, this.descriptionProps, defaultDescriptionProps, null);
  }

  /**
   * Sets the OWL annotation properties where the description of the code system can be found.
   * 
   * @param props the descriptionProps to set
   */
  public void setDescriptionProps(String props) {
    descriptionProps.clear();
    final List<String> args = Arrays.asList(props.split("[,]"));
    descriptionProps.addAll(args);
  }

  /**
   * Returns the language.
   * 
   * @return the language
   */
  public String getLanguage() {
    return language;
  }

  /**
   * Sets the language. Does not validate if the language code is valid.
   * 
   * @param language the language to set
   */
  public void setLanguage(String language) {
    this.language = language;
  }


  /**
   * Returns the OWL annotation property that contains the name of the ontology.
   * 
   * @return the nameProp
   */
  public OWLAnnotationProperty getNameProp(OWLDataFactory factory) {
    return loadProp(factory, nameProp, RDFS_LABEL, null);
  }


  /**
   * Sets the OWL annotation property that contains the name of the ontology.
   * 
   * @param nameProp the nameProp to set
   */
  public void setNameProp(String nameProp) {
    this.nameProp = nameProp;
  }

  /**
   * Returns the flag that indicates if the .owl extension in the IRI should be replaced with .fhir.
   *
   * @return the useFhirExtension
   */
  public boolean isUseFhirExtension() {
    return useFhirExtension;
  }

  /**
   * Sets the flag that indicates if the .owl extension in the IRI should be replaced with .fhir.
   *
   * @param useFhirExtension The flag.
   */
  public void setUseFhirExtension(boolean useFhirExtension) {
    this.useFhirExtension = useFhirExtension;
  }

  /**
   * Returns the regular expression used to extract the code system date.
   *
   * @return the date regex
   */
  public String getDateRegex() {
    return dateRegex;
  }

  /**
   * Sets the regular expression used to extract the code system date.
   *
   * @param dateRegex the dateRegex to set
   */
  public void setDateRegex(String dateRegex) {
    this.dateRegex = dateRegex;
  }
}
