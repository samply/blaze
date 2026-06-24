(ns blaze.job.canonical.impl-test
  (:require
   [blaze.fhir.canonical :as canonical]
   [blaze.job.canonical.impl :as impl]
   [blaze.job.canonical.impl-spec]
   [blaze.job.test-util :as jtu]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest upgrade-test
  (testing "every coded value and profile of a legacy job gains the current canonical, current first"
    (given (impl/upgrade (jtu/job canonical/old-base))
      [:meta :profile 0] := #fhir/canonical "https://blaze-server.org/fhir/StructureDefinition/ReIndexJob"
      [:meta :profile 1] := #fhir/canonical "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"
      [:code :coding 0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobType"
      [:code :coding 1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
      [:input 0 :type :coding 0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/ReIndexJobParameter"
      [:input 0 :type :coding 1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter")))

(deftest downgrade-test
  (testing "every 0.1.0 coded value and profile of a current job gains the legacy canonical, current first"
    (given (impl/downgrade (jtu/job canonical/base))
      [:meta :profile count] := 2
      [:meta :profile 0] := #fhir/canonical "https://blaze-server.org/fhir/StructureDefinition/ReIndexJob"
      [:meta :profile 1] := #fhir/canonical "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"
      [:code :coding 0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobType"
      [:code :coding 1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
      [:input 0 :type :coding 0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/ReIndexJobParameter"
      [:input 0 :type :coding 1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter"))

  (testing "a profile added after 0.1.0 is not downgraded"
    (given (impl/downgrade (jtu/job canonical/base :profile "VacuumJob"))
      [:meta :profile count] := 1
      [:meta :profile 0] := #fhir/canonical "https://blaze-server.org/fhir/StructureDefinition/VacuumJob"))

  (testing "a code added to an existing CodeSystem after 0.1.0 is not downgraded"
    (given (impl/downgrade (jtu/job canonical/base :job-type "vacuum"))
      [:code :coding count] := 1
      [:code :coding 0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/JobType"
      [:code :coding 0 :code] := #fhir/code "vacuum"))

  (testing "a CodeSystem added after 0.1.0 is not downgraded"
    (let [j (assoc-in (jtu/job canonical/base) [:input 0 :type]
                      (jtu/concept (str canonical/base "/CodeSystem/VacuumJobParameter") "size"))]
      (given (impl/downgrade j)
        [:input 0 :type :coding count] := 1
        [:input 0 :type :coding 0 :system] := #fhir/uri "https://blaze-server.org/fhir/CodeSystem/VacuumJobParameter")))

  (testing "status reason, business status and typed outputs gain the legacy canonical, an output without a type is left untouched"
    (let [j (assoc (jtu/job canonical/base)
                   :statusReason (jtu/concept (str canonical/base "/CodeSystem/JobStatusReason") "paused")
                   :businessStatus (jtu/concept (str canonical/base "/CodeSystem/JobCancelledSubStatus") "requested")
                   :output [{:fhir/type :fhir.Task/output
                             :type (jtu/concept (str canonical/base "/CodeSystem/JobOutput") "error")}
                            {:fhir/type :fhir.Task/output
                             :value #fhir/string "no-type"}])]
      (given (impl/downgrade j)
        [:statusReason :coding count] := 2
        [:statusReason :coding 1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason"
        [:businessStatus :coding count] := 2
        [:businessStatus :coding 1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobCancelledSubStatus"
        [:output 0 :type :coding count] := 2
        [:output 0 :type :coding 1 :system] := #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput"
        [:output 1 :type] := nil
        [:output 1 :value] := #fhir/string "no-type"))))
