{ resourceType: "Bundle",
  type: "transaction",
  entry: [.entry[]
    # select only resource types we use in queries
    | select(.resource.resourceType == "Patient"
      or .resource.resourceType == "Encounter"
      or .resource.resourceType == "Observation"
      or .resource.resourceType == "Condition"
      or .resource.resourceType == "DiagnosticReport"
      or .resource.resourceType == "Medication"
      or .resource.resourceType == "MedicationAdministration"
      or .resource.resourceType == "Procedure")
    # remove the narrative because we don't use it
    | del(.resource.text)
    ]
}
