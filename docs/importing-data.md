# Importing Data

In order to use Blaze for queries, you have to import your data. Although you can use the full functionality of the [FHIR RESTful API](https://www.hl7.org/fhir/http.html) available under `http://localhost:8080/fhir` to create resources, the easiest way is to download [blazectl](https://github.com/samply/blazectl/releases/tag/v0.8.1) to upload [bundles](https://www.hl7.org/fhir/bundle.html).

First you should test connectivity by counting already available resources in Blaze which should be zero:

```text
blazectl --server http://localhost:8080/fhir count-resources
```

which should return:

```text
Count all resources on http://localhost:8080/fhir ...

-------------------------------------
total                             : 0
```

After that, you need a FHIR bundle to upload. You can generate one by downloading [bbmri-fhir-gen](https://github.com/samply/bbmri-fhir-gen) and run:

```text
mkdir fhir-test-data
bbmri-fhir-gen fhir-test-data
```

That will generate two files under `fhir-test-data`:

```text
-rw-r--r--  1 akiel  staff    31K Nov  8 10:16 biobank.json
-rw-r--r--  1 akiel  staff   757K Nov  8 10:16 transaction-0.json
```

After you have the test data, you can upload it with:

```text
blazectl --server http://localhost:8080/fhir upload fhir-test-data
```

which will output:

```text
Starting Upload to http://localhost:8080/fhir ...
Uploads          [total, concurrency]     2, 2
Success          [ratio]                  100.00 %
Duration         [total]                  3s
Requ. Latencies  [mean, 50, 95, 99, max]  2.414s, 3.313s, 3.313s, 3.313s 3.313s
Proc. Latencies  [mean, 50, 95, 99, max]  2.412s, 3.31s, 3.31s, 3.31s 3.31s
Bytes In         [total, mean]            123.60 KiB, 61.80 KiB
Bytes Out        [total, mean]            788.30 KiB, 394.15 KiB
Status Codes     [code:count]             200:2
```

counting the FHIR resources again:

```text
blazectl --server http://localhost:8080/fhir count-resources
```

should return:

```text
Count all resources on http://localhost:8080/fhir ...

Condition                         : 100
Observation                       : 431
Organization                      :  11
Patient                           : 100
Specimen                          : 100
---------------------------------------
total                             : 742
```

## Disabling Referential Integrity Checks

If your primary date source ensures referential integrity already, and you can't import the resources into Blaze in the right order or bunldes so that the referential integrity will be maintained per transaction, you can disable the referential integrity checks in Blaze by setting the env var `ENFORCE_REFERENTIAL_INTEGRITY` to `false`. 

The checks can be enabled/disabled at each start of Blaze. You can check whether the checks are enabled by executing:

````sh
curl -s http://localhost:8080/fhir/metadata | jq -r 'isempty(.rest[].resource[].referencePolicy[] | select(. == "enforced")) | not'
````

That command return `true` or `false` depending on whether the referential integrity checks are enabled or not.

Please be aware that Blaze doesn't check the referential integrity of already imported resource if you enable the checks after you have already imported resources. By disabling the referential integrity checks at some time, you will have a database that can contain stale references.
