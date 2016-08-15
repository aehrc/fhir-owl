package au.csiro.fhir.owl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import ca.uhn.fhir.context.FhirContext;

@SpringBootApplication
public class Application extends WebMvcConfigurerAdapter {

    /**
     * Created here as a bean because it is expensive to create and we only need one instance that can be shared.
     *
     * @return
     */
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forDstu3();
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
