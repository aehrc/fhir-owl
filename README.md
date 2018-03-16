# OWL to FHIR Transformer

This standalone application transforms an OLW ontology into one or more FHIR CodeSystem resources. 

The following are some of the challenges in this transformation:

1. OWL ontologies have an import mechanism that can be used to include content from other ontologies and thus allow reusability. FHIR does not support a similar mechanism, but codes in a code system can declare a parent property that references an external code system.

2. Classes defined in an OWL ontology do not necessarily need to used the Ontology IRI as prefix, as is the case with the OBO ontologies. For example, the IRI for HPO is http://purl.obolibrary.org/obo/hp.owl and a sample class is http://purl.obolibrary.org/obo/HP\_0000005.

3. Importing of the ontologies that contain external classes is not enforced and this has two consequences:
    * It complicates determining the defining ontology for the class. For example, RO\_IMPORT defines the class PATO\_0001241 without importing the PATO ontology.
    * Impedes the creation of some of the required code systems. For example, HSAPDV declares the UBERON class UBERON_0000105, but the UBERON ontololgy is not imported.

4. Some OWL ontologies import a smaller version of other ontologies, for performance reasons. These derived ontologies are, however, not the defining ontologies of the imported classes and therefore the original defining ontology should be resolved and used to create the corresponding FHIR code system.

5. Some imports are not versioned, which makes creating the corresponding code systems difficult.

All of these examples are based on HPO version 2017\-06\-21 (and it's imports). 

The algorithm for determining the containing code system for an OWL class is as follows:

1. Check if the IRI has the ontology IRI as prefix. In this case, the class is defined in this ontology.
2. Check the short name of the class and see if matches the following regex: ``^[a-zA-Z]*[_][0-9]*``. In that case we assume the class follows the OBO conventions and the prefix determines the containing ontology.





