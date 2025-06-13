# Transaction

The transaction interaction allows to submit a set of actions to be performed inside a single transaction. Blaze supports the full set of ACID (atomicity, consistency, isolation, durability) properties. Transactions are always performed atomically and in isolation. Actions from other individual requests or actions from batch interactions will not interleave with any actions from a transaction interaction.

```
POST [base]
```

## Processing Rules 

References in transaction bundles are resolved according to [Resolving references in Bundles][1]. Especially absolute URIs like URNs and URLs can be used as well as relative references in entries with absolute RESTful fullUrls.

[1]: <https://hl7.org/fhir/bundle.html#references>
