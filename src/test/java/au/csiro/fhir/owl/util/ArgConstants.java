package au.csiro.fhir.owl.util;

public class ArgConstants {

    //   Only used to keep the application context up for the sake of allowing assertions in tests.
    //   Not published as an argument since it is not useful for users
    public static final String TEST_FLAG = "-test";
    
    public static final String INPUT_FLAG = "-i";
    public static final String INPUT_FILE = "src/test/resources/pizza.owl";
    public static final String OUTPUT_FLAG = "-o";
    public static final String OUTPUT_FILE = "src/test/resources/pizza.json";
    
    public static final String CONTACT_FLAG = "-contact";
    public static final String EXAMPLE_NAME_1 = "Mr. Lorem Ipsum";
    public static final String EXAMPLE_EMAIL = "lorem.ipsum@gmail.com";
    public static final String EMAIL = "email";
    public static final String EXAMPLE_NAME_2 = "Ms. Ipsum Lorem";
    public static final String EXAMPLE_PHONE = "987-765-4321";
    public static final String PHONE = "phone";
    
    public static final String CONTACT_EXAMPLE_WITH_EMAIL = EXAMPLE_NAME_1 +"|"+EMAIL+"|"+ EXAMPLE_EMAIL;
    public static final String CONTACT_EXAMPLE_WITH_PHONE = EXAMPLE_NAME_2 +"|"+PHONE+"|"+ EXAMPLE_PHONE;
    
    public static final String HIERARCHY_MEANING_FLAG = "-hierarchyMeaning";
    public static final String HIERARCHY_MEANING_GROUPED_BY = "grouped-by"; //Static value of CodeSystem.CodeSystemHierarchyMeaning.GROUPEDBY
    
    public static final String JURISDICTION_FLAG = "-jurisdiction";
    public static final String JURISDICTION_URN = "urn:iso:std:iso:3166";
    public static final String US = "US";
    public static final String UNITED_STATES_OF_AMERICA = "United States of America";
    public static final String JURISDICTION_EXAMPLE_US_ARG = JURISDICTION_URN+"|"+US+"|"+UNITED_STATES_OF_AMERICA;
    
    
}
