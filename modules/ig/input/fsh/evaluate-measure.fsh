Extension: EvalDuration
Id: eval-duration
Title: "Evaluation Duration"
Description: "The duration the evaluation of the measure took, in seconds."
* ^status = #active
* ^context[0].type = #element
* ^context[0].expression = "MeasureReport"
* value[x] only Quantity
* valueQuantity
  * value 1..1
  * system 1..1
  * system = UCUM
  * code 1..1
  * code = #s "seconds"

Extension: BloomFilterRatio
Id: bloom-filter-ratio
Title: "Bloom Filter Ratio"
Description: "The number of Bloom filters available over the number of Bloom filters requested by the evaluation of the measure. With the CQL expression cache enabled, a Bloom filter lets the evaluation skip the patients for which an expression is certainly false. A filter that isn't available yet is calculated in the background, so a lower ratio means a slower evaluation. Without the CQL expression cache, no Bloom filters are requested, so both numbers are 0."
* ^status = #active
* ^context[0].type = #element
* ^context[0].expression = "MeasureReport"
* value[x] only Ratio
* valueRatio
  * numerator 1..1
  * numerator.value 1..1
  * denominator 1..1
  * denominator.value 1..1
