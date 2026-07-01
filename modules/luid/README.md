# Locally Unique Lexicographically Sortable Identifier

This module contains an implementation of identifiers as an alternative to UUID's, closely related to [ULID's][1]. The design goals are:

* generatable in a distributed system with low (but not very low) probability of collisions
* lexicographically sortable (generates monotonically greater identifiers with time)
* short string representation, compatible with the FHIR ID constraints

## Structure

A LUID is a compact, lexicographically sortable identifier made from a millisecond timestamp and a random entropy component. It consists of a 44 bit wide timestamp component followed by a 36 bit wide entropy component. The total width is 80 bit or 10 byte. It is encoded as a base 32 string of length 16.

## Usage

Two ways to obtain LUIDs are provided:

* `luid` creates a single LUID, drawing one long from the RNG, and
* `generator` creates a generator that yields a strictly increasing sequence of LUIDs while drawing only a single long from the RNG in total, at creation time.

The generator is useful whenever multiple identifiers are needed at once: subsequent LUIDs are derived by incrementing the entropy component (rolling over into the timestamp on overflow), so the RNG is consulted only once.

## Comparison to ULID's

[ULID's][1] are 128 bit wide and encoded as a 26 character string, spending 48 bit on the timestamp and 80 bit on randomness. Blaze instead uses an 80 bit identifier encoded as only 16 characters, spending 44 bit on the timestamp and 36 bit on the entropy.

The reason for the own implementation is the **shorter string representation**. Identifiers appear pervasively — in resource references, URLs and database indexes — so their length has a real cost. Trimming the layout to 80 bit yields a 16 character id instead of ULID's 26 characters while still fitting the FHIR ID constraints.

The price is a smaller entropy component: 36 bit instead of ULID's 80 bit. This raises the probability of a collision between identifiers generated in the same millisecond from very low (ULID) to low (Blaze). Whether that trade-off is acceptable depends on the rate at which identifiers are generated; it is quantified by the collision test in `blaze.luid-test`. For example, when generating 1,000 LUID's per millisecond it takes on the order of 310,000 milliseconds to reach a 90% probability of a single collision. The 44 bit timestamp is enough to represent milliseconds since the epoch until roughly the year 2527.


[1]: <https://github.com/ulid/spec> 
