# OWL to FHIR Transformer

This Spring Boot CLI application transforms an OWL ontology into FHIR terminology resources.

## Building from source

You will need Maven installed in your computer. You can build the jar file using Maven.

```
mvn package
```

## Configuration

### Properties

The following properties define some of the default values that are used. These can be overriden via arguments to the VM, e.g., ``-Dontoserver.owl.defaults.publisher=http://purl.org/dc/elements/1.1/publisher``

__ontoserver.owl.defaults.publisher__ (http://purl.org/dc/elements/1.1/publisher)

The default annotation property where the publisher of the ontology can be found.

__ontoserver.owl.defaults.description__ (http://purl.org/dc/elements/1.1/subject,http://www.w3.org/2000/01/rdf-schema#comment)

The default annotation property where the description of the ontology can be found.

__ontoserver.owl.defaults.preferredTerm__ (http://www.w3.org/2000/01/rdf-schema#label)

The default annotation property where the preferred term of OWL classes can be found.

__ontoserver.owl.defaults.synonym__ (http://www.w3.org/2000/01/rdf-schema#label)

The default annotation property where the synonyms of the OWL classes can be found.

### IRI mappings

The application also looks for a file named __iri_mappings.txt__ in the classpath and uses it to load OWL ontologies that are available in the file system. This is useful to reduce the loading time of some ontologies that import large external ontologies, such as HPO. By default, the OWL API library will try to resolve any imported ontology using its IRI, which results in the ontology being downloaded from the web. The iri_mappings.txt file defines mappings between IRIs and local files, as shown here:

```
http://purl.obolibrary.org/obo/nbo.owl,/CSIRO/resources/ontologies/iri_maps/http___purl_obolibrary_org_obo_nbo_owl.owl
```

The location of the files is relative to the user's home folder.

## Running

You need a JVM to run the application. The only mandatory options are -i and -o.

```
java -jar fhir-owl-1.0.0.jar -i [input OWL file] -o [output FHIR JSON file]
```

The following options are available:

| Parameter          | Type        | Description   |
| :----------------- | :---------- |:------------- |
| -c                 | string      | Indicates which annotation property contains the concepts' codes. If the value is not set, then the IRI of the class is used. If the class is imported then the full IRI is used. If the class is defined in the ontology then the short form is used. |
| -codeReplace       | string      | Two strings separated by a comma. Replaces the first string with the second string in all local codes. |
| -compositional     | boolean     | Flag to indicate if the code system defines a post-coordination grammar. |
| -contact           | string      | Comma-separated list of contact details for the publisher. Each contact detail has the format [name\|system\|value], where system has the following possible values: *phone*, *fax*, *email*, *pager*, *url*, *sms* or *other*. |
| -content           | string      | The extent of the content in this resource. Valid values are *not-present*, *example*, *fragment*, *complete* and *supplement*. Defaults to *complete*. The actual value does not affect the output of the transformation. |
| -copyright         | string      | A copyright statement about the code system. |
| -d                 | string      | Indicates which annotation property contains the concepts' displays. Default is RDFS:label. |
| -date              | string      | The published date. Valid formats are: YYYY, YYYY-MM, YYYY-MM-DD and YYYY-MM-DDThh:mm:ss+zz:zz. |
| -definition        | string      | Indicates which annotation property contains the concepts' definitions. |
| -description       | string      | The description of the code system. This option takes precedence over -descriptionProp. |
| -descriptionProp   | string      | Comma-separated list of OWL annotation properties that contain the code system description. |
| -experimental      | boolean     | Indicates if the code system is for testing purposes or real usage. |
| -filter            | string      | Comma-separated list of properties to add a filter for.  The listed filters will only be added if they exist as a CodeSystem Property (see -extractDataProps and -extractObjectProps). Filter Operator will be "Equal" and the Filter value will be the type of the Property.  |
| -help               | none        | Print the help message. |
| -hierarchyMeaning  | string      | The meaning of the hierarchy of concepts as represented in this resource. Valid values are *grouped-by*, *is-a*, *part-of*, and *classified-with*.  Default is *is-a*. | 
| -i                 | string      | The input OWL file. |
| -id                | string      | The technical id of the code system. Required if using PUT to upload the resource to a FHIR server. |
| -identifier        | string      | Comma-separated list of additional business identifiers. Each business identifer has the format [system]\|[value]. |
| -inactive           | string      | Indicates which annotation property contains the concepts' inactive status. If the value is not set, then inactive defaults to false for all codes |
| -includeDeprecated | boolean     | Include all OWL classes, including deprecated ones. |
| -jurisdiction      | string      | Comma-separated list of jurisdictions for the codesystem. Each jurisdiction must have the format [system\|code\|display], with values retrieved from the [FHIR Jurisdiction ValueSet](https://hl7.org/fhir/valueset-jurisdiction.html). |
| -labelsToExclude   | string      | Comma-separated list of class labels to exclude. |
| -language          | string      | The language of the content. This is a code from the [FHIR Common Languages value set](https://www.hl7.org/fhir/valueset-languages.html). |
| - mainNs           | string      | Comma-separated list of namespace prefixes that determine which classes are part of the main ontology. |
| -n                 | string      | Used to specify the computer-friendly name of the code system. This option takes precedence over -nameProp. |
| -nameProp          | string      | A property to look for the computer-friendly name of the code system in the OWL file. If this option is not specified or the specified property is not found, then the RDFS:label property is used by default. If no label can be found using the property then the ontology IRI is used. |
| -o                 | string      | The output FHIR JSON file. |
| -publisher         | string      | The publisher of the code system. This option takes precedence over -publisherProp. |
| -publisherProp     | string      | Comma-separated list of OWL annotation properties that contain the code system publisher. |
| -purpose           | string      | Explanation of why this code system is needed. |
| -s                 | string      | Comma-separated list of annotation properties on OWL classes that contain the concepts' synonyms. |
| -status            | string      | Code system status. Valid values are: *draft*, *active*, *retired* and *unknown*. |
| -t                 | string      | A human-friendly name for the code system. |
| -url               | string      | Canonical identifier of the code system. If this option is not specified then the ontology’s IRI will be used. If the ontology has no IRI then the transformation fails. |
| -v                 | string      | Business version. If this option is not specified then the ontology’s version will be used. If the ontology has no version then the version is set to ‘NA’. |
| -valueset          | string      | The value set that represents the entire code system. If this option is not specified then the value will be constructed from the URI of the code system. |
| -versionNeeded     | boolean     | Flag to indicate if the code system commits to concept permanence across versions. |
| -r                 | string      | The reasoner to use. Valid values are: *elk* and *jfact*. Default value is *elk*.  |
| -useFhirExtension  | boolean     | Flag to indicate if the last part of an IRI ending in `.owl` should be replaced with `.fhir`. |
| -dateRegex         | string      | A regular expression used to extract the date of the code system from the configured attribute in the ontology. It should have the following three named groups: year, month and day. The three groups will be concatenated to form a version of the form `YYYYMMDD`. This is useful if the ontology version is a URI that contains a date but only the date wants to be used as the version of the code system. |
| -extractDataProps  | boolean     | Flag to indicate if DataProperties defined in the source ontology should be added as CodeSystem properties and CodeSystem Concept properties. |
| -extractObjectProps| boolean     | Flag to indicate if ObjectProperties defined in the source ontology should be added as CodeSystem properties and CodeSystem Concept properties. An attempt has been made to represent both simple and complex OWL Object Properties, but because of the variability of how OWL Object Properties are expressed in different Ontologies, there may be need for enhancements in the future. |

### Examples

The Human Phenotype Ontology was transformed using the following command:

```
java -jar fhir-owl-v1.1.0.jar -i hp.owl -o hp.json -id hpo -name "HumanPhenotypeOntology" -t "Human Phenotype Ontology" -content complete -mainNs http://purl.obolibrary.org/obo/HP_ -descriptionProp http://purl.org/dc/elements/1.1/subject -status active -codeReplace _,: -useFhirExtension -dateRegex "(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})" -s http://www.geneontology.org/formats/oboInOwl#hasExactSynonym
```

You can browse the output [here](https://ontoserver.csiro.au/shrimp/?system=http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2Fhp.fhir&concept=http://www.w3.org/2002/07/owl%23Thing&version=20210613&valueset=http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2Fhp.fhir%3Fvs).

The Sequence Ontology was transformed using the following command:

```
java -jar fhir-owl-1.0.0.jar -i so-simple.owl -o so-simple.json -id so -name "Sequence Ontology" -status active -codeReplace "_,:" -s "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym" -mainNs "http://purl.obolibrary.org/obo/SO_" -labelsToExclude "wiki,WIKI"
```

You can browse the output [here](https://ontoserver.csiro.au/shrimp/?concept=Thing&system=http://purl.obolibrary.org/obo/so/so-simple.owl&versionId=http://purl.obolibrary.org/obo/so/so-xp.owl/so-simple.owl&fhir=https://genomics.ontoserver.csiro.au/fhir).


