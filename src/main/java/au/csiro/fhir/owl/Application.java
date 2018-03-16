package au.csiro.fhir.owl;

import static java.lang.System.exit;

import au.csiro.fhir.owl.util.CustomBundleSerialiser;
import ca.uhn.fhir.context.FhirContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.hl7.fhir.dstu3.model.Bundle;
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
    return FhirContext.forDstu3();
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
    
    SpringApplication app = new SpringApplication(Application.class);
    app.setBannerMode(Banner.Mode.OFF);
    app.run(args);

  }

  @Override
  public void run(String... args) throws Exception {
    if (args.length != 2) {
      System.out.println("Usage java - jar target/fhir-owl-1.0.jar [input OWL file] "
          + "[target FHIR JSON file]");
      exit(0);
    }
    
    final File input = new File(args[0]);
    final File output = new File(args[1]);
    
    try {
      fhirOwlService.transform(input, output);
    } catch (Throwable t) {
      System.out.println("There was a problem transforming the OWL file into FHIR: " 
          + t.getLocalizedMessage());
      t.printStackTrace();
    }
    
    exit(0);
  }

}
