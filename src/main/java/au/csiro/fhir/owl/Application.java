package au.csiro.fhir.owl;

import static java.lang.System.exit;

import au.csiro.fhir.owl.util.CustomBundleSerialiser;
import ca.uhn.fhir.context.FhirContext;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.PrintWriter;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application implements CommandLineRunner {
  
  @Autowired
  private FhirOwlService fhirOwlService;
  
  /**
   * Created here as a bean because it is expensive to create and we only need one instance that can
   * be shared.
   *
   * @return
   */
  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }
  
  /**
   * Returns a GSON bean, with a custom serialiser for {@link Bundle}s.
   * 
   * @return
   */
  @Bean
  public Gson gson() {
    return new GsonBuilder()
        .registerTypeAdapter(Bundle.class, new CustomBundleSerialiser(fhirContext()))
        .create();
  }
  
  /**
   * Main method.
   * 
   * @param args Arguments.
   */
  public static void main(String[] args) {
    trustEverything();
    
    SpringApplication app = new SpringApplication(Application.class);
    app.setBannerMode(Banner.Mode.OFF);
    app.run(args);

  }
  
  private static void trustEverything() {
    // Create a trust manager that does not validate certificate chains
    TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
      public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return null;
      }

      public void checkClientTrusted(X509Certificate[] certs, String authType) {}

      public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    } };

    // Install the all-trusting trust manager
    try {
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

      // Create all-trusting host name verifier
      HostnameVerifier allHostsValid = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      };

      // Install the all-trusting host verifier
      HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }
  
  private static void printUsage(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    final PrintWriter writer = new PrintWriter(System.out);
    formatter.printUsage(writer, 80, "FHIR OWL", options);
    writer.flush();
  }

  @Override
  public void run(String... args) throws Exception {
    Options options = new Options();
    
    options.addOption(new Option("help", "Print this message."));
    options.addOption(
        Option.builder("i")
        .required(true)
        .hasArg(true)
        .longOpt("input")
        .desc("Specify the input OWL file.")
        .build()
    );
    options.addOption(
        Option.builder("o")
        .required(true)
        .hasArg(true)
        .longOpt("output")
        .desc("Specify the output JSON file.")
        .build()
    );
    options.addOption("includeDeprecated", false, "Include all classes, including deprecated "
        + "ones.");
    
    options.addOption("id", true, "The technical id of the code system.");
    options.addOption("language", true, "The language of the content. This is a code supplied by "
        + "the user from the FHIR Common Languages value set.");
    options.addOption("url", true, "The code system url. If this option is not specified then the"
        + " ontology's IRI will be used. If the ontology has no IRI then the transformation "
        + "fails.");
    options.addOption("identifier", true, "Comma-separated list of additional business "
        + "identifiers. Each business identifer has the format [system]|[value].");
    options.addOption("v", "version", true, "The code system version. If this option is not "
        + "specified then the ontology's version will be used. If the ontology has no version then "
        + "the version is set to 'NA'.");
    options.addOption("n", "name", true, "The code system name. If this option is not " 
        + "specified then the value will be taken from the OWL file based on the nameProp " 
        + "option. Otherwise the ontology's IRI is used.");
    options.addOption("nameProp", "nameProp", true, "OWL annotation property that contains the "
        + "name of the code system.");
    options.addOption("t", "title", true, "The code system title.");
    options.addOption("status", true, "Code system status. Valid values are draft, active, "
        + "retired and unknown");
    options.addOption("experimental", false, "Indicates if the code system is for testing "
        + "purposes, not real usage.");
    options.addOption("date", true, "The published date. Valid formats are: YYYY, YYYY-MM, "
        + "YYYY-MM-DD and YYYY-MM-DDThh:mm:ss+zz:zz.");
    options.addOption("publisher", true, "The publisher of the code system. If this option is not "
        + "specified then the value will be taken from the OWL file based on the publisherProp "
        + "option.");
    options.addOption("publisherProp", true, "Comma-separated list of OWL annotation properties "
        + "that contain the code system publisher.");
    options.addOption("contact", true, "Comma-separated list of contact details for the "
        + "publisher. Each contact detail has the format [name|system|value], where system has "
        + "the following possible values: phone, fax, email, pager, url, sms or other.");
    options.addOption("description", true, "The description of the code system. If this option "
        + "is not specified then the value will be taken from the OWL file based on the "
        + "descriptionProp option.");
    options.addOption("descriptionProp", true, "Comma-separated list of OWL annotation "
        + "properties that contain the code system description.");
    options.addOption("purpose", true, "Explanation of why this code system is needed.");
    options.addOption("copyright", true, "A copyright statement about the code system.");
    options.addOption("valueset", true, "The value set that represents the entire code system.");
    options.addOption("compositional", false, "Flag to indicate that the code system defines a "
        + "post-coordination grammar.");
    options.addOption("noVersionNeeded", false, "Flag to indicate that the code system commits to "
        + "concept permanence across versions.");
    options.addOption("content", true, "The extent of the content in this resource. Defaults to "
        + "complete but can be set to other values. The actual value does not affect the output "
        + "of the transformation.");
    
    options.addOption("c", "code", true, "OWL annotation property for each class that maps to a "
        + "concept code.");
    options.addOption("d", "display", true, "OWL annotation property for each class that maps to "
        + "a display.");
    options.addOption("definition", true, "OWL annotation property for each class that maps to "
        + "a defintion.");
    options.addOption("s", "synonyms", true, "Comma-separated list of OWL annotation properties "
        + "for each class that map to synonyms.");
    options.addOption("mainNs", true, "Comma-separated list of namespaces that constitute the "
        + "main ontology.");
    options.addOption("codeReplace", true, "Two strings separated by a comma. Replaces the first"
        + " string with the second string in all local codes.");
    
    CommandLineParser parser = new DefaultParser();
    try {
      // parse the command line arguments
      CommandLine line = parser.parse(options, args);
      
      CodeSystemProperties csp = loadCodeSystemProperties(line);
      ConceptProperties cp = loadConceptProperties(line);
      
      try {
        if (line.hasOption("mainNs")) {
          fhirOwlService.transform(csp, cp, new HashSet<String>(
              Arrays.asList(line.getOptionValue("mainNs").split("[,]"))));
        } else {
          fhirOwlService.transform(csp, cp);
        }
      } catch (Throwable t) {
        System.out.println("There was a problem transforming the OWL file into FHIR: " 
            + t.getLocalizedMessage());
        t.printStackTrace();
      }
      
    } catch (ParseException exp) {
      // oops, something went wrong
      System.out.println(exp.getMessage());
      printUsage(options);
    }
    
    exit(0);
  }
  
  private ConceptProperties loadConceptProperties(CommandLine line) {
    ConceptProperties res = new ConceptProperties();
    
    String val = line.getOptionValue('c');
    if (val != null) {
      res.setCode(val);
    }
    
    val = line.getOptionValue('d');
    if (val != null) {
      res.setDisplay(val);
    }
    
    val = line.getOptionValue("definition");
    if (val != null) {
      res.setDefinition(val);
    }
    
    val = line.getOptionValue('s');
    if (val != null) {
      res.setDesignations(val);
    }
    
    val = line.getOptionValue("codeReplace");
    if (val != null) {
      String[] parts = val.split("[,]");
      if (parts.length != 2) {
        throw new InvalidPropertyException("Invalid codeReplace value '" + val + "'. This should "
            + "have two strings separated by a comma. Commas are forbidden in both strings.");
      }
      res.setStringToReplaceInCodes(parts[0]);
      res.setReplacementStringInCodes(parts[1]);
    }
    
    return res;
  }
  
  private CodeSystemProperties loadCodeSystemProperties(CommandLine line) {
    CodeSystemProperties res = new CodeSystemProperties();
    res.setInput(new File(line.getOptionValue("i")));
    res.setOutput(new File(line.getOptionValue("o")));
    res.setIncludeDeprecated(line.hasOption("includeDeprecated"));
    res.setExperimental(line.hasOption("experimental"));
    res.setCompositional(line.hasOption("compositional"));
    res.setVersionNeeded(!line.hasOption("noVersionNeeded"));
    
    String val = line.getOptionValue("id");
    if (val != null) {
      res.setId(val);
    }
    
    val = line.getOptionValue("language");
    if (val != null) {
      res.setLanguage(val);
    }
    
    val = line.getOptionValue("url");
    if (val != null) {
      res.setUrl(val);
    }
    
    val = line.getOptionValue("identifier");
    if (val != null) {
      res.setIdentifiers(val);
    }
    
    val = line.getOptionValue("version");
    if (val != null) {
      res.setVersion(val);
    }
    
    val = line.getOptionValue("name");
    if (val != null) {
      res.setName(val);
    }
    
    val = line.getOptionValue("nameProp");
    if (val != null) {
      res.setNameProp(val);
    }
    
    val = line.getOptionValue("title");
    if (val != null) {
      res.setTitle(val);
    }
    
    val = line.getOptionValue("status");
    if (val != null) {
      res.setStatus(val);
    }
    
    val = line.getOptionValue("date");
    if (val != null) {
      res.setDate(val);
    }
    
    val = line.getOptionValue("publisher");
    if (val != null) {
      res.setPublisher(val);
    }
    
    val = line.getOptionValue("publisherProp");
    if (val != null) {
      res.setPublisherProps(val);
    }
    
    val = line.getOptionValue("contact");
    if (val != null) {
      res.setContacts(val);
    }
    
    val = line.getOptionValue("description");
    if (val != null) {
      res.setDescription(val);
    }
    
    val = line.getOptionValue("descriptionProp");
    if (val != null) {
      res.setDescriptionProps(val);
    }

    val = line.getOptionValue("purpose");
    if (val != null) {
      res.setPurpose(val);
    }
    
    val = line.getOptionValue("copyright");
    if (val != null) {
      res.setCopyright(val);
    }
    
    val = line.getOptionValue("valueset");
    if (val != null) {
      res.setValueSet(val);
    }
    
    val = line.getOptionValue("content");
    if (val != null) {
      res.setContent(val);
    }
    
    return res;
  }

}
