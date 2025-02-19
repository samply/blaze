# LOINC <Badge type="info" text="Feature: TERMINOLOGY_LOINC"/> <Badge type="warning" text="Since 0.32"/>

> [!NOTE]
> LOINC data is build into the Blaze image. Because LOINC support needs additional memory, it has to be enabled by setting the environment variable `ENABLE_TERMINOLOGY_LOINC` to `true`.

Blaze supports the [LOINC](https://loinc.org) terminology in version 2.78.

## Copyright

This material contains content from [LOINC](http://loinc.org). LOINC is copyright © 1995-2020, Regenstrief Institute, Inc. and the Logical Observation Identifiers Names and Codes (LOINC) Committee and is available at no cost under the license at http://loinc.org/license icon. LOINC® is a registered United States trademark of Regenstrief Institute, Inc.

## Filters

| Property   | Operators | Values                                                                        |
|------------|-----------|-------------------------------------------------------------------------------|
| COMPONENT  | =, regex  | Part code / Part term                                                         |
| PROPERTY   | =, regex  | Part code / Part term                                                         |
| TIME_ASPCT | =, regex  | Part code / Part term                                                         |
| SYSTEM     | =, regex  | Part code / Part term                                                         |
| SCALE_TYP  | =, regex  | Part code / Part term                                                         |
| METHOD_TYP | =, regex  | Part code / Part term                                                         |
| CLASS      | =, regex  | Part code / Part term                                                         |
| STATUS     | =, regex  | ACTIVE, TRIAL, DISCOURAGED, DEPRECATED                                        |
| CLASSTYPE  | =         | 1 (Laboratory class), 2 (Clinical class), 3 (Claims attachments), 4 (Surveys) |
| ORDER_OBS  | =, regex  | Order, Observation, Both, Subset                                              |
| LIST       | =         | Answer list code                                                              |

## Implicit Value Sets

The following implicit value sets are supported.

| Name         | Example                      |
|--------------|------------------------------|
| Answer Lists | http://loinc.org/vs/LL4049-4 |

## Resources

[Using LOINC with FHIR](https://terminology.hl7.org/LOINC.html)
