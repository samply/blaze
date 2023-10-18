library "condition-50-rare"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'

context Patient

define InInitialPopulation:
  exists [Condition: Code '287185009' from sct] or
  exists [Condition: Code '427089005' from sct] or
  exists [Condition: Code '707577004' from sct] or
  exists [Condition: Code '11218009' from sct] or
  exists [Condition: Code '406602003' from sct] or
  exists [Condition: Code '427089005' from sct] or
  exists [Condition: Code '233604007' from sct] or
  exists [Condition: Code '448813005' from sct] or
  exists [Condition: Code '448417001' from sct] or
  exists [Condition: Code '190905008' from sct] or
  exists [Condition: Code '55680006' from sct] or
  exists [Condition: Code '414564002' from sct] or
  exists [Condition: Code '86849004' from sct] or
  exists [Condition: Code '27942005' from sct] or
  exists [Condition: Code '225444004' from sct] or
  exists [Condition: Code '47200007' from sct] or
  exists [Condition: Code '171131006' from sct] or
  exists [Condition: Code '200936003' from sct] or
  exists [Condition: Code '60951000119105' from sct] or
  exists [Condition: Code '40095003' from sct] or
  exists [Condition: Code '300916003' from sct] or
  exists [Condition: Code '86175003' from sct] or
  exists [Condition: Code '314994000' from sct] or
  exists [Condition: Code '1734006' from sct] or
  exists [Condition: Code '433144002' from sct] or
  exists [Condition: Code '157141000119108' from sct] or
  exists [Condition: Code '429280009' from sct] or
  exists [Condition: Code '67811000119102' from sct] or
  exists [Condition: Code '298382003' from sct] or
  exists [Condition: Code '254632001' from sct] or
  exists [Condition: Code '76916001' from sct] or
  exists [Condition: Code '94260004' from sct] or
  exists [Condition: Code '161622006' from sct] or
  exists [Condition: Code '399912005' from sct] or
  exists [Condition: Code '127295002' from sct] or
  exists [Condition: Code '373587001' from sct] or
  exists [Condition: Code '230745008' from sct] or
  exists [Condition: Code '302297009' from sct] or
  exists [Condition: Code '414667000' from sct] or
  exists [Condition: Code '68566005' from sct] or
  exists [Condition: Code '267064002' from sct] or
  exists [Condition: Code '36923009' from sct] or
  exists [Condition: Code '45816000' from sct] or
  exists [Condition: Code '14760008' from sct] or
  exists [Condition: Code '15802004' from sct] or
  exists [Condition: Code '157265008' from sct] or
  exists [Condition: Code '698754002' from sct] or
  exists [Condition: Code '47505003' from sct] or
  exists [Condition: Code '288959006' from sct] or
  exists [Condition: Code '234466008' from sct]
