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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.hl7.fhir.r4.model.Identifier;
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
    options.addOption(
        Option.builder("i")
        .required(true)
        .hasArg(true)
        .longOpt("input-file")
        .desc("The input OWL file.")
        .build()
    );
    options.addOption(
        Option.builder("o")
        .required(true)
        .hasArg(true)
        .longOpt("output-file")
        .desc("The output JSON file.")
        .build()
    );
    options.addOption("a", "include-deprecated", false, "Include deprecated.");
    options.addOption("u", "url", true, "Code system url.");
    options.addOption("b", "identifier", true, "Comma-separated list of additional business "
        + "identifiers. Each business identifer has the format [system]|[value].");
    options.addOption("v", "version", true, "Code system version. If empty the version of the "
        + "ontology will be used.");
    options.addOption("n", "name", true, "Code system name.");
    options.addOption("p", "publisher", true, "Comma-separated list of OWL annotation properties "
        + "that contain the code system publisher.");
    options.addOption("d", "description", true, "Comma-separated list of OWL annotation "
        + "properties that contain the code system description.");
    options.addOption("c", "code", true, "OWL annotation property for each class that maps to a "
        + "concept code.");
    options.addOption("t", "display", true, "OWL annotation property for each class that maps to "
        + "a display.");
    options.addOption("s", "synonyms", true, "Comma-separated list of OWL annotation properties "
        + "for each class that map to synonyms.");
    
    CommandLineParser parser = new DefaultParser();
    try {
      // parse the command line arguments
      CommandLine line = parser.parse(options, args);
      
      final File input = new File(line.getOptionValue("i"));
      final File output = new File(line.getOptionValue("o"));
      String url = line.hasOption('u') ? line.getOptionValue('u') : null;
      String identifiersArgs = line.hasOption('b') ? line.getOptionValue('b') : null;
      String version = line.hasOption('v') ? line.getOptionValue('v') : null;
      String name = line.hasOption('n') ? line.getOptionValue('n') : null;
      boolean includeDeprecated = line.hasOption('a');
      final List<String> publisherProps = new ArrayList<>();
      if (line.hasOption('p')) {
        publisherProps.addAll(Arrays.asList(line.getOptionValue('p').split(",")));
      }
      final List<String> descriptionProps = new ArrayList<>();
      if (line.hasOption('d')) {
        descriptionProps.addAll(Arrays.asList(line.getOptionValue('d').split(",")));
      }
      String codeProp = line.hasOption('c') ? line.getOptionValue('c') : null;
      String displayProp = line.hasOption('t') ? line.getOptionValue('t') : null;
      final List<String> synonymProps = new ArrayList<>();
      if (line.hasOption('s')) {
        synonymProps.addAll(Arrays.asList(line.getOptionValue('s').split(",")));
      }
      
      try {
        fhirOwlService.transform(input, output, url, processIdentifierArgs(identifiersArgs), 
            version, name, includeDeprecated, publisherProps, descriptionProps, codeProp, 
            displayProp, synonymProps);
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
  
  private List<Identifier> processIdentifierArgs(String args) throws ParseException {
    if (args == null) {
      return null;
    }
    
    final List<Identifier> res = new ArrayList<>();
    String[] parts = args.split("[,]");
    for (String part : parts) {
      String[] innerPart = part.split("[|]");
      if (innerPart.length != 2) {
        throw new ParseException("Inavlid identifier argument: " + part 
            + ". Valid format is [system]|[value].");
      }
      Identifier i = new Identifier();
      if (innerPart[0] != null && !innerPart[0].isEmpty()) {
        i.setSystem(innerPart[0]);
      }
      if (innerPart[1] == null || innerPart[1].isEmpty()) {
        throw new ParseException("Inavlid identifier argument: " + part 
            + ". Valid format is [system]|[value] and value cannot be empty.");
      }
      i.setValue(innerPart[1]);
      res.add(i);
    }
    return res;
  }

}
