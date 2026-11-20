Extension: GrandTotal
Id: grand-total
Title: "Grand Total"
Description: "The number of entries of a history bundle that exceeds the range of `Bundle.total`, which is an unsignedInt and so can't hold numbers higher than 2^31-1. In that case, `Bundle.total` has no value and carries this extension instead, holding the number as decimal string."
* ^status = #active
* ^context[0].type = #element
* ^context[0].expression = "Bundle.total"
* value[x] only string
