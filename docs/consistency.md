# Consistency Guaranties

The FHIR specification is not overly clear whether a FHIR server should ensure the ACID (atomicity, consistency, isolation, durability) properties. For Blaze we like to provide a consistency model suitable for handling medical data. We test our claims using the [Jepsen][1] framework. 

# Linearizability on a Single Resource

TODO


[1]: <https://jepsen.io>
