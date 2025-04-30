# CQL Evaluation API

The process described here to evaluate a CQL query uses the [Quality Reporting][1] framework of FHIR and includes three steps. First, a [Library][3] resource is created on Blaze, followed by a [Measure][4] resource, which is then evaluated in the third step.

## Creating the Library Resource

The Library resource serves as a container for the CQL Query, which in fact is also a CQL library.

The following sub-items have to be done to create the corresponding Library resource:

### Base64 Encoding of the CQL Library

Using the following command:

```sh
cat docs/cql-queries/gender-male.cql | base64 | tr -d '\n'
```

generates the following Base64 string:

```text
bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICBQYXRpZW50LmdlbmRlciA9ICdtYWxlJwo=
```

Other methods of Base64 encoding are of course also possible.

### Creating the Library Resource

This Base64 string is then inserted into the library resource under `content[0].data`. The resulting library resource will look like this:

```json
{
  "resourceType": "Library",
  "url": "urn:uuid:a2b9f4b4-5d5b-46bd-a9fd-35f024c852fa",
  "status": "active",
  "type" : {
    "coding" : [
      {
        "system": "http://terminology.hl7.org/CodeSystem/library-type",
        "code" : "logic-library"
      }
    ]
  },
  "content": [
    {
      "contentType": "text/cql",
      "data": "bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICBQYXRpZW50LmdlbmRlciA9ICdtYWxlJwo="
    }
  ]
}
```

### Upload the Library Resource

The library resource can be uploaded using [curl][2] for example:

```sh
curl -sH "Content-Type: application/fhir+json" -d @library.json "http://localhost:8080/fhir/Library"
```

## Create the Measure Resource

The Measure resource represents the actual definition of the measure, which is evaluated in the third step. In its simplest form, a Measure resource looks like this:

```json
{
  "resourceType": "Measure",
  "url": "urn:uuid:49f4c7de-3320-4208-8e60-ecc0d8824e08",
  "status": "active",
  "library": "urn:uuid:a2b9f4b4-5d5b-46bd-a9fd-35f024c852fa",
  "scoring": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/measure-scoring",
        "code": "cohort"
      }
    ]
  },
  "group": [
    {
      "population": [
        {
          "code": {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/measure-population",
                "code": "initial-population"
              }
            ]
          },
          "criteria": {
            "language": "text/cql-identifier",
            "expression": "InInitialPopulation"
          }
        }
      ]
    }
  ]
}
```

The two important points here are on the one hand the reference to the Library resource via its canonical URL (`urn:uuid:a2b9f4b4-5d5b-46bd-a9fd-35f024c852fa`) and on the other hand the name of the CQL expression to be evaluated (`InitialPopulation`), which must appear in the CQL library.

### Upload the Measure Resource

The measure resource can be uploaded using curl, for example:

```sh
curl -sH "Content-Type: application/fhir+json" -d @measure.json "http://localhost:8080/fhir/Measure"
```

## Evaluation of the Measure Resource

The Measure resource is finally evaluated using the FHIR operation [$evaluate-measure][5].

Via curl this can be done as follows:

```sh
curl -s 'http://localhost:8080/fhir/Measure/$evaluate-measure?measure=urn:uuid:49f4c7de-3320-4208-8e60-ecc0d8824e08&periodStart=2000&periodEnd=2030'
```

the result should be the following [MeasureReport][6]:

```json
{
  "resourceType": "MeasureReport",
  "status": "complete",
  "type": "summary",
  "measure": "urn:uuid:49f4c7de-3320-4208-8e60-ecc0d8824e08",
  "date": "2021-06-14T10:54:31.458494Z",
  "period": {
    "start": "2000",
    "end": "2030"
  },
  "group": [
    {
      "population": [
        {
          "code": {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/measure-population",
                "code": "initial-population"
              }
            ]
          },
          "count": 0
        }
      ]
    }
  ]
}
```

Under `group[0].population[0].count` the result of the query can be found.

## Evaluation of the Measure Resource including Generation of Patient Lists

By default, the evaluation results in a MeasureReport of type `summary`. Such a MeasureReport contains only the number of resources of each population. However, if you also need the resources itself, you can have a MeasureReport of type `subject-list` generated. This works as follows:

```sh
curl -sd '{"resourceType": "Parameters", "parameter": [{"name": "periodStart", "valueDate": "2000"}, {"name": "periodEnd", "valueDate": "2030"}, {"name": "measure", "valueString": "urn:uuid:49f4c7de-3320-4208-8e60-ecc0d8824e08"}, {"name": "reportType", "valueCode": "subject-list"}]}' -H "Content-Type: application/fhir+json" 'http://localhost:8080/fhir/Measure/$evaluate-measure'
```

the result should be the following MeasureReport:

```json
{
  "resourceType": "MeasureReport",
  "id": "C6QKSKQ2YWQIY4A2",
  "meta": {
    "versionId": "913",
    "lastUpdated": "2021-06-14T12:50:19.711Z"
  },
  "status": "complete",
  "type": "subject-list",
  "measure": "urn:uuid:49f4c7de-3320-4208-8e60-ecc0d8824e08",
  "date": "2021-06-14T12:50:19.431239Z",
  "period": {
    "start": "2000",
    "end": "2030"
  },
  "group": [
    {
      "population": [
        {
          "code": {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/measure-population",
                "code": "initial-population"
              }
            ],
            "count": 57,
            "subjectResults": {
              "reference": "List/C6QKSKQ2V7DAF3X6"
            }
          }
        }
      ]
    }
  ]
}
```

When generating the MeasureReport, the list of Patient references has been stored by Blaze in the List resource `List/C6QKSKQ2V7DAF3X6`. If this List resource is called via:

```sh
curl -s 'http://localhost:8080/fhir/List/C6QKSKQ2V7DAF3X6'
```

it looks like this, truncated after two references:

```json
{
  "resourceType": "List",
  "id": "C6QKSKQ2V7DAF3X6",
  "meta": {
    "versionId": "913",
    "lastUpdated": "2021-06-14T12:50:19.711Z"
  },
  "status": "current",
  "mode": "working",
  "entry": [
    {
      "item": {
        "reference": "Patient/C6QKGRHBFJIQ3B5U"
      }
    },
    {
      "item": {
        "reference": "Patient/C6QKGROQ5G5NCKUY"
      }
    }
  ]
}
```

Thus, all patient references can be found under `entry.item.reference`.

### Output of the Patients of a List Resource

The pure List resource with its references is not particularly useful at first. When it comes to further processing of the populations, you would rather download the patients themselves.

There are two ways to do this, on the one hand you can use the `_include` search parameter on the list endpoint and on the other hand you can use the `_list` search parameter on the patient endpoint. 

#### Include Search Parameter

The following call:

```sh
curl -s 'http://localhost:8080/fhir/List?_id=C6QKSKQ2V7DAF3X6&_include=List:item'
```

yields, truncated and thinned out after two patients, the following bundle:

```json
{
  "resourceType": "Bundle",
  "id": "C6QKZUY3NTTIU5M5",
  "type": "searchset",
  "entry": [
    {
      "fullUrl": "http://localhost:8080/fhir/List/C6QKSKQ2V7DAF3X6",
      "resource": {
        "resourceType": "List",
        "id": "C6QKSKQ2V7DAF3X6",
        "meta": {
          "versionId": "913",
          "lastUpdated": "2021-06-14T12:50:19.711Z"
        },
        "status": "current",
        "mode": "working",
        "entry": [
          {
            "item": {
              "reference": "Patient/C6QKGRHBFJIQ3B5U"
            }
          },
          {
            "item": {
              "reference": "Patient/C6QKGROQ5G5NCKUY"
            }
          }
        ]
      },
      "search": {
        "mode": "match"
      }
    },
    {
      "fullUrl": "http://localhost:8080/fhir/Patient/C6QKGROQ5G5NCKUY",
      "resource": {
        "resourceType": "Patient",
        "id": "C6QKGROQ5G5NCKUY",
        "meta": {
          "versionId": "7",
          "lastUpdated": "2021-06-14T11:07:25.416Z"
        },
        "gender": "male",
        "birthDate": "1913-10-06"
      },
      "search": {
        "mode": "include"
      }
    },
    {
      "fullUrl": "http://localhost:8080/fhir/Patient/C6QKGUT6PPAUX3FV",
      "resource": {
        "resourceType": "Patient",
        "id": "C6QKGUT6PPAUX3FV",
        "meta": {
          "versionId": "59",
          "lastUpdated": "2021-06-14T11:08:13.899Z"
        }, 
        "gender": "male",
        "birthDate": "2006-03-15"
      },
      "search": {
        "mode": "include"
      }
    }
  ]
}
```

This variant has the disadvantage that paging cannot be used. Thus, the include variant is only suitable for small population sizes.

#### List Search Parameter

The following call:

```sh
curl -s 'http://localhost:8080/fhir/Patient?_list=C6QKSKQ2V7DAF3X6&_count=2'
```

results in, thinned out, the following bundle:

```json
{
  "resourceType": "Bundle",
  "id": "C6QK4HB4FDS6PKRY",
  "type": "searchset",
  "entry": [
    {
      "fullUrl": "http://localhost:8080/fhir/Patient/C6QKGRHBFJIQ3B5U",
      "resource": {
        "resourceType": "Patient",
        "id": "C6QKGRHBFJIQ3B5U",
        "meta": {
          "versionId": "3",
          "lastUpdated": "2021-06-14T11:07:19.316Z"
        },
        "gender": "male",
        "birthDate": "1986-06-22"
      },
      "search": {
        "mode": "match"
      }
    },
    {
      "fullUrl": "http://localhost:8080/fhir/Patient/C6QKGROQ5G5NCKUY",
      "resource": {
        "resourceType": "Patient",
        "id": "C6QKGROQ5G5NCKUY",
        "meta": {
          "versionId": "7",
          "lastUpdated": "2021-06-14T11:07:25.416Z"
        },
        "gender": "male",
        "birthDate": "1913-10-06"
      },
      "search": {
        "mode": "match"
      }
    }
  ],
  "link": [
    {
      "relation": "next",
      "url": "http://localhost:8080/fhir/Patient?_list=C6QKSKQ2V7DAF3X6&_count=2&__t=913&__page-id=C6QKGRPAJHAD6KFW"
    }
  ]
}
```

This variant uses the special search parameter `_list` to search for patients included in the list with the given ID. This list of patients can be further shaped using the normal search parameters like `_count` or additionally using `_include`.

[1]: <https://www.hl7.org/fhir/clinicalreasoning-quality-reporting.html>
[2]: <https://curl.se>
[3]: <https://www.hl7.org/fhir/library.html>
[4]: <https://www.hl7.org/fhir/measure.html#Measure>
[5]: <https://www.hl7.org/fhir/operation-measure-evaluate-measure.html>
[6]: <https://www.hl7.org/fhir/measurereport.html#MeasureReport>
