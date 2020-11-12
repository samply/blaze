# Locally Unique Lexicographically Sortable Identifier

This module contains an implementation of identifiers as an alternative to UUID's, closely related to [ULID's][1]. The design goals are:

* generatable in a distributed system with low (but not very low) probability of collisions
* lexicographically sortable (generates monotonically greater identifiers with time)
* short string representation, compatible with the FHIR ID constraints




[1]: <https://github.com/ulid/spec> 
