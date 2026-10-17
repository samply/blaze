// The conversation the CQL chat animation plays. The counts are the ones Blaze
// returns for the Synthea test data in `.github/test-data/synthea`.

export interface Bar {
  label: string;
  value: number;
}

export type Step =
  | { kind: "user"; text: string }
  | { kind: "assistant"; text: string; chart?: Bar[] }
  | { kind: "file"; name: string; content: string }
  | { kind: "command"; command: string; output: string };

const decades: [number, number][] = [
  [1910, 3],
  [1920, 7],
  [1930, 1],
  [1940, 13],
  [1950, 11],
  [1960, 22],
  [1970, 15],
  [1980, 10],
  [1990, 12],
  [2000, 11],
  [2010, 14],
  [2020, 1],
];

export const conversation: Step[] = [
  {
    kind: "user",
    text: "How many male patients are on our Blaze server at http://localhost:8080/fhir?",
  },
  {
    kind: "assistant",
    text: "I'll define the male patients as the initial population of a CQL measure and let Blaze evaluate it with blazectl.",
  },
  {
    kind: "file",
    name: "gender-male.cql",
    content: `library "gender-male"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  Patient.gender = 'male'`,
  },
  {
    kind: "file",
    name: "gender-male.yml",
    content: `library: gender-male.cql
group:
- type: Patient
  population:
  - expression: InInitialPopulation`,
  },
  {
    kind: "command",
    command: `blazectl evaluate-measure gender-male.yml \\
  --server http://localhost:8080/fhir \\
  | jq '.group[0].population[0].count'`,
    output: `Evaluate measure with canonical URL urn:uuid:73a6023d-5a91-4822-9e36-48ebc2a8ed09 on http://localhost:8080/fhir ...

56`,
  },
  {
    kind: "assistant",
    text: "Your Blaze server has **56** male patients. Blaze evaluated `InInitialPopulation` for every patient and counted the ones for which it was true.",
  },
  {
    kind: "user",
    text: "How are all patients distributed by birth decade?",
  },
  {
    kind: "assistant",
    text: "I'll include every patient and add a stratifier that groups them by the decade of their birth date.",
  },
  {
    kind: "file",
    name: "birth-decade.cql",
    content: `library "birth-decade"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  true

define BirthDecade:
  (year from Patient.birthDate) div 10 * 10`,
  },
  {
    kind: "file",
    name: "birth-decade.yml",
    content: `library: birth-decade.cql
group:
- type: Patient
  population:
  - expression: InInitialPopulation
  stratifier:
  - code: birth-decade
    expression: BirthDecade`,
  },
  {
    kind: "command",
    command: `blazectl evaluate-measure birth-decade.yml \\
  --server http://localhost:8080/fhir \\
  | jq -r '.group[0].stratifier[0].stratum
      | sort_by(.value.text)[]
      | "\\(.value.text) \\(.population[0].count)"'`,
    output: `Evaluate measure with canonical URL urn:uuid:5be0b1a2-3f6e-4c1d-9a57-2e8f0c6d4b19 on http://localhost:8080/fhir ...

${decades.map(([decade, count]) => `${decade} ${count}`).join("\n")}`,
  },
  {
    kind: "assistant",
    text: "All **120** patients fall into 12 birth decades. The 1960s are the largest group with **22** patients:",
    chart: decades.map(([decade, count]) => ({
      label: `${decade}s`,
      value: count,
    })),
  },
];
