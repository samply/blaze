(ns blaze.terminology-service.local.value-set.validate-code.issue-test
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.value-set.validate-code.issue :as issue]
   [blaze.terminology-service.local.value-set.validate-code.issue-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn tx-issue-type [code]
  (fn [codings]
    (some
     (fn [coding]
       (and (= "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type" (type/value (:system coding)))
            (= code (type/value (:code coding)))))
     codings)))

(deftest not-in-vs-test
  (given (issue/not-in-vs
          {:fhir/type :fhir/ValueSet}
          {:code "code-135027"})
    [:severity] := #fhir/code "error"
    [:code] := #fhir/code "code-invalid"
    [:details :coding] :? (tx-issue-type "not-in-vs")
    [:details :text] := #fhir/string "The provided code `code-135027` was not found in the provided value set."
    [:expression] := [#fhir/string "code"])

  (testing "details text"
    (testing "with code only"
      (testing "without value set URL"
        (given (issue/not-in-vs
                {:fhir/type :fhir/ValueSet}
                {:code "code-135027"})
          [:details :text] := #fhir/string "The provided code `code-135027` was not found in the provided value set."))

      (testing "with value set URL"
        (given (issue/not-in-vs
                {:fhir/type :fhir/ValueSet
                 :url #fhir/uri "value-set-135606"}
                {:code "code-135027"})
          [:details :text] := #fhir/string "The provided code `code-135027` was not found in the value set `value-set-135606`."))

      (testing "with value set URL and version"
        (given (issue/not-in-vs
                {:fhir/type :fhir/ValueSet
                 :url #fhir/uri "value-set-135606"
                 :version #fhir/string "version-135642"}
                {:code "code-135027"})
          [:details :text] := #fhir/string "The provided code `code-135027` was not found in the value set `value-set-135606|version-135642`.")))

    (testing "with code and system"
      (testing "without value set URL"
        (given (issue/not-in-vs
                {:fhir/type :fhir/ValueSet}
                {:code "code-135027"
                 :system "system-135913"})
          [:details :text] := #fhir/string "The provided code `system-135913#code-135027` was not found in the provided value set."))

      (testing "with value set URL"
        (given (issue/not-in-vs
                {:fhir/type :fhir/ValueSet
                 :url #fhir/uri "value-set-135606"}
                {:code "code-135027"
                 :system "system-135913"})
          [:details :text] := #fhir/string "The provided code `system-135913#code-135027` was not found in the value set `value-set-135606`."))

      (testing "with value set URL and version"
        (given (issue/not-in-vs
                {:fhir/type :fhir/ValueSet
                 :url #fhir/uri "value-set-135606"
                 :version #fhir/string "version-135642"}
                {:code "code-135027"
                 :system "system-135913"})
          [:details :text] := #fhir/string "The provided code `system-135913#code-135027` was not found in the value set `value-set-135606|version-135642`.")))))
