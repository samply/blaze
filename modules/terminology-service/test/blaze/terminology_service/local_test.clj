(ns blaze.terminology-service.local-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [parameter structure-definition-repo]]
   [blaze.fhir.util :as fu]
   [blaze.fhir.util-spec]
   [blaze.module.test-util :refer [given-failed-future given-failed-system with-system]]
   [blaze.path :refer [path]]
   [blaze.spec]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.local :as local]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system-spec]
   [blaze.terminology-service.local.code-system.loinc.spec]
   [blaze.terminology-service.local.code-system.sct-spec]
   [blaze.terminology-service.local.graph-spec]
   [blaze.terminology-service.local.spec]
   [blaze.terminology-service.local.validate-code-spec]
   [blaze.terminology-service.local.value-set-spec]
   [blaze.terminology-service.local.value-set.validate-code.issue-test :refer [tx-issue-type]]
   [blaze.test-util :as tu]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::local/graph-cache)}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}
   ::local/graph-cache {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {::ts/local nil}
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::ts/local {}}
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :graph-cache))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [::ts/local :node] ::invalid)
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [::ts/local :clock] ::invalid)
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [::ts/local :rng-fn] ::invalid)
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid graph-cache"
    (given-failed-system (assoc-in config [::ts/local :graph-cache] ::invalid)
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::local/graph-cache]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def bcp-13-config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::local/graph-cache)
    :enable-bcp-13 true}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}
   ::local/graph-cache {}))

(def bcp-47-config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::local/graph-cache)
    :enable-bcp-47 true}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}
   ::local/graph-cache {}))

;; put LOINC data into an opaque function, so that it can't be introspected
;; by dev tooling, because it's just large
(defonce loinc
  (let [{::cs/keys [loinc]} (ig/init {::cs/loinc {}})]
    (fn [] loinc)))

;; component for access of the opaque LOINC data
(defmethod ig/init-key ::loinc
  [_ _]
  (loinc))

(def loinc-config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::local/graph-cache)
    :loinc (ig/ref ::loinc)}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}
   ::local/graph-cache {}
   ::loinc {}))

(def sct-config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/incrementing-rng-fn)
    :graph-cache (ig/ref ::local/graph-cache)
    :sct (ig/ref ::cs/sct)}
   :blaze.test/fixed-clock {}
   :blaze.test/incrementing-rng-fn {}
   ::local/graph-cache {}
   ::cs/sct {:release-path (path "sct-release")}))

(def ucum-config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::local/graph-cache)
    :enable-ucum true}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}
   ::local/graph-cache {}))

(def complete-config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/incrementing-rng-fn)
    :graph-cache (ig/ref ::local/graph-cache)
    :enable-bcp-13 true
    :enable-bcp-47 true
    :enable-ucum true
    :loinc (ig/ref ::loinc)
    :sct (ig/ref ::cs/sct)}
   :blaze.test/fixed-clock {}
   :blaze.test/incrementing-rng-fn {}
   ::local/graph-cache {}
   ::loinc {}
   ::cs/sct {:release-path (path "sct-release")}))

(defn- pull-type-list [node type]
  @(d/pull-many node (vec (d/type-list (d/db node) type))))

(deftest init-bcp-13-test
  (with-system [{:blaze.db/keys [node]} bcp-13-config]
    (testing "the BCP-13 code system is available"
      (given (pull-type-list node "CodeSystem")
        count := 1
        [0 :id] := "AAAAAAAAAAAAAAAA"
        [0 :url] := #fhir/uri "urn:ietf:bcp:13"))

    (testing "can't delete the BCP-13 code system"
      (given-failed-future (d/transact node [[:delete "CodeSystem" "AAAAAAAAAAAAAAAA"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Can't delete the read-only resource `CodeSystem/AAAAAAAAAAAAAAAA`."))))

(deftest init-bcp-47-test
  (with-system [{:blaze.db/keys [node]} bcp-47-config]
    (testing "the BCP-47 code system is available"
      (given (pull-type-list node "CodeSystem")
        count := 1
        [0 :id] := "AAAAAAAAAAAAAAAA"
        [0 :url] := #fhir/uri "urn:ietf:bcp:47"))

    (testing "can't delete the BCP-47 code system"
      (given-failed-future (d/transact node [[:delete "CodeSystem" "AAAAAAAAAAAAAAAA"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Can't delete the read-only resource `CodeSystem/AAAAAAAAAAAAAAAA`."))))

(deftest init-loinc-test
  (with-system [{:blaze.db/keys [node]} loinc-config]
    (testing "the LOINC code system is available"
      (given (pull-type-list node "CodeSystem")
        count := 1
        [0 :id] := "AAAAAAAAAAAAAAAA"
        [0 :url] := #fhir/uri "http://loinc.org"))

    (testing "can't delete the LOINC code system"
      (given-failed-future (d/transact node [[:delete "CodeSystem" "AAAAAAAAAAAAAAAA"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Can't delete the read-only resource `CodeSystem/AAAAAAAAAAAAAAAA`."))))

(deftest init-sct-test
  (with-system [{:blaze.db/keys [node]} sct-config]
    (testing "SNOMED CT code systems are available"
      (given (pull-type-list node "CodeSystem")
        count := 25
        [0 :id] := "AAAAAAAAAAAAAAAA"
        [0 :url] := #fhir/uri "http://snomed.info/sct"))

    (testing "can't delete the first SNOMED CT code system"
      (given-failed-future (d/transact node [[:delete "CodeSystem" "AAAAAAAAAAAAAAAA"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Can't delete the read-only resource `CodeSystem/AAAAAAAAAAAAAAAA`."))))

(deftest init-ucum-test
  (with-system [{:blaze.db/keys [node]} ucum-config]
    (testing "the UCUM code system is available"
      (given (pull-type-list node "CodeSystem")
        count := 1
        [0 :id] := "AAAAAAAAAAAAAAAA"
        [0 :url] := #fhir/uri "http://unitsofmeasure.org"))

    (testing "can't delete the UCUM code system"
      (given-failed-future (d/transact node [[:delete "CodeSystem" "AAAAAAAAAAAAAAAA"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "Can't delete the read-only resource `CodeSystem/AAAAAAAAAAAAAAAA`."))))

(defn- uuid-urn? [s]
  (some? (re-matches #"urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" s)))

(defn- sort-expansion [value-set]
  (update-in value-set [:expansion :contains] (partial sort-by (comp :value :code))))

(defn- concept [code]
  (fn [concepts]
    (filterv #(= code (:value (:code %))) concepts)))

(deftest code-system-test
  (testing "with no code system"
    (with-system [{ts ::ts/local} config]
      (is (empty? @(ts/code-systems ts)))))

  (testing "with one code system"
    (testing "without version"
      (doseq [content ["complete" "fragment"]]
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-192435"
                   :content (type/code content)}]]]

          (given @(ts/code-systems ts)
            count := 1
            [0 :fhir/type] := :fhir.TerminologyCapabilities/codeSystem
            [0 :uri] := #fhir/canonical "system-192435"
            [0 :version count] := 1
            [0 :version 0 :fhir/type] := :fhir.TerminologyCapabilities.codeSystem/version
            [0 :version 0 :code] := nil
            [0 :version 0 :isDefault] := #fhir/boolean true))))

    (testing "with version"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-192435"
                 :version #fhir/string "version-121451"
                 :content #fhir/code "complete"}]]]

        (given @(ts/code-systems ts)
          [count] := 1
          [0 :fhir/type] := :fhir.TerminologyCapabilities/codeSystem
          [0 :uri] := #fhir/canonical "system-192435"
          [0 :version count] := 1
          [0 :version 0 :fhir/type] := :fhir.TerminologyCapabilities.codeSystem/version
          [0 :version 0 :code] := #fhir/string "version-121451"
          [0 :version 0 :isDefault] := #fhir/boolean true)))

    (testing "with two versions"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-192435"
                 :version #fhir/string "1.2.0"
                 :content #fhir/code "complete"}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri "system-192435"
                 :version #fhir/string "1.10.0"
                 :content #fhir/code "complete"}]]]

        (given @(ts/code-systems ts)
          [count] := 1
          [0 :fhir/type] := :fhir.TerminologyCapabilities/codeSystem
          [0 :uri] := #fhir/canonical "system-192435"
          [0 :version count] := 2
          [0 :version 0 :fhir/type] := :fhir.TerminologyCapabilities.codeSystem/version
          [0 :version 0 :code] := #fhir/string "1.10.0"
          [0 :version 0 :isDefault] := #fhir/boolean true
          [0 :version 1 :fhir/type] := :fhir.TerminologyCapabilities.codeSystem/version
          [0 :version 1 :code] := #fhir/string "1.2.0"
          [0 :version 1 :isDefault] := #fhir/boolean false))

      (testing "but the newest as draft"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-192435"
                   :version #fhir/string "1.2.0"
                   :status #fhir/code "active"
                   :content #fhir/code "complete"}]
            [:put {:fhir/type :fhir/CodeSystem :id "1"
                   :url #fhir/uri "system-192435"
                   :version #fhir/string "1.10.0"
                   :status #fhir/code "draft"
                   :content #fhir/code "complete"}]]]

          (given @(ts/code-systems ts)
            [count] := 1
            [0 :fhir/type] := :fhir.TerminologyCapabilities/codeSystem
            [0 :uri] := #fhir/canonical "system-192435"
            [0 :version count] := 2
            [0 :version 0 :fhir/type] := :fhir.TerminologyCapabilities.codeSystem/version
            [0 :version 0 :code] := #fhir/string "1.2.0"
            [0 :version 0 :isDefault] := #fhir/boolean true
            [0 :version 1 :fhir/type] := :fhir.TerminologyCapabilities.codeSystem/version
            [0 :version 1 :code] := #fhir/string "1.10.0"
            [0 :version 1 :isDefault] := #fhir/boolean false)))))

  (testing "with two code systems"
    (testing "without version"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-192435"
                 :content #fhir/code "complete"}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri "system-174248"
                 :content #fhir/code "complete"}]]]

        (given @(ts/code-systems ts)
          count := 2
          [0 :fhir/type] := :fhir.TerminologyCapabilities/codeSystem
          [0 :uri] := #fhir/canonical "system-192435"
          [0 :version count] := 1
          [0 :version 0 :fhir/type] := :fhir.TerminologyCapabilities.codeSystem/version
          [0 :version 0 :code] := nil
          [0 :version 0 :isDefault] := #fhir/boolean true
          [1 :fhir/type] := :fhir.TerminologyCapabilities/codeSystem
          [1 :uri] := #fhir/canonical "system-174248"
          [1 :version count] := 1
          [1 :version 0 :fhir/type] := :fhir.TerminologyCapabilities.codeSystem/version
          [1 :version 0 :code] := nil
          [1 :version 0 :isDefault] := #fhir/boolean true))))

  (testing "code system with content not-present is ignored"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-192435"
               :content #fhir/code "not-present"}]]]

      (is (empty? @(ts/code-systems ts))))))

(defn- code-system-validate-code [ts & nvs]
  (ts/code-system-validate-code ts (apply fu/parameters nvs)))

(deftest code-system-validate-code-fails-test
  (with-system [{ts ::ts/local} config]
    (testing "no parameters"
      (given-failed-future (code-system-validate-code ts)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing one of the parameters `code`, `coding` or `codeableConcept`."))

    (testing "missing url"
      (given-failed-future (code-system-validate-code ts
                             "code" #fhir/code "code-212423")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing both parameters `url` and `codeSystem`.")

      (given-failed-future (code-system-validate-code ts
                             "coding" #fhir/Coding{})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing all of the parameters `url`, `coding.system` and `codeSystem`.")

      (given-failed-future (code-system-validate-code ts
                             "codeableConcept"
                             #fhir/CodeableConcept{:coding [#fhir/Coding{}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing all of the parameters `url`, `codeableConcept.coding[0].system` and `codeSystem`."))

    (testing "incomplete coding"
      (given-failed-future (code-system-validate-code ts
                             "url" #fhir/uri "url-194718"
                             "coding" #fhir/Coding{})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing required parameter `coding.code`."))

    (testing "coding version mismatch"
      (given-failed-future (code-system-validate-code ts
                             "url" #fhir/uri "url-194718"
                             "version" #fhir/string "version-203135"
                             "coding" #fhir/Coding{:code #fhir/code "code-203107"
                                                   :version #fhir/string "version-203128"})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Parameter `version` differs from parameter `coding.version`."))

    (testing "incomplete codeableConcept"
      (given-failed-future (code-system-validate-code ts
                             "url" #fhir/uri "url-194718"
                             "codeableConcept" #fhir/CodeableConcept{})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Incorrect parameter `codeableConcept` with no coding.")

      (given-failed-future (code-system-validate-code ts
                             "url" #fhir/uri "url-194718"
                             "codeableConcept"
                             #fhir/CodeableConcept{:coding [#fhir/Coding{}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing required parameter `coding.code`."))

    (testing "codeableConcept with two codings"
      (given-failed-future (code-system-validate-code ts
                             "url" #fhir/uri "url-194718"
                             "codeableConcept"
                             #fhir/CodeableConcept
                              {:coding [#fhir/Coding{}
                                        #fhir/Coding{}]})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported parameter `codeableConcept` with more than one coding."))

    (testing "codeableConcept version mismatch"
      (given-failed-future (code-system-validate-code ts
                             "url" #fhir/uri "url-194718"
                             "version" #fhir/string "version-203135"
                             "codeableConcept"
                             #fhir/CodeableConcept
                              {:coding [#fhir/Coding{:code #fhir/code "code-203107"
                                                     :version #fhir/string "version-203128"}]})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Parameter `version` differs from parameter `codeableConcept.coding[0].version`."))

    (testing "both url and valueSet parameters"
      (given-failed-future (code-system-validate-code ts
                             "url" #fhir/uri "value-set-161213"
                             "codeSystem" {:fhir/type :fhir/ValueSet})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Both parameters `url` and `codeSystem` are given."))

    (testing "unsupported param"
      (given-failed-future (code-system-validate-code ts
                             "date" #fhir/dateTime #system/date-time "2025")
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported parameter `date`."))

    (testing "not found"
      (testing "url"
        (given-failed-future (code-system-validate-code ts
                               "url" #fhir/uri "url-194718"
                               "code" #fhir/code "code-083955")
          ::anom/category := ::anom/not-found
          ::anom/message := "The code system `url-194718` was not found."))

      (testing "url and version"
        (given-failed-future (code-system-validate-code ts
                               "url" #fhir/uri "url-144258"
                               "code" #fhir/code "code-083955"
                               "version" #fhir/string "version-144244")
          ::anom/category := ::anom/not-found
          ::anom/message := "The code system `url-144258|version-144244` was not found.")))

    (testing "with non-complete code system"
      (doseq [content ["not-present" "example" "supplement"]]
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content (type/code content)}]]]

          (given-failed-future (code-system-validate-code ts
                                 "url" #fhir/uri "system-115910"
                                 "code" #fhir/code "code-115927")
            ::anom/category := ::anom/conflict
            ::anom/message := (format "Can't use the code system `system-115910` because it's content is not one of complete, fragment. It's content is `%s`." content)))))))

(deftest code-system-validate-code-test
  (testing "status property with type Coding doesn't crash"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-154735"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "status"
                   :value
                   #fhir/Coding
                    {:system #fhir/uri "http://devices.fhir.org/CodeSystem/MDC-concept-status",
                     :code #fhir/code "published"}}]}]}]]]

      (given @(code-system-validate-code ts
                "url" #fhir/uri "system-115910"
                "code" #fhir/code "code-154735")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true)))

  (testing "with url"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :version #fhir/string "version-203456"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"
                 :display #fhir/string "display-112832"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-154735"
                 :display #fhir/string "display-154737"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "status"
                   :value #fhir/code "retired"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-155156"
                 :display #fhir/string "display-155159"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "inactive"
                   :value #fhir/boolean true}]}]}]]]

      (testing "existing code"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "system-115910"
                  "code" #fhir/code "code-115927")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-115927"
          [(parameter "system") 0 :value] := #fhir/uri "system-115910"
          [(parameter "display") 0 :value] := #fhir/string "display-112832")

        (testing "with version"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-115910"
                    "code" #fhir/code "code-115927"
                    "version" #fhir/string "version-203456")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-115927"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910"
            [(parameter "display") 0 :value] := #fhir/string "display-112832"))

        (testing "inactive"
          (testing "via status property"
            (given @(code-system-validate-code ts
                      "url" #fhir/uri "system-115910"
                      "code" #fhir/code "code-154735")
              :fhir/type := :fhir/Parameters
              [(parameter "result") 0 :value] := #fhir/boolean true
              [(parameter "code") 0 :value] := #fhir/code "code-154735"
              [(parameter "system") 0 :value] := #fhir/uri "system-115910"
              [(parameter "display") 0 :value] := #fhir/string "display-154737"
              [(parameter "inactive") 0 :value] := #fhir/boolean true))

          (testing "via inactive property"
            (given @(code-system-validate-code ts
                      "url" #fhir/uri "system-115910"
                      "code" #fhir/code "code-155156")
              :fhir/type := :fhir/Parameters
              [(parameter "result") 0 :value] := #fhir/boolean true
              [(parameter "code") 0 :value] := #fhir/code "code-155156"
              [(parameter "system") 0 :value] := #fhir/uri "system-115910"
              [(parameter "display") 0 :value] := #fhir/string "display-155159"
              [(parameter "inactive") 0 :value] := #fhir/boolean true))))

      (testing "non-existing code"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "system-115910"
                  "code" #fhir/code "code-153948")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "Unknown code `code-153948` was not found in the code system `system-115910|version-203456`."
          [(parameter "code") 0 :value] := #fhir/code "code-153948"
          [(parameter "system") 0 :value] := #fhir/uri "system-115910"
          [(parameter "version") 0 :value] := #fhir/string "version-203456"))

      (testing "existing coding"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "system-115910"
                  "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                        :code #fhir/code "code-115927"})
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-115927"
          [(parameter "system") 0 :value] := #fhir/uri "system-115910")

        (testing "with top-level version"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-115910"
                    "version" #fhir/string "version-203456"
                    "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                          :code #fhir/code "code-115927"})
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-115927"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910"))

        (testing "with coding version"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-115910"
                    "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                          :code #fhir/code "code-115927"
                                          :version #fhir/string "version-203456"})
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-115927"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910"))

        (testing "with top-level and coding version"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-115910"
                    "version" #fhir/string "version-203456"
                    "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                          :code #fhir/code "code-115927"
                                          :version #fhir/string "version-203456"})
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-115927"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910")))

      (testing "existing codeableConcept"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "system-115910"
                  "codeableConcept"
                  #fhir/CodeableConcept
                   {:coding
                    [#fhir/Coding{:system #fhir/uri "system-115910"
                                  :code #fhir/code "code-115927"}]})
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-115927"
          [(parameter "system") 0 :value] := #fhir/uri "system-115910")

        (testing "with top-level version"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-115910"
                    "version" #fhir/string "version-203456"
                    "codeableConcept"
                    #fhir/CodeableConcept
                     {:coding
                      [#fhir/Coding{:system #fhir/uri "system-115910"
                                    :code #fhir/code "code-115927"}]})
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-115927"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910"))

        (testing "with coding version"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-115910"
                    "codeableConcept"
                    #fhir/CodeableConcept
                     {:coding
                      [#fhir/Coding{:system #fhir/uri "system-115910"
                                    :code #fhir/code "code-115927"
                                    :version #fhir/string "version-203456"}]})
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-115927"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910"))

        (testing "with top-level and coding version"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-115910"
                    "version" #fhir/string "version-203456"
                    "codeableConcept"
                    #fhir/CodeableConcept
                     {:coding
                      [#fhir/Coding{:system #fhir/uri "system-115910"
                                    :code #fhir/code "code-115927"
                                    :version #fhir/string "version-203456"}]})
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-115927"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910")))

      (testing "non-existing coding"
        (testing "with non-existing system"
          (given-failed-future (code-system-validate-code ts
                                 "url" #fhir/uri "system-115910"
                                 "coding" #fhir/Coding{:system #fhir/uri "system-170454"
                                                       :code #fhir/code "code-115927"})
            ::anom/category := ::anom/incorrect
            ::anom/message := "Parameter `url` differs from parameter `coding.system`."))

        (testing "with non-existing code"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-115910"
                    "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                          :code #fhir/code "code-153948"})
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "Unknown code `code-153948` was not found in the code system `system-115910|version-203456`."
            [(parameter "code") 0 :value] := #fhir/code "code-153948"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910"
            [(parameter "version") 0 :value] := #fhir/string "version-203456")))

      (testing "non-existing codeableConcept"
        (testing "with non-existing system"
          (given-failed-future (code-system-validate-code ts
                                 "url" #fhir/uri "system-115910"
                                 "codeableConcept"
                                 #fhir/CodeableConcept
                                  {:coding
                                   [#fhir/Coding{:system #fhir/uri "system-170454"
                                                 :code #fhir/code "code-115927"}]})
            ::anom/category := ::anom/incorrect
            ::anom/message := "Parameter `url` differs from parameter `codeableConcept.coding[0].system`."))

        (testing "with non-existing code"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-115910"
                    "codeableConcept"
                    #fhir/CodeableConcept
                     {:coding
                      [#fhir/Coding{:system #fhir/uri "system-115910"
                                    :code #fhir/code "code-153948"}]})
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "Unknown code `code-153948` was not found in the code system `system-115910|version-203456`."
            [(parameter "code") 0 :value] := #fhir/code "code-153948"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910"
            [(parameter "version") 0 :value] := #fhir/string "version-203456"))))

    (testing "multiple code-systems with the same url"
      (testing "the code-system with the higher version number is used"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-120349"
                   :version #fhir/string "1.1.0"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-120333"}]}]
            [:put {:fhir/type :fhir/CodeSystem :id "1"
                   :url #fhir/uri "system-120349"
                   :version #fhir/string "1.0.0"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-120413"}]}]]]

          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-120349"
                    "code" #fhir/code "code-120333")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-120333"
            [(parameter "system") 0 :value] := #fhir/uri "system-120349")

          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-120349"
                    "code" #fhir/code "code-120413")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "code") 0 :value] := #fhir/code "code-120413"
            [(parameter "system") 0 :value] := #fhir/uri "system-120349")))

      (testing "the active code-system is used"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-120349"
                   :status #fhir/code "active"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-120333"}]}]
            [:put {:fhir/type :fhir/CodeSystem :id "1"
                   :url #fhir/uri "system-120349"
                   :status #fhir/code "draft"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-120413"}]}]]]

          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-120349"
                    "code" #fhir/code "code-120333")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-120333"
            [(parameter "system") 0 :value] := #fhir/uri "system-120349")

          (given @(code-system-validate-code ts
                    "url" #fhir/uri "system-120349"
                    "code" #fhir/code "code-120413")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "code") 0 :value] := #fhir/code "code-120413"
            [(parameter "system") 0 :value] := #fhir/uri "system-120349")))))

  (testing "with code-system"
    (with-system [{ts ::ts/local} config]
      (testing "existing code"
        (given @(code-system-validate-code ts
                  "codeSystem"
                  {:fhir/type :fhir/CodeSystem
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"}]}
                  "code" #fhir/code "code-115927")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-115927")

        (testing "with url"
          (given @(code-system-validate-code ts
                    "codeSystem"
                    {:fhir/type :fhir/CodeSystem
                     :url #fhir/uri "system-115910"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"}]}
                    "code" #fhir/code "code-115927")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-115927"
            [(parameter "system") 0 :value] := #fhir/uri "system-115910")))

      (testing "non-existing code"
        (given @(code-system-validate-code ts
                  "codeSystem"
                  {:fhir/type :fhir/CodeSystem
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"}]}
                  "code" #fhir/code "code-153948")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "Unknown code `code-153948` was not found in the provided code system."
          [(parameter "code") 0 :value] := #fhir/code "code-153948")

        (testing "with url"
          (given @(code-system-validate-code ts
                    "codeSystem"
                    {:fhir/type :fhir/CodeSystem
                     :url #fhir/uri "system-115910"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"}]}
                    "code" #fhir/code "code-153948")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "Unknown code `code-153948` was not found in the code system `system-115910`.")))

      (testing "existing coding"
        (given @(code-system-validate-code ts
                  "codeSystem"
                  {:fhir/type :fhir/CodeSystem
                   :url #fhir/uri "system-172718"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-172653"}]}
                  "coding" #fhir/Coding{:system #fhir/uri "system-172718"
                                        :code #fhir/code "code-172653"})
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-172653"
          [(parameter "system") 0 :value] := #fhir/uri "system-172718"))))

  (testing "with coding only"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]]]

      (given @(code-system-validate-code ts
                "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                      :code #fhir/code "code-115927"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-115927"
        [(parameter "system") 0 :value] := #fhir/uri "system-115910"))

    (testing "with version"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri "system-115910"
                 :version #fhir/string "version-124939"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-124951"}]}]]]

        (given @(code-system-validate-code ts
                  "coding" #fhir/Coding{:system #fhir/uri "system-115910"
                                        :version #fhir/string "version-124939"
                                        :code #fhir/code "code-124951"})
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-124951"
          [(parameter "system") 0 :value] := #fhir/uri "system-115910"
          [(parameter "version") 0 :value] := #fhir/string "version-124939")))))

(deftest code-system-validate-code-bcp-13-test
  (with-system [{ts ::ts/local} bcp-13-config]
    (testing "existing code"
      (doseq [[code]
              [["text/plain"]
               ["application/fhir+json"]]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "urn:ietf:bcp:13"
                  "code" (type/code code))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := #fhir/uri "urn:ietf:bcp:13"
          [(parameter "version") 0 :value] := #fhir/string "1.0.0")))

    (testing "non-existing code"
      (doseq [code ["text-plain" "xx/yyy"]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "urn:ietf:bcp:13"
                  "code" (type/code code))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unknown code `%s` was not found in the code system `urn:ietf:bcp:13|1.0.0`." code))
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := #fhir/uri "urn:ietf:bcp:13"
          [(parameter "version") 0 :value] := #fhir/string "1.0.0")))))

(deftest code-system-validate-code-bcp-47-test
  (with-system [{ts ::ts/local} bcp-47-config]
    (testing "existing code"
      (doseq [[code display]
              [["de" "German"]
               ["de-DE" "German (Region=Germany)"]
               ["nn-NO" "Norwegian Nynorsk (Region=Norway)"]
               ["dje-Latn-NE" "Zarma (Script=Latin, Region=Niger)"]
               ["sr-Cyrl-ME" "Serbian (Script=Cyrillic, Region=Montenegro)"]]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "urn:ietf:bcp:47"
                  "code" (type/code code))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := #fhir/uri "urn:ietf:bcp:47"
          [(parameter "version") 0 :value] := #fhir/string "1.0.0"
          [(parameter "display") 0 :value] := (type/string display))))

    (testing "non-existing code"
      (doseq [code ["xx-yyy" "de-XYZ"]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "urn:ietf:bcp:47"
                  "code" (type/code code))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unknown code `%s` was not found in the code system `urn:ietf:bcp:47|1.0.0`." code))
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := #fhir/uri "urn:ietf:bcp:47"
          [(parameter "version") 0 :value] := #fhir/string "1.0.0")))))

(deftest code-system-validate-code-loinc-test
  (with-system [{ts ::ts/local} loinc-config]
    (testing "existing code"
      (doseq [[code display]
              [["718-7" "Hemoglobin [Mass/volume] in Blood"]
               ["LA26421-0" "Consider alternative medication"]]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://loinc.org"
                  "code" (type/code code))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "version") 0 :value] := #fhir/string "2.78"
          [(parameter "display") 0 :value] := (type/string display)))

      (testing "with version"
        (doseq [[code display]
                [["718-7" "Hemoglobin [Mass/volume] in Blood"]
                 ["LA26421-0" "Consider alternative medication"]]]
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "http://loinc.org"
                    "code" (type/code code)
                    "version" #fhir/string "2.78")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := (type/code code)
            [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
            [(parameter "version") 0 :value] := #fhir/string "2.78"
            [(parameter "display") 0 :value] := (type/string display))))

      (testing "wrong display"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://loinc.org"
                  "code" #fhir/code "718-7"
                  "display" #fhir/string "wrong")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "Invalid display `wrong` for code `http://loinc.org#718-7`. A valid display is `Hemoglobin [Mass/volume] in Blood`."
          [(parameter "code") 0 :value] := #fhir/code "718-7"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "version") 0 :value] := #fhir/string "2.78"
          [(parameter "display") 0 :value] := #fhir/string "Hemoglobin [Mass/volume] in Blood"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `wrong` for code `http://loinc.org#718-7`. A valid display is `Hemoglobin [Mass/volume] in Blood`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "display"]))

      (testing "inactive"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://loinc.org"
                  "code" #fhir/code "1009-0")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "1009-0"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "version") 0 :value] := #fhir/string "2.78"
          [(parameter "display") 0 :value] := #fhir/string "Deprecated Direct antiglobulin test.poly specific reagent [Presence] on Red Blood Cells"
          [(parameter "inactive") 0 :value] := #fhir/boolean true)))

    (testing "non-existing code"
      (doseq [code ["non-existing" "0815" "718-8"]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://loinc.org"
                  "code" (type/code code))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unknown code `%s` was not found in the code system `http://loinc.org|2.78`." code))
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "version") 0 :value] := #fhir/string "2.78")))

    (testing "non-existing system version"
      (given-failed-future
       (code-system-validate-code ts
         "url" #fhir/uri "http://loinc.org"
         "code" #fhir/code "code-160652"
         "version" #fhir/string "non-existing-160221")
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `http://loinc.org|non-existing-160221` was not found."))))

(deftest code-system-validate-code-sct-test
  (with-system [{ts ::ts/local} sct-config]
    (testing "existing code"
      (doseq [[input-version
               output-version]
              [[nil
                "http://snomed.info/sct/900000000000207008/version/20241001"]
               ["http://snomed.info/sct/900000000000207008"
                "http://snomed.info/sct/900000000000207008/version/20241001"]
               ["http://snomed.info/sct/900000000000207008/version/20241001"
                "http://snomed.info/sct/900000000000207008/version/20241001"]
               ["http://snomed.info/sct/11000274103"
                "http://snomed.info/sct/11000274103/version/20241115"]
               ["http://snomed.info/sct/11000274103/version/20241115"
                "http://snomed.info/sct/11000274103/version/20241115"]
               ["http://snomed.info/sct/11000274103/version/20240515"
                "http://snomed.info/sct/11000274103/version/20240515"]]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://snomed.info/sct"
                  "code" #fhir/code "441510007"
                  "version" (some-> input-version type/string))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value :value] := output-version
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"))

      (testing "fully specified name display"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://snomed.info/sct"
                  "code" #fhir/code "441510007"
                  "display" #fhir/string "Blood specimen with anticoagulant (specimen)")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"))

      (testing "synonym display"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://snomed.info/sct"
                  "code" #fhir/code "441510007"
                  "display" #fhir/string "Blood specimen with anticoagulant")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant")

        (testing "Germany module"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "http://snomed.info/sct"
                    "code" #fhir/code "440500007"
                    "version" #fhir/string "http://snomed.info/sct/11000274103"
                    "display" #fhir/string "Trockenblutkarte")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "440500007"
            [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
            [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/11000274103/version/20241115"
            [(parameter "display") 0 :value] := #fhir/string "Dried blood spot specimen")

          (testing "displayLanguage = de"
            (given @(code-system-validate-code ts
                      "url" #fhir/uri "http://snomed.info/sct"
                      "code" #fhir/code "440500007"
                      "version" #fhir/string "http://snomed.info/sct/11000274103"
                      "display" #fhir/string "Trockenblutkarte"
                      "displayLanguage" #fhir/code "de")
              :fhir/type := :fhir/Parameters
              [(parameter "result") 0 :value] := #fhir/boolean true
              [(parameter "code") 0 :value] := #fhir/code "440500007"
              [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
              [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/11000274103/version/20241115"
              [(parameter "display") 0 :value] := #fhir/string "Trockenblutkarte"))))

      (testing "wrong display"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://snomed.info/sct"
                  "code" #fhir/code "441510007"
                  "display" #fhir/string "wrong")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "Invalid display `wrong` for code `http://snomed.info/sct#441510007`. A valid display is `Blood specimen with anticoagulant`."
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `wrong` for code `http://snomed.info/sct#441510007`. A valid display is `Blood specimen with anticoagulant`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "display"])

        (testing "Germany display in core module"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri "http://snomed.info/sct"
                    "code" #fhir/code "440500007"
                    "display" #fhir/string "Trockenblutkarte")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "Invalid display `Trockenblutkarte` for code `http://snomed.info/sct#440500007`. A valid display is `Dried blood spot specimen`."
            [(parameter "code") 0 :value] := #fhir/code "440500007"
            [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
            [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
            [(parameter "display") 0 :value] := #fhir/string "Dried blood spot specimen"
            [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
            [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
            [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
            [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `Trockenblutkarte` for code `http://snomed.info/sct#440500007`. A valid display is `Dried blood spot specimen`."
            [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "display"])))

      (testing "inactive"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://snomed.info/sct"
                  "code" #fhir/code "860958002")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "860958002"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "display") 0 :value] := #fhir/string "Temperature of blood"
          [(parameter "inactive") 0 :value] := #fhir/boolean true)))

    (testing "non-existing code"
      (doseq [code ["non-existing" "0815" "441510008"]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://snomed.info/sct"
                  "code" (type/code code))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unknown code `%s` was not found in the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20241001`." code))
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001")))

    (testing "existing coding"
      (given @(code-system-validate-code ts
                "url" #fhir/uri "http://snomed.info/sct"
                "coding" #fhir/Coding{:system #fhir/uri "http://snomed.info/sct" :code #fhir/code "441510007"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "441510007"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
        [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"))

    (testing "non-existing coding"
      (given @(code-system-validate-code ts
                "url" #fhir/uri "http://snomed.info/sct"
                "coding" #fhir/Coding{:system #fhir/uri "http://snomed.info/sct" :code #fhir/code "non-existing"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Unknown code `non-existing` was not found in the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20241001`."))

    (testing "non-existing system version"
      (given-failed-future
       (code-system-validate-code ts
         "url" #fhir/uri "http://snomed.info/sct"
         "code" #fhir/code "code-160626"
         "version" #fhir/string "non-existing-160221")
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `http://snomed.info/sct|non-existing-160221` was not found."))))

(deftest code-system-validate-code-ucum-test
  (with-system [{ts ::ts/local} ucum-config]
    (testing "existing code"
      (given @(code-system-validate-code ts
                "url" #fhir/uri "http://unitsofmeasure.org"
                "code" #fhir/code "s")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "s"
        [(parameter "system") 0 :value] := #fhir/uri "http://unitsofmeasure.org"
        [(parameter "version") 0 :value] := #fhir/string "2013.10.21"))

    (testing "non-existing code"
      (given @(code-system-validate-code ts
                "url" #fhir/uri "http://unitsofmeasure.org"
                "code" #fhir/code "non-existing")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Unknown code `non-existing` was not found in the code system `http://unitsofmeasure.org|2013.10.21`."
        [(parameter "code") 0 :value] := #fhir/code "non-existing"
        [(parameter "system") 0 :value] := #fhir/uri "http://unitsofmeasure.org"
        [(parameter "version") 0 :value] := #fhir/string "2013.10.21"))

    (testing "existing coding"
      (given @(code-system-validate-code ts
                "url" #fhir/uri "http://unitsofmeasure.org"
                "coding" #fhir/Coding{:system #fhir/uri "http://unitsofmeasure.org" :code #fhir/code "km"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "km"
        [(parameter "system") 0 :value] := #fhir/uri "http://unitsofmeasure.org"
        [(parameter "version") 0 :value] := #fhir/string "2013.10.21"))

    (testing "non-existing coding"
      (testing "with non-existing code"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri "http://unitsofmeasure.org"
                  "coding" #fhir/Coding{:system #fhir/uri "http://unitsofmeasure.org" :code #fhir/code "non-existing"})
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "Unknown code `non-existing` was not found in the code system `http://unitsofmeasure.org|2013.10.21`."
          [(parameter "code") 0 :value] := #fhir/code "non-existing"
          [(parameter "system") 0 :value] := #fhir/uri "http://unitsofmeasure.org"
          [(parameter "version") 0 :value] := #fhir/string "2013.10.21")))))

(defn- expand-value-set [ts & nvs]
  (ts/expand-value-set ts (apply fu/parameters nvs)))

(deftest expand-value-set-fails-test
  (with-system-data [{ts ::ts/local} complete-config]
    [[[:put {:fhir/type :fhir/CodeSystem :id "0"
             :url #fhir/uri "system-182822"
             :content #fhir/code "complete"
             :concept
             [{:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-182832"
               :display #fhir/string "display-182717"}]}]]]

    (testing "no parameters"
      (given-failed-future (expand-value-set ts)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing both parameters `url` and `valueSet`."))

    (testing "unsupported parameter"
      (given-failed-future (expand-value-set ts
                             "excludePostCoordinated" #fhir/string "foo")
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported parameter `excludePostCoordinated`."))

    (testing "invalid negative parameter count"
      (given-failed-future (expand-value-set ts
                             "count" #fhir/integer{:id "0"})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid value for parameter `count`. Missing value."))

    (testing "invalid negative parameter count"
      (given-failed-future (expand-value-set ts
                             "count" #fhir/integer -1)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid value for parameter `count`. Has to be a non-negative integer."))

    (testing "invalid non-zero parameter offset"
      (given-failed-future (expand-value-set ts
                             "offset" #fhir/integer 1)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid non-zero value for parameter `offset`."))

    (testing "both url and valueSet parameters"
      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri "value-set-161213"
                             "valueSet" {:fhir/type :fhir/ValueSet})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Both parameters `url` and `valueSet` are given."))

    (testing "unsupported param"
      (given-failed-future (expand-value-set ts
                             "date" #fhir/dateTime #system/date-time "2025")
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported parameter `date`."))

    (testing "not found"
      (testing "url"
        (given-failed-future (expand-value-set ts
                               "url" #fhir/uri "url-194718")
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-194718` was not found."))

      (testing "url and version"
        (given-failed-future (expand-value-set ts
                               "url" #fhir/uri "url-144258"
                               "valueSetVersion" #fhir/string "version-144244")
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-144258|version-144244` was not found.")))

    (testing "unsupported filter operator"
      (doseq [system ["system-182822" "urn:ietf:bcp:13"
                      "http://unitsofmeasure.org" "http://loinc.org"
                      "http://snomed.info/sct"]]
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system (type/uri system)
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property #fhir/code "property-160019"
                 :op #fhir/code "op-unsupported-160011"
                 :value #fhir/string "value-160032"}]}]}})
          ::anom/category := ::anom/unsupported
          ::anom/message := (format "Error while expanding the provided value set. Unsupported filter operator `op-unsupported-160011` in code system `%s`." system))))

    (testing "missing filter property"
      (doseq [[system op]
              [["system-182822" "is-a"]
               ["system-182822" "descendent-of"]
               ["system-182822" "exists"]
               ["system-182822" "="]
               ["system-182822" "regex"]
               ["http://loinc.org" "="]
               ["http://loinc.org" "regex"]
               ["http://snomed.info/sct" "is-a"]
               ["http://snomed.info/sct" "descendent-of"]
               ["http://snomed.info/sct" "="]]]
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system (type/uri system)
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :op (type/code op)
                 :value #fhir/string "value-162629"}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Error while expanding the provided value set. Missing %s filter property in code system `%s`." op system))))

    (testing "unsupported filter property"
      (doseq [[system op]
              [["system-182822" "is-a"]
               ["system-182822" "descendent-of"]
               ["http://loinc.org" "="]
               ["http://loinc.org" "regex"]
               ["http://snomed.info/sct" "is-a"]
               ["http://snomed.info/sct" "descendent-of"]
               ["http://snomed.info/sct" "="]]]
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system (type/uri system)
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property #fhir/code "property-163943"
                 :op (type/code op)}]}]}})
          ::anom/category := ::anom/unsupported
          ::anom/message := (format "Error while expanding the provided value set. Unsupported %s filter property `property-163943` in code system `%s`." op system))))

    (testing "missing filter value"
      (doseq [[system op property]
              [["system-182822" "is-a" "concept"]
               ["system-182822" "descendent-of" "concept"]
               ["system-182822" "exists" "property-arbitrary-161647"]
               ["system-182822" "=" "property-arbitrary-161647"]
               ["system-182822" "regex" "property-arbitrary-161647"]
               ["http://loinc.org" "=" "COMPONENT"]
               ["http://loinc.org" "=" "PROPERTY"]
               ["http://loinc.org" "=" "TIME_ASPCT"]
               ["http://loinc.org" "=" "SYSTEM"]
               ["http://loinc.org" "=" "SCALE_TYP"]
               ["http://loinc.org" "=" "METHOD_TYP"]
               ["http://loinc.org" "=" "CLASS"]
               ["http://loinc.org" "=" "STATUS"]
               ["http://loinc.org" "=" "CLASSTYPE"]
               ["http://loinc.org" "=" "ORDER_OBS"]
               ["http://loinc.org" "regex" "COMPONENT"]
               ["http://loinc.org" "regex" "PROPERTY"]
               ["http://loinc.org" "regex" "TIME_ASPCT"]
               ["http://loinc.org" "regex" "SYSTEM"]
               ["http://loinc.org" "regex" "SCALE_TYP"]
               ["http://loinc.org" "regex" "METHOD_TYP"]
               ["http://loinc.org" "regex" "CLASS"]
               ["http://loinc.org" "regex" "STATUS"]
               ["http://loinc.org" "regex" "ORDER_OBS"]
               ["http://snomed.info/sct" "is-a" "concept"]
               ["http://snomed.info/sct" "descendent-of" "concept"]
               ["http://snomed.info/sct" "=" "parent"]
               ["http://snomed.info/sct" "=" "child"]]]
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system (type/uri system)
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property (type/code property)
                 :op (type/code op)}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Error while expanding the provided value set. Missing %s %s filter value in code system `%s`." property op system))))

    (testing "invalid filter value"
      (doseq [[system op property msg]
              [["system-182822" "exists" "property-arbitrary-161647" "Should be one of `true` or `false`."]
               ["system-182822" "regex" "property-arbitrary-161647" "Should be a valid regex pattern."]
               ["http://loinc.org" "=" "STATUS"]
               ["http://loinc.org" "=" "CLASSTYPE"]
               ["http://loinc.org" "=" "ORDER_OBS"]
               ["http://snomed.info/sct" "is-a" "concept"]
               ["http://snomed.info/sct" "descendent-of" "concept"]
               ["http://snomed.info/sct" "=" "parent"]
               ["http://snomed.info/sct" "=" "child"]]]
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system (type/uri system)
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property (type/code property)
                 :op (type/code op)
                 :value #fhir/string "value-invalid-[-174601"}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := (format (cond-> "Error while expanding the provided value set. Invalid %s %s filter value `value-invalid-[-174601` in code system `%s`." msg (str " " msg)) property op system)))))

  (testing "empty include"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri "value-set-135750")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-135750`. Missing system or valueSet."
        :t := 1)))

  (testing "code system not found"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri "value-set-135750")
        ::anom/category := ::anom/not-found
        ::anom/message := "Error while expanding the value set `value-set-135750`. The code system `system-115910` was not found."
        :t := 1))

    (testing "with version"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"
                    :version #fhir/string "version-093818"}]}}]]]

        (given-failed-future (expand-value-set ts
                               "url" #fhir/uri "value-set-135750")
          ::anom/category := ::anom/not-found
          ::anom/message := "Error while expanding the value set `value-set-135750`. The code system `system-115910|version-093818` was not found."
          :t := 1))

      (testing "special * value is unsupported"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :version #fhir/string "*"}]}}]]]

          (given-failed-future (expand-value-set ts
                                 "url" #fhir/uri "value-set-135750")
            ::anom/category := ::anom/unsupported
            ::anom/message := "Error while expanding the value set `value-set-135750`. Expanding the code system `system-115910` in all versions is unsupported."
            :t := 1)))))

  (testing "fails with value set ref and system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"
                  :valueSet [#fhir/canonical "value-set-135750"]}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri "value-set-161213")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-161213`. Incorrect combination of system and valueSet."
        :t := 1)))

  (testing "fails with concept and filter"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "code-163444"}]
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "property-160019"
                    :op #fhir/code "op-unknown-160011"
                    :value #fhir/string "value-160032"}]}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri "value-set-161213")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-161213`. Incorrect combination of concept and filter."
        :t := 1)))

  (testing "fails on non-complete code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :content #fhir/code "example"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-170447"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "code-163824"}]}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "2"
               :url #fhir/uri "value-set-170829"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "property-160019"
                    :op #fhir/code "op-unknown-160011"
                    :value #fhir/string "value-160032"}]}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri "value-set-161213")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-161213`. Can't use the code system `system-180814` because it's content is not one of complete, fragment. It's content is `example`."
        :t := 1)

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri "value-set-170447")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-170447`. Can't use the code system `system-180814` because it's content is not one of complete, fragment. It's content is `example`."
        :t := 1)

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri "value-set-170829")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-170829`. Can't use the code system `system-180814` because it's content is not one of complete, fragment. It's content is `example`."
        :t := 1))))

(deftest expand-value-set-existing-expansion-test
  (testing "retains already existing expansion"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/ValueSet :id "id-144002"
               :url #fhir/uri "value-set-135750"
               :version #fhir/string "version-143955"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}
               :expansion
               {:fhir/type :fhir.ValueSet/expansion
                :identifier #fhir/uri "urn:uuid:b01db38a-3ec8-4167-a279-0bb1200624a8"
                :timestamp #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
                :contains
                [{:fhir/type :fhir.ValueSet.expansion/contains
                  :system #fhir/uri "system-115910"
                  :code #fhir/code "code-115927"
                  :display #fhir/string "display-115927"}]}}]]]

      (doseq [params [["url" #fhir/uri "value-set-135750"]
                      ["url" #fhir/uri "value-set-135750"
                       "valueSetVersion" #fhir/string "version-143955"]]]
        (given @(apply expand-value-set ts params)
          :fhir/type := :fhir/ValueSet
          [:expansion :identifier] := #fhir/uri "urn:uuid:b01db38a-3ec8-4167-a279-0bb1200624a8"
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927"
          [:expansion :contains 0 :display] := #fhir/string "display-115927")))))

(deftest expand-value-set-include-concept-test
  (testing "with one code system"
    (testing "with one code"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (given @(expand-value-set ts
                  "url" #fhir/uri "value-set-135750"
                  "unknown-parameter" #fhir/string "is-ignored")
          :fhir/type := :fhir/ValueSet
          [:expansion :parameter count] := 1
          [:expansion (parameter "used-codesystem") 0 :value] := #fhir/uri "system-115910"
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false))

      (testing "including designations"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"
                     :designation
                     [{:fhir/type :fhir.CodeSystem.concept/designation
                       :value #fhir/string "designation-011441"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"}]}}]]]

          (given @(expand-value-set ts
                    "url" #fhir/uri "value-set-135750"
                    "includeDesignations" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion (parameter "includeDesignations") 0 :value] := #fhir/boolean true
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927"
            [:expansion :contains 0 :designation 0 :value] := #fhir/string "designation-011441")))

      (testing "including properties"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code "status"
                       :value #fhir/code "active"}
                      {:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code "property-034158"
                       :value #fhir/code "value-034206"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"}]}}]]]

          (given @(expand-value-set ts
                    "url" #fhir/uri "value-set-135750"
                    "property" #fhir/string "status"
                    "property" #fhir/string "property-034158")
            :fhir/type := :fhir/ValueSet
            [:expansion :property count] := 2
            [:expansion :property 0 :code] := #fhir/code "status"
            [:expansion :property 0 :uri] := #fhir/uri "http://hl7.org/fhir/concept-properties#status"
            [:expansion :property 1 :code] := #fhir/code "property-034158"
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927"
            [:expansion :contains 0 :property count] := 2
            [:expansion :contains 0 :property 0 :code] := #fhir/code "status"
            [:expansion :contains 0 :property 0 :value] := #fhir/code "active"
            [:expansion :contains 0 :property 1 :code] := #fhir/code "property-034158"
            [:expansion :contains 0 :property 1 :value] := #fhir/code "value-034206"))

        (testing "special definition property (FHIR-43519)"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri "system-115910"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"
                       :definition #fhir/string "definition-143747"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri "value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-115910"}]}}]]]

            (given @(expand-value-set ts
                      "url" #fhir/uri "value-set-135750"
                      "property" #fhir/string "definition")
              :fhir/type := :fhir/ValueSet
              [:expansion :property count] := 1
              [:expansion :property 0 :code] := #fhir/code "definition"
              [:expansion :property 0 :uri] := #fhir/uri "http://hl7.org/fhir/concept-properties#definition"
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains 0 :code] := #fhir/code "code-115927"
              [:expansion :contains 0 :property count] := 1
              [:expansion :contains 0 :property 0 :code] := #fhir/code "definition"
              [:expansion :contains 0 :property 0 :value] := #fhir/string "definition-143747"))))

      (testing "with versions"
        (testing "choosing an explicit version"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "1.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "1"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "2.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-092722"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "2"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "3.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115357"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri "value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-115910"
                        :version #fhir/string "2.0.0"}]}}]]]

            (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
              :fhir/type := :fhir/ValueSet
              [:expansion (parameter "version") 0 :value] := #fhir/uri "system-115910|2.0.0"
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains 0 :code] := #fhir/code "code-092722"
              [:expansion :contains 0 #(contains? % :display)] := false)))

        (testing "choosing the newest version by default"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "1.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "1"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "2.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-092722"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "2"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "3.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115357"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri "value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-115910"}]}}]]]

            (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
              :fhir/type := :fhir/ValueSet
              [:expansion (parameter "version") 0 :value] := #fhir/uri "system-115910|3.0.0"
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains 0 :code] := #fhir/code "code-115357"
              [:expansion :contains 0 #(contains? % :display)] := false)))

        (testing "choosing the version by parameter"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "1.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "1"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "2.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-092722"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "2"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "3.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115357"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri "value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-115910"}]}}]]]

            (given @(expand-value-set ts
                      "url" #fhir/uri "value-set-135750"
                      "system-version" #fhir/canonical "system-115910|2.0.0")
              :fhir/type := :fhir/ValueSet
              [:expansion (parameter "version") 0 :value] := #fhir/uri "system-115910|2.0.0"
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains 0 :code] := #fhir/code "code-092722"
              [:expansion :contains 0 #(contains? % :display)] := false)))))

    (testing "with two codes"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-163444"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false
          [:expansion :contains 1 :system] := #fhir/uri "system-115910"
          [:expansion :contains 1 :code] := #fhir/code "code-163444"
          [:expansion :contains 1 #(contains? % :display)] := false))

      (testing "include only one code"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-163444"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-163444"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-163444"
            [:expansion :contains 0 #(contains? % :display)] := false))

        (testing "including designations"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri "system-115910"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"}
                      {:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-163444"
                       :designation
                       [{:fhir/type :fhir.CodeSystem.concept/designation
                         :value #fhir/string "designation-011441"}]}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri "value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-115910"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "code-163444"}]}]}}]]]

            (given @(expand-value-set ts
                      "url" #fhir/uri "value-set-135750"
                      "includeDesignations" #fhir/boolean true)
              :fhir/type := :fhir/ValueSet
              [:expansion (parameter "includeDesignations") 0 :value] := #fhir/boolean true
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "system-115910"
              [:expansion :contains 0 :code] := #fhir/code "code-163444"
              [:expansion :contains 0 :designation 0 :value] := #fhir/string "designation-011441"))))

      (testing "exclude one code"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-163444"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"}]
                    :exclude
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-163444"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927"
            [:expansion :contains 0 #(contains? % :display)] := false))))

    (testing "with two hierarchical codes"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-163444"}]}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false
          [:expansion :contains 1 :system] := #fhir/uri "system-115910"
          [:expansion :contains 1 :code] := #fhir/code "code-163444"
          [:expansion :contains 1 #(contains? % :display)] := false))

      (testing "include only the parent code"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-163444"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-115927"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927"
            [:expansion :contains 0 #(contains? % :display)] := false)))

      (testing "include only the child code"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-163444"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-163444"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-163444"
            [:expansion :contains 0 #(contains? % :display)] := false))))

    (testing "multiple value-sets with the same url"
      (testing "the value-set with the higher version number is used"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-163444"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-132726"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-132706"
                   :version #fhir/string "1.1.0"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-163444"}]}]}}]
            [:put {:fhir/type :fhir/ValueSet :id "1"
                   :url #fhir/uri "value-set-132706"
                   :version #fhir/string "1.0.0"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-132726"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-132706")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-163444")))

      (testing "the active value-set is used"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-163444"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-132726"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-132706"
                   :status #fhir/code "active"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-163444"}]}]}}]
            [:put {:fhir/type :fhir/ValueSet :id "1"
                   :url #fhir/uri "value-set-132706"
                   :status #fhir/code "draft"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-132726"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-132706")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-163444")))))

  (testing "with two code systems"
    (testing "with one code each"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri "system-180814"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-180828"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}
                   {:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-180814"}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "used-codesystem") 0 :value] := #fhir/uri "system-115910"
          [:expansion (parameter "used-codesystem") 1 :value] := #fhir/uri "system-180814"
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false
          [:expansion :contains 1 :system] := #fhir/uri "system-180814"
          [:expansion :contains 1 :code] := #fhir/code "code-180828"
          [:expansion :contains 1 #(contains? % :display)] := false)))

    (testing "with two codes each"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-163824"}]}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri "system-180814"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-180828"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-163852"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}
                   {:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-180814"}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "used-codesystem") 0 :value] := #fhir/uri "system-115910"
          [:expansion (parameter "used-codesystem") 1 :value] := #fhir/uri "system-180814"
          [:expansion :contains count] := 4
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false
          [:expansion :contains 1 :system] := #fhir/uri "system-115910"
          [:expansion :contains 1 :code] := #fhir/code "code-163824"
          [:expansion :contains 1 #(contains? % :display)] := false
          [:expansion :contains 2 :system] := #fhir/uri "system-180814"
          [:expansion :contains 2 :code] := #fhir/code "code-180828"
          [:expansion :contains 2 #(contains? % :display)] := false
          [:expansion :contains 3 :system] := #fhir/uri "system-180814"
          [:expansion :contains 3 :code] := #fhir/code "code-163852"
          [:expansion :contains 3 #(contains? % :display)] := false))

      (testing "excluding the second code from the first code system"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-163824"}]}]
            [:put {:fhir/type :fhir/CodeSystem :id "1"
                   :url #fhir/uri "system-180814"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-180828"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-163852"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"}
                     {:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-180814"}]
                    :exclude
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-163824"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion (parameter "used-codesystem") 0 :value] := #fhir/uri "system-115910"
            [:expansion (parameter "used-codesystem") 1 :value] := #fhir/uri "system-180814"
            [:expansion :contains count] := 3
            [:expansion :contains 0 :system] := #fhir/uri "system-115910"
            [:expansion :contains 0 :code] := #fhir/code "code-115927"
            [:expansion :contains 0 #(contains? % :display)] := false
            [:expansion :contains 1 :system] := #fhir/uri "system-180814"
            [:expansion :contains 1 :code] := #fhir/code "code-180828"
            [:expansion :contains 1 #(contains? % :display)] := false
            [:expansion :contains 2 :system] := #fhir/uri "system-180814"
            [:expansion :contains 2 :code] := #fhir/code "code-163852"
            [:expansion :contains 2 #(contains? % :display)] := false)))))

  (testing "with two value sets and only one matching the given version"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri "system-135810"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-135827"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-154043"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-154043"
               :version #fhir/string "version-135747"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-135810"}]}}]]]

      (given @(expand-value-set ts
                "url" #fhir/uri "value-set-154043"
                "valueSetVersion" #fhir/string "version-135747")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-135810"
        [:expansion :contains 0 :code] := #fhir/code "code-135827")))

  (testing "with inactive concepts"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-170702"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-170118"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "foo"
                   :value #fhir/boolean false}
                  {:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "inactive"
                   :value #fhir/boolean true}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-164637"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "status"
                   :value #fhir/code "retired"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-163445"
                 :display #fhir/string "display-164521"}]}]]]

      (given @(expand-value-set ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-170702"}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 3
        [:expansion :contains 0 :system] := #fhir/uri "system-170702"
        [:expansion :contains 0 :inactive] := #fhir/boolean true
        [:expansion :contains 0 :code] := #fhir/code "code-170118"
        [:expansion :contains 1 :system] := #fhir/uri "system-170702"
        [:expansion :contains 1 :inactive] := #fhir/boolean true
        [:expansion :contains 1 :code] := #fhir/code "code-164637"
        [:expansion :contains 2 :system] := #fhir/uri "system-170702"
        [:expansion :contains 2 :code] := #fhir/code "code-163445"
        [:expansion :contains 2 :display] := #fhir/string "display-164521")

      (testing "including only active"
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :inactive #fhir/boolean false
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-170702"}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-170702"
          [:expansion :contains 0 :code] := #fhir/code "code-163445"))

      (testing "including all codes"
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-170702"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-170118"}
                       {:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-164637"}
                       {:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "code-163445"
                        :display #fhir/string "display-165751"}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 3
          [:expansion :contains 0 :system] := #fhir/uri "system-170702"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code "code-170118"
          [:expansion :contains 1 :system] := #fhir/uri "system-170702"
          [:expansion :contains 1 :inactive] := #fhir/boolean true
          [:expansion :contains 1 :code] := #fhir/code "code-164637"
          [:expansion :contains 2 :system] := #fhir/uri "system-170702"
          [:expansion :contains 2 :code] := #fhir/code "code-163445"
          [:expansion :contains 2 :display] := #fhir/string "display-165751")

        (testing "including only active"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :inactive #fhir/boolean false
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-170702"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "code-170118"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "code-164637"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "code-163445"
                          :display #fhir/string "display-165751"}]}]}})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-170702"
            [:expansion :contains 0 :code] := #fhir/code "code-163445")))))

  (testing "with not-selectable concepts"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-170702"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-170118"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "foo"
                   :value #fhir/boolean false}
                  {:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "notSelectable"
                   :value #fhir/boolean true}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-164637"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-163445"
                 :display #fhir/string "display-164521"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-145941"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-170702"}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-145941")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 3
        [:expansion :contains 0 :system] := #fhir/uri "system-170702"
        [:expansion :contains 0 :abstract] := #fhir/boolean true
        [:expansion :contains 0 :code] := #fhir/code "code-170118"
        [:expansion :contains 1 :system] := #fhir/uri "system-170702"
        [:expansion :contains 1 :code] := #fhir/code "code-164637"
        [:expansion :contains 2 :system] := #fhir/uri "system-170702"
        [:expansion :contains 2 :code] := #fhir/code "code-163445"
        [:expansion :contains 2 :display] := #fhir/string "display-164521")))

  (testing "with externally supplied value set and code system"
    (with-system [{ts ::ts/local} config]
      (given @(expand-value-set ts
                "url" #fhir/uri "value-set-110445"
                "tx-resource"
                {:fhir/type :fhir/CodeSystem
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}
                "tx-resource"
                {:fhir/type :fhir/ValueSet
                 :url #fhir/uri "value-set-110445"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-115910"
        [:expansion :contains 0 :code] := #fhir/code "code-115927"))

    (testing "with value set version"
      (with-system [{ts ::ts/local} config]
        (given @(expand-value-set ts
                  "url" #fhir/uri "value-set-110445"
                  "valueSetVersion" #fhir/string "version-134920"
                  "tx-resource"
                  {:fhir/type :fhir/CodeSystem
                   :url #fhir/uri "system-115910"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"}]}
                  "tx-resource"
                  {:fhir/type :fhir/ValueSet
                   :url #fhir/uri "value-set-110445"
                   :version #fhir/string "version-134920"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927")))

    (testing "with code system version"
      (with-system [{ts ::ts/local} config]
        (given @(expand-value-set ts
                  "url" #fhir/uri "value-set-110445"
                  "tx-resource"
                  {:fhir/type :fhir/CodeSystem
                   :url #fhir/uri "system-115910"
                   :version #fhir/string "version-135221"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-115927"}]}
                  "tx-resource"
                  {:fhir/type :fhir/ValueSet
                   :url #fhir/uri "value-set-110445"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-115910"
                      :version #fhir/string "version-135221"}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927")))))

(deftest expand-value-set-include-value-set-refs-test
  (testing "one value set ref"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet [#fhir/canonical "value-set-135750"]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-161213")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-180814"
        [:expansion :contains 0 :code] := #fhir/code "code-180828"
        [:expansion :contains 0 #(contains? % :display)] := false))

    (testing "retains already existing expansion"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-180814"}]}
                 :expansion
                 {:fhir/type :fhir.ValueSet/expansion
                  :identifier #fhir/uri "urn:uuid:78653bd7-1b0a-4c9c-afa7-0d5ccf8c6a71"
                  :timestamp #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
                  :contains
                  [{:fhir/type :fhir.ValueSet.expansion/contains
                    :system #fhir/uri "system-180814"
                    :code #fhir/code "code-180828"
                    :display #fhir/string "display-191917"}]}}]
          [:put {:fhir/type :fhir/ValueSet :id "1"
                 :url #fhir/uri "value-set-161213"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :valueSet [#fhir/canonical "value-set-135750"]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-161213")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-180814"
          [:expansion :contains 0 :code] := #fhir/code "code-180828"
          [:expansion :contains 0 :display] := #fhir/string "display-191917"))))

  (testing "two value set refs"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri "system-162531"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-162551"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-162451"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-162531"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "2"
               :url #fhir/uri "value-set-162456"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet
                  [#fhir/canonical "value-set-135750"
                   #fhir/canonical "value-set-162451"]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-162456")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri "system-180814"
        [:expansion :contains 0 :code] := #fhir/code "code-180828"
        [:expansion :contains 0 #(contains? % :display)] := false
        [:expansion :contains 1 :system] := #fhir/uri "system-162531"
        [:expansion :contains 1 :code] := #fhir/code "code-162551"
        [:expansion :contains 1 #(contains? % :display)] := false)))

  (testing "two value set refs including the same code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-162451"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "2"
               :url #fhir/uri "value-set-162456"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet
                  [#fhir/canonical "value-set-135750"
                   #fhir/canonical "value-set-162451"]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-162456")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-180814"
        [:expansion :contains 0 :code] := #fhir/code "code-180828"
        [:expansion :contains 0 #(contains? % :display)] := false)))

  (testing "with externally supplied value set"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet [#fhir/canonical "value-set-135750"]}]}}]]]

      (given @(expand-value-set ts
                "url" #fhir/uri "value-set-161213"
                "tx-resource"
                {:fhir/type :fhir/ValueSet
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-180814"}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-180814"
        [:expansion :contains 0 :code] := #fhir/code "code-180828"
        [:expansion :contains 0 #(contains? % :display)] := false))))

(deftest expand-value-set-include-filter-is-a-test
  (testing "with a single concept"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "is-a"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-182905")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-182822"
        [:expansion :contains 0 :code] := #fhir/code "code-182832"
        [:expansion :contains 0 :display] := #fhir/string "display-182717"))

    (testing "including designations"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-182822"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-182832"
                   :display #fhir/string "display-182717"
                   :designation
                   [{:fhir/type :fhir.CodeSystem.concept/designation
                     :value #fhir/string "designation-011441"}]}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-182905"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-182822"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "concept"
                      :op #fhir/code "is-a"
                      :value #fhir/string "code-182832"}]}]}}]]]

        (given @(expand-value-set ts
                  "url" #fhir/uri "value-set-182905"
                  "includeDesignations" #fhir/boolean true)
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "includeDesignations") 0 :value] := #fhir/boolean true
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-182822"
          [:expansion :contains 0 :code] := #fhir/code "code-182832"
          [:expansion :contains 0 :display] := #fhir/string "display-182717"
          [:expansion :contains 0 :designation 0 :value] := #fhir/string "designation-011441"))))

  (testing "with two concepts, a parent and a child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :display #fhir/string "display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-182832"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "is-a"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given (sort-expansion @(expand-value-set ts "url" #fhir/uri "value-set-182905"))
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri "system-182822"
        [:expansion :contains 0 :code] := #fhir/code "code-182832"
        [:expansion :contains 0 :display] := #fhir/string "display-182717"
        [:expansion :contains 1 :system] := #fhir/uri "system-182822"
        [:expansion :contains 1 :code] := #fhir/code "code-191445"
        [:expansion :contains 1 :display] := #fhir/string "display-191448"))

    (testing "with inactive child"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-182822"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-182832"
                   :display #fhir/string "display-182717"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-191445"
                   :display #fhir/string "display-191448"
                   :property
                   [{:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code "parent"
                     :value #fhir/code "code-182832"}
                    {:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code "inactive"
                     :value #fhir/boolean true}]}]}]]]

        (given (sort-expansion
                @(expand-value-set ts
                   "valueSet"
                   {:fhir/type :fhir/ValueSet
                    :compose
                    {:fhir/type :fhir.ValueSet/compose
                     :include
                     [{:fhir/type :fhir.ValueSet.compose/include
                       :system #fhir/uri "system-182822"
                       :filter
                       [{:fhir/type :fhir.ValueSet.compose.include/filter
                         :property #fhir/code "concept"
                         :op #fhir/code "is-a"
                         :value #fhir/string "code-182832"}]}]}}))
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri "system-182822"
          [:expansion :contains 0 :code] := #fhir/code "code-182832"
          [:expansion :contains 0 :display] := #fhir/string "display-182717"
          [:expansion :contains 1 :system] := #fhir/uri "system-182822"
          [:expansion :contains 1 :inactive] := #fhir/boolean true
          [:expansion :contains 1 :code] := #fhir/code "code-191445"
          [:expansion :contains 1 :display] := #fhir/string "display-191448")

        (testing "including only active"
          (given (sort-expansion
                  @(expand-value-set ts
                     "valueSet"
                     {:fhir/type :fhir/ValueSet
                      :compose
                      {:fhir/type :fhir.ValueSet/compose
                       :inactive #fhir/boolean false
                       :include
                       [{:fhir/type :fhir.ValueSet.compose/include
                         :system #fhir/uri "system-182822"
                         :filter
                         [{:fhir/type :fhir.ValueSet.compose.include/filter
                           :property #fhir/code "concept"
                           :op #fhir/code "is-a"
                           :value #fhir/string "code-182832"}]}]}}))
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-182822"
            [:expansion :contains 0 :code] := #fhir/code "code-182832"
            [:expansion :contains 0 :display] := #fhir/string "display-182717")))))

  (testing "with three concepts, a parent, a child and a child of the child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :display #fhir/string "display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-182832"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-192308"
                 :display #fhir/string "display-192313"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-191445"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "is-a"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given (sort-expansion @(expand-value-set ts "url" #fhir/uri "value-set-182905"))
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 3
        [:expansion :contains 0 :system] := #fhir/uri "system-182822"
        [:expansion :contains 0 :code] := #fhir/code "code-182832"
        [:expansion :contains 0 :display] := #fhir/string "display-182717"
        [:expansion :contains 1 :system] := #fhir/uri "system-182822"
        [:expansion :contains 1 :code] := #fhir/code "code-191445"
        [:expansion :contains 1 :display] := #fhir/string "display-191448"
        [:expansion :contains 2 :system] := #fhir/uri "system-182822"
        [:expansion :contains 2 :code] := #fhir/code "code-192308"
        [:expansion :contains 2 :display] := #fhir/string "display-192313"))

    (testing "works if child of child comes before child"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-182822"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-182832"
                   :display #fhir/string "display-182717"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-192308"
                   :display #fhir/string "display-192313"
                   :property
                   [{:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code "parent"
                     :value #fhir/code "code-191445"}]}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-191445"
                   :display #fhir/string "display-191448"
                   :property
                   [{:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code "parent"
                     :value #fhir/code "code-182832"}]}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-182905"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-182822"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "concept"
                      :op #fhir/code "is-a"
                      :value #fhir/string "code-182832"}]}]}}]]]

        (given (sort-expansion @(expand-value-set ts "url" #fhir/uri "value-set-182905"))
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 3
          [:expansion :contains 0 :system] := #fhir/uri "system-182822"
          [:expansion :contains 0 :code] := #fhir/code "code-182832"
          [:expansion :contains 0 :display] := #fhir/string "display-182717"
          [:expansion :contains 1 :system] := #fhir/uri "system-182822"
          [:expansion :contains 1 :code] := #fhir/code "code-191445"
          [:expansion :contains 1 :display] := #fhir/string "display-191448"
          [:expansion :contains 2 :system] := #fhir/uri "system-182822"
          [:expansion :contains 2 :code] := #fhir/code "code-192308"
          [:expansion :contains 2 :display] := #fhir/string "display-192313")))))

(deftest expand-value-set-include-filter-descendent-of-test
  (testing "with a single concept"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "descendent-of"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-182905")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 0)))

  (testing "with two concepts, a parent and a child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :display #fhir/string "display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-182832"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "descendent-of"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given (sort-expansion @(expand-value-set ts "url" #fhir/uri "value-set-182905"))
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-182822"
        [:expansion :contains 0 :code] := #fhir/code "code-191445"
        [:expansion :contains 0 :display] := #fhir/string "display-191448")))

  (testing "with three concepts, a parent, a child and a child of the child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :display #fhir/string "display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-182832"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-192308"
                 :display #fhir/string "display-192313"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-191445"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "descendent-of"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given (sort-expansion @(expand-value-set ts "url" #fhir/uri "value-set-182905"))
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri "system-182822"
        [:expansion :contains 0 :code] := #fhir/code "code-191445"
        [:expansion :contains 0 :display] := #fhir/string "display-191448"
        [:expansion :contains 1 :system] := #fhir/uri "system-182822"
        [:expansion :contains 1 :code] := #fhir/code "code-192308"
        [:expansion :contains 1 :display] := #fhir/string "display-192313"))))

(deftest expand-value-set-include-filter-exists-test
  (testing "with a single concept"
    (testing "without a property"
      (testing "that shouldn't exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-160622"
                        :op #fhir/code "exists"
                        :value #fhir/string "false"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-182822"
            [:expansion :contains 0 :code] := #fhir/code "code-182832"
            [:expansion :contains 0 :display] := #fhir/string "display-182717")))

      (testing "that should exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-160622"
                        :op #fhir/code "exists"
                        :value #fhir/string "true"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))

    (testing "with existing property"
      (testing "that shouldn't exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code "property-160631"
                       :value #fhir/string "value-161324"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-160631"
                        :op #fhir/code "exists"
                        :value #fhir/string "false"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0)))

      (testing "that should exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code "property-160631"
                       :value #fhir/string "value-161324"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-160631"
                        :op #fhir/code "exists"
                        :value #fhir/string "true"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-182822"
            [:expansion :contains 0 :code] := #fhir/code "code-182832"
            [:expansion :contains 0 :display] := #fhir/string "display-182717"))))))

(deftest expand-value-set-include-filter-equals-test
  (with-system-data [{ts ::ts/local} config]
    [[[:put {:fhir/type :fhir/CodeSystem :id "0"
             :url #fhir/uri "system-182822"
             :content #fhir/code "complete"
             :concept
             [{:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-175652"
               :display #fhir/string "display-175659"
               :property
               [{:fhir/type :fhir.CodeSystem.concept/property
                 :code #fhir/code "property-175506"
                 :value #fhir/string "value-161324"}]}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-175607"
               :display #fhir/string "display-175610"
               :property
               [{:fhir/type :fhir.CodeSystem.concept/property
                 :code #fhir/code "property-175506"
                 :value #fhir/string "value-175614"}]}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-172215"
               :display #fhir/string "display-172220"
               :property
               [{:fhir/type :fhir.CodeSystem.concept/property
                 :code #fhir/code "property-172030"
                 :value #fhir/string "value-161324"}]}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-175607"
               :display #fhir/string "display-175610"}]}]
      [:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-175628"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "system-182822"
                :filter
                [{:fhir/type :fhir.ValueSet.compose.include/filter
                  :property #fhir/code "property-175506"
                  :op #fhir/code "="
                  :value #fhir/string "value-161324"}]}]}}]]]

    (given @(expand-value-set ts "url" #fhir/uri "value-set-175628")
      :fhir/type := :fhir/ValueSet
      [:expansion :contains count] := 1
      [:expansion :contains 0 :system] := #fhir/uri "system-182822"
      [:expansion :contains 0 :code] := #fhir/code "code-175652"
      [:expansion :contains 0 :display] := #fhir/string "display-175659")))

(deftest expand-value-set-include-filter-regex-test
  (testing "code"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "a"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "aa"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "ab"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-175628"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "code"
                    :op #fhir/code "regex"
                    :value #fhir/string "a+"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-175628")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri "system-182822"
        [:expansion :contains 0 :code] := #fhir/code "a"
        [:expansion :contains 1 :system] := #fhir/uri "system-182822"
        [:expansion :contains 1 :code] := #fhir/code "aa")))

  (testing "other property"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-145708"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "property-175506"
                   :value #fhir/string "a"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-145731"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "property-175506"
                   :value #fhir/string "aa"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-145738"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "property-150054"
                   :value #fhir/string "aa"}
                  {:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "property-175506"
                   :value #fhir/string "ab"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-175628"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "property-175506"
                    :op #fhir/code "regex"
                    :value #fhir/string "a+"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-175628")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri "system-182822"
        [:expansion :contains 0 :code] := #fhir/code "code-145731"
        [:expansion :contains 1 :system] := #fhir/uri "system-182822"
        [:expansion :contains 1 :code] := #fhir/code "code-145708"))))

(deftest expand-value-set-include-filter-multiple-test
  (testing "is-a and exists (and the other way around)"
    (let [is-a-filter {:fhir/type :fhir.ValueSet.compose.include/filter
                       :property #fhir/code "concept"
                       :op #fhir/code "is-a"
                       :value #fhir/string "code-182832"}
          leaf-filter {:fhir/type :fhir.ValueSet.compose.include/filter
                       :property #fhir/code "child"
                       :op #fhir/code "exists"
                       :value #fhir/string "false"}]
      (doseq [filters (tu/permutations [is-a-filter leaf-filter])]
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-191445"
                     :display #fhir/string "display-191448"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code "parent"
                       :value #fhir/code "code-182832"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter filters}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "system-182822"
            [:expansion :contains 0 :code] := #fhir/code "code-191445"
            [:expansion :contains 0 :display] := #fhir/string "display-191448"))))))

(deftest expand-value-set-provided-value-set-test
  (testing "fails on non-complete code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "not-present"}]]]

      (given-failed-future
       (expand-value-set ts
         "valueSet"
         {:fhir/type :fhir/ValueSet
          :compose
          {:fhir/type :fhir.ValueSet/compose
           :include
           [{:fhir/type :fhir.ValueSet.compose/include
             :system #fhir/uri "system-115910"}]}})
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the provided value set. Can't use the code system `system-115910` because it's content is not one of complete, fragment. It's content is `not-present`."
        :t := 1)))

  (with-system-data [{ts ::ts/local} config]
    [[[:put {:fhir/type :fhir/CodeSystem :id "0"
             :url #fhir/uri "system-115910"
             :content #fhir/code "complete"
             :concept
             [{:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-115927"}]}]]]

    (given @(expand-value-set ts
              "valueSet"
              {:fhir/type :fhir/ValueSet
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}})
      :fhir/type := :fhir/ValueSet
      [:expansion :contains count] := 1
      [:expansion :contains 0 :system] := #fhir/uri "system-115910"
      [:expansion :contains 0 :code] := #fhir/code "code-115927"
      [:expansion :contains 0 #(contains? % :display)] := false)))

(deftest expand-value-set-bcp-13-include-all-test
  (testing "including all of BCP-13 is not possible"
    (with-system-data [{ts ::ts/local} bcp-13-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-150638"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "urn:ietf:bcp:13"}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri "value-set-150638")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-150638`. Expanding all BCP-13 concepts is not possible."))))

(deftest expand-value-set-bcp-47-include-all-test
  (testing "including all of BCP-47 is not possible"
    (with-system-data [{ts ::ts/local} bcp-47-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-150638"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "urn:ietf:bcp:47"}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri "value-set-150638")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-150638`. Expanding all BCP-47 concepts is not possible."))))

(deftest expand-value-set-bcp-13-concept-test
  (with-system-data [{ts ::ts/local} bcp-13-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-152706"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "urn:ietf:bcp:13"
                :concept
                [{:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code "text/plain"}
                 {:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code "application/fhir+json"}]}]}}]]]

    (given @(expand-value-set ts "url" #fhir/uri "value-set-152706")
      :fhir/type := :fhir/ValueSet
      [:expansion :contains count] := 2
      [:expansion :contains 0 :system] := #fhir/uri "urn:ietf:bcp:13"
      [:expansion :contains 0 :code] := #fhir/code "text/plain"
      [:expansion :contains 1 :system] := #fhir/uri "urn:ietf:bcp:13"
      [:expansion :contains 1 :code] := #fhir/code "application/fhir+json")))

(deftest expand-value-set-bcp-47-concept-test
  (with-system-data [{ts ::ts/local} bcp-47-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-152706"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "urn:ietf:bcp:47"
                :concept
                [{:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code "ar"}
                 {:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code "bn"}
                 {:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code "ar_XXX"}]}]}}]]]

    (given @(expand-value-set ts "url" #fhir/uri "value-set-152706")
      :fhir/type := :fhir/ValueSet
      [:expansion :contains count] := 2
      [:expansion :contains 0 :system] := #fhir/uri "urn:ietf:bcp:47"
      [:expansion :contains 0 :code] := #fhir/code "ar"
      [:expansion :contains 1 :system] := #fhir/uri "urn:ietf:bcp:47"
      [:expansion :contains 1 :code] := #fhir/code "bn")))

(deftest expand-value-set-loinc-include-all-test
  (testing "including all of LOINC is too costly"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-152015"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://loinc.org"}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri "value-set-152015")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-152015`. Expanding all LOINC concepts is too costly."
        :fhir/issue "too-costly"))))

(deftest expand-value-set-loinc-include-concept-test
  (testing "include one concept"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "system-152546"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://loinc.org"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "26465-5"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "system-152546")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "http://loinc.org"
        [:expansion :contains 0 #(contains? % :inactive)] := false
        [:expansion :contains 0 :code] := #fhir/code "26465-5"
        [:expansion :contains 0 :display] := #fhir/string "Leukocytes [#/volume] in Cerebral spinal fluid"
        [:expansion :contains 0 #(contains? % :designation)] := false))

    (testing "with inactive concepts"
      (with-system [{ts ::ts/local} loinc-config]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "1009-0"}
                       {:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "26465-5"}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code "1009-0"
          [:expansion :contains 0 :display] := #fhir/string "Deprecated Direct antiglobulin test.poly specific reagent [Presence] on Red Blood Cells"
          [:expansion :contains 1 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains 1 #(contains? % :inactive)] := false
          [:expansion :contains 1 :code] := #fhir/code "26465-5"
          [:expansion :contains 1 :display] := #fhir/string "Leukocytes [#/volume] in Cerebral spinal fluid")

        (testing "value set including only active"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :inactive #fhir/boolean false
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "http://loinc.org"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "1009-0"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "26465-5"}]}]}})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "http://loinc.org"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code "26465-5"
            [:expansion :contains 0 :display] := #fhir/string "Leukocytes [#/volume] in Cerebral spinal fluid"))

        (testing "activeOnly param"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "http://loinc.org"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "1009-0"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "26465-5"}]}]}}
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "http://loinc.org"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code "26465-5"
            [:expansion :contains 0 :display] := #fhir/string "Leukocytes [#/volume] in Cerebral spinal fluid"))))))

(deftest expand-value-set-loinc-include-filter-equals-test
  (testing "COMPONENT = LP14449-0/Hemoglobin"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP14449-0" "lp14449-0" "Hemoglobin" "hemoglobin" "HEMOGLOBIN"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "COMPONENT"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10 % 100)
          [:expansion :contains (concept "718-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "718-7") 0 :display] := #fhir/string "Hemoglobin [Mass/volume] in Blood"))))

  (testing "PROPERTY = LP6870-2/Susc"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP6870-2" "lp6870-2" "Susc" "susc" "SUSC"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "PROPERTY"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 1000 % 10000)
          [:expansion :contains (concept "18868-0") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "18868-0") 0 :display] := #fhir/string "Aztreonam [Susceptibility]"))))

  (testing "TIME_ASPCT = LP6960-1/Pt"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP6960-1" "lp6960-1" "Pt" "pt" "PT"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "TIME_ASPCT"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10000 % 100000)
          [:expansion :contains (concept "718-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "718-7") 0 :display] := #fhir/string "Hemoglobin [Mass/volume] in Blood"))))

  (testing "SYSTEM = LP7057-5/Bld"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP7057-5" "lp7057-5" "Bld" "bld" "BLD"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "SYSTEM"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 1000 % 10000)
          [:expansion :contains (concept "718-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "718-7") 0 :display] := #fhir/string "Hemoglobin [Mass/volume] in Blood"))))

  (testing "SCALE_TYP = LP7753-9/Qn"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP7753-9" "lp7753-9" "Qn" "qn" "QN"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "SCALE_TYP"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10000 % 100000)
          [:expansion :contains (concept "718-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "718-7") 0 :display] := #fhir/string "Hemoglobin [Mass/volume] in Blood"))))

  (testing "METHOD_TYP = LP28723-2/Genotyping"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP28723-2" "lp28723-2" "Genotyping" "genotyping" "GENOTYPING"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "METHOD_TYP"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 100 % 1000)
          [:expansion :contains (concept "100983-6") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "100983-6") 0 :display] := #fhir/string "HIV reverse transcriptase failed codons [Identifier] by Genotype method"))))

  (testing "CLASS = LP7789-3/Cyto"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP7789-3" "lp7789-3" "Cyto" "cyto" "CYTO"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "CLASS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10 % 100)
          [:expansion :contains (concept "50971-1") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "50971-1") 0 :display] := #fhir/string "Cytology report of Bronchial brush Cyto stain"))))

  (testing "CLASS = LP94892-4/Laborders"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP94892-4" "lp94892-4" "Laborders" "laborders" "LABORDERS"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "CLASS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10 % 100)
          [:expansion :contains (concept "82773-3") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "82773-3") 0 :display] := #fhir/string "Lab result time reported"))))

  (testing "STATUS = ACTIVE"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["ACTIVE" "active"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "STATUS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? (partial < 100)
          [:expansion :contains (concept "82773-3") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "82773-3") 0 :display] := #fhir/string "Lab result time reported"))))

  (testing "STATUS = TRIAL"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["TRIAL" "trial"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "STATUS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? (partial < 100)))))

  (testing "STATUS = DISCOURAGED"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["DISCOURAGED" "discouraged"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "STATUS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? (partial < 100)
          [:expansion :contains (concept "69349-9") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "69349-9") 0 :display] := #fhir/string "Presence of pressure ulcers - acute [CARE]"))))

  (testing "STATUS = DEPRECATED"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["DEPRECATED" "deprecated"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "STATUS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? (partial < 100)
          [:expansion :contains (concept "29491-8") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "29491-8") 0 :display] := #fhir/string "Deprecated Special attachment request modifier codes"))))

  (testing "CLASSTYPE = 1 (Laboratory class)"
    (with-system [{ts ::ts/local} loinc-config]
      (given @(expand-value-set ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "CLASSTYPE"
                      :op #fhir/code "="
                      :value #fhir/string "1"}]}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? (partial < 100)
        [:expansion :contains (concept "3694-7") 0 :system] := #fhir/uri "http://loinc.org"
        [:expansion :contains (concept "3694-7") 0 :display] := #fhir/string "Indomethacin [Mass/volume] in Serum or Plasma")))

  (testing "CLASSTYPE = 2 (Clinical class)"
    (with-system [{ts ::ts/local} loinc-config]
      (given @(expand-value-set ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "CLASSTYPE"
                      :op #fhir/code "="
                      :value #fhir/string "2"}]}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? (partial < 100)
        [:expansion :contains (concept "71735-5") 0 :system] := #fhir/uri "http://loinc.org"
        [:expansion :contains (concept "71735-5") 0 :display] := #fhir/string "Personnel Credentials")))

  (testing "CLASSTYPE = 3 (Claims attachments)"
    (with-system [{ts ::ts/local} loinc-config]
      (given @(expand-value-set ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "CLASSTYPE"
                      :op #fhir/code "="
                      :value #fhir/string "3"}]}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? (partial < 100)
        [:expansion :contains (concept "39215-9") 0 :system] := #fhir/uri "http://loinc.org"
        [:expansion :contains (concept "39215-9") 0 :display] := #fhir/string "Vision screen finding recency CPHS")))

  (testing "CLASSTYPE = 4 (Surveys)"
    (with-system [{ts ::ts/local} loinc-config]
      (given @(expand-value-set ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "CLASSTYPE"
                      :op #fhir/code "="
                      :value #fhir/string "4"}]}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? (partial < 100)
        [:expansion :contains (concept "28234-3") 0 :system] := #fhir/uri "http://loinc.org"
        [:expansion :contains (concept "28234-3") 0 :display] := #fhir/string "Unilateral neglect [CCC]")))

  (testing "ORDER_OBS = Observation"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Observation" "observation" "OBSERVATION"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "ORDER_OBS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? (partial < 100)
          [:expansion :contains (concept "18868-0") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "18868-0") 0 :display] := #fhir/string "Aztreonam [Susceptibility]"))))

  (testing "ORDER_OBS = Order"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Order" "order" "ORDER"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "ORDER_OBS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? (partial < 100)
          [:expansion :contains (concept "98207-4") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "98207-4") 0 :display] := #fhir/string "Borrelia burgdorferi IgG and IgM panel - Cerebral spinal fluid by Immunoassay"))))

  (testing "ORDER_OBS = Both"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Both" "both" "BOTH"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "ORDER_OBS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? (partial < 100)
          [:expansion :contains (concept "13356-1") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "13356-1") 0 :display] := #fhir/string "Fat [Presence] in Body fluid"))))

  (testing "ORDER_OBS = Subset"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Subset" "subset" "SUBSET"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "ORDER_OBS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? (partial < 100)
          [:expansion :contains (concept "100197-3") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "100197-3") 0 :display] := #fhir/string "Exercise activity and pain severity panel")))))

(deftest expand-value-set-loinc-include-filter-regex-test
  (testing "COMPONENT =~ Hemoglobin|Amprenavir"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Hemoglobin|Amprenavir" "hemoglobin|amprenavir" "HEMOGLOBIN|AMPRENAVIR"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "COMPONENT"
                        :op #fhir/code "regex"
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10 % 100)
          [:expansion :contains (concept "718-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "718-7") 0 :display] := #fhir/string "Hemoglobin [Mass/volume] in Blood"
          [:expansion :contains (concept "30299-2") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "30299-2") 0 :display] := #fhir/string "Amprenavir [Susceptibility]"))))

  (testing "PROPERTY =~ Susc|CCnc"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Susc|CCnc" "susc|ccnc" "SUSC|CCNC"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "PROPERTY"
                        :op #fhir/code "regex"
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 1000 % 10000)
          [:expansion :contains (concept "3036-1") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "3036-1") 0 :display] := #fhir/string "Transketolase [Enzymatic activity/volume] in Serum"
          [:expansion :contains (concept "30299-2") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "30299-2") 0 :display] := #fhir/string "Amprenavir [Susceptibility]"))))

  (testing "TIME_ASPCT =~ 10H|18H"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["10H|18H" "10h|18h"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "TIME_ASPCT"
                        :op #fhir/code "regex"
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10 % 100)
          [:expansion :contains (concept "63474-1") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "63474-1") 0 :display] := #fhir/string "Microalbumin [Mass/time] in 18 hour Urine"
          [:expansion :contains (concept "8323-8") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "8323-8") 0 :display] := #fhir/string "Body temperature 10 hour"))))

  (testing "SYSTEM =~ Bld|Ser/Plas"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Bld|Ser/Plas" "bld|ser/plas" "BLD|SER/PLAS"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "SYSTEM"
                        :op #fhir/code "regex"
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10000 % 100000)
          [:expansion :contains (concept "718-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "718-7") 0 :display] := #fhir/string "Hemoglobin [Mass/volume] in Blood"
          [:expansion :contains (concept "47595-4") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "47595-4") 0 :display] := #fhir/string "C peptide [Moles/volume] in Serum or Plasma --pre dose glucose"))))

  (testing "SCALE_TYP =~ Qn|Ord"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Qn|Ord" "qn|ord" "QN|ORD"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "SCALE_TYP"
                        :op #fhir/code "regex"
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10000 % 100000)
          [:expansion :contains (concept "718-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "718-7") 0 :display] := #fhir/string "Hemoglobin [Mass/volume] in Blood"
          [:expansion :contains (concept "4764-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "4764-7") 0 :display] := #fhir/string "HLA-B55(22) [Presence] in Donor"))))

  (testing "METHOD_TYP =~ Genotyping|Molgen"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Genotyping|Molgen" "genotyping|molgen" "GENOTYPING|MOLGEN"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "METHOD_TYP"
                        :op #fhir/code "regex"
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 1000 % 10000)
          [:expansion :contains (concept "100983-6") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "100983-6") 0 :display] := #fhir/string "HIV reverse transcriptase failed codons [Identifier] by Genotype method"
          [:expansion :contains (concept "48577-1") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "48577-1") 0 :display] := #fhir/string "Deprecated HFE gene c.845G>A [Presence] in Blood or Tissue by Molecular genetics method"))))

  (testing "STATUS =~ ACTIVE|DISCOURAGED"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["ACTIVE|DISCOURAGED" "active|discouraged"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "STATUS"
                        :op #fhir/code "regex"
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 100 % 100000)
          [:expansion :contains (concept "82773-3") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "82773-3") 0 :display] := #fhir/string "Lab result time reported"
          [:expansion :contains (concept "69349-9") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "69349-9") 0 :display] := #fhir/string "Presence of pressure ulcers - acute [CARE]"))))

  (testing "CLASS =~ Cyto|Laborders"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Cyto|Laborders" "cyto|laborders" "CYTO|LABORDERS"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "CLASS"
                        :op #fhir/code "regex"
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10 % 1000)
          [:expansion :contains (concept "50971-1") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "50971-1") 0 :display] := #fhir/string "Cytology report of Bronchial brush Cyto stain"
          [:expansion :contains (concept "82773-3") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "82773-3") 0 :display] := #fhir/string "Lab result time reported"))))

  (testing "ORDER_OBS =~ Order|Both"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Order|Both" "order|both" "ORDER|BOTH"]]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "ORDER_OBS"
                        :op #fhir/code "regex"
                        :value (type/string value)}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] :? #(< 10000 % 100000)
          [:expansion :contains (concept "718-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "718-7") 0 :display] := #fhir/string "Hemoglobin [Mass/volume] in Blood"
          [:expansion :contains (concept "100987-7") 0 :system] := #fhir/uri "http://loinc.org"
          [:expansion :contains (concept "100987-7") 0 :display] := #fhir/string "Lymphocyte T-cell and B-cell and Natural killer subsets panel - Cerebral spinal fluid")))))

(deftest expand-value-set-loinc-include-filter-multiple-test
  (testing "http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-190529"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "STATUS"
                    :op #fhir/code "="
                    :value #fhir/string "ACTIVE"}
                   {:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "CLASSTYPE"
                    :op #fhir/code "="
                    :value #fhir/string "1"}]}]
                :exclude
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "CLASS"
                    :op #fhir/code "regex"
                    :value #fhir/string "CYTO|HL7\\.CYTOGEN|HL7\\.GENETICS|^PATH(\\..*)?|^MOLPATH(\\..*)?|NR STATS|H&P\\.HX\\.LAB|CHALSKIN|LABORDERS"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-190529")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? #(< 50000 % 60000)
        [:expansion :contains (concept "100046-2") 0 :display] := #fhir/string "Cefquinome [Susceptibility]"
        [:expansion :contains (concept "50971-1") count] := 0
        [:expansion :contains (concept "82773-3") count] := 0))))

(deftest expand-value-set-loinc-implicit-test
  (testing "answer list LL4049-4"
    (with-system [{ts ::ts/local} loinc-config]
      (given @(expand-value-set ts
                "url" #fhir/uri "http://loinc.org/vs/LL4049-4")
        :fhir/type := :fhir/ValueSet
        :title := #fhir/string "LOINC AnswerList LL4049-4 (Medication usage suggestion)"
        [:expansion :contains count] := 5
        [:expansion :contains (concept "LA26421-0") 0 :display] := #fhir/string "Consider alternative medication"
        [:expansion :contains (concept "LA26422-8") 0 :display] := #fhir/string "Decrease dose"
        [:expansion :contains (concept "LA26423-6") 0 :display] := #fhir/string "Increase dose"
        [:expansion :contains (concept "LA26424-4") 0 :display] := #fhir/string "Use caution"
        [:expansion :contains (concept "LA26425-1") 0 :display] := #fhir/string "Normal response expected")))

  (testing "unknown"
    (with-system [{ts ::ts/local} loinc-config]
      (given-failed-future
       (expand-value-set ts
         "url" #fhir/uri "http://loinc.org/vs/unknown-210800")
        ::anom/category := ::anom/not-found
        ::anom/message := "The value set `http://loinc.org/vs/unknown-210800` was not found."))))

(deftest expand-value-set-sct-include-all-test
  (testing "including all of SNOMED CT is too costly"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "system-182137"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri "system-182137")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `system-182137`. Expanding all SNOMED CT concepts is too costly."
        :fhir/issue "too-costly"))))

(deftest expand-value-set-sct-include-concept-test
  (testing "include one concept"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "system-151922"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "441510007"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "system-151922")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
        [:expansion :contains 0 #(contains? % :inactive)] := false
        [:expansion :contains 0 :code] := #fhir/code "441510007"
        [:expansion :contains 0 :display] := #fhir/string "Blood specimen with anticoagulant"
        [:expansion :contains 0 #(contains? % :designation)] := false))

    (testing "with inactive concepts"
      (with-system [{ts ::ts/local} sct-config]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://snomed.info/sct"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "860958002"}
                       {:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "441510007"}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code "860958002"
          [:expansion :contains 0 :display] := #fhir/string "Temperature of blood"
          [:expansion :contains 1 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains 1 #(contains? % :inactive)] := false
          [:expansion :contains 1 :code] := #fhir/code "441510007"
          [:expansion :contains 1 :display] := #fhir/string "Blood specimen with anticoagulant")

        (testing "value set including only active"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :inactive #fhir/boolean false
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "http://snomed.info/sct"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "860958002"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "441510007"}]}]}})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code "441510007"
            [:expansion :contains 0 :display] := #fhir/string "Blood specimen with anticoagulant"))

        (testing "activeOnly param"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "http://snomed.info/sct"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "860958002"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "441510007"}]}]}}
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code "441510007"
            [:expansion :contains 0 :display] := #fhir/string "Blood specimen with anticoagulant"))))

    (testing "with version (module)"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "system-152048"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://snomed.info/sct"
                    :version #fhir/string "http://snomed.info/sct/900000000000207008"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code "441510007"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "system-152048")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code "441510007"
          [:expansion :contains 0 :display] := #fhir/string "Blood specimen with anticoagulant"))

      (testing "Germany module"
        (with-system-data [{ts ::ts/local} sct-config]
          [[[:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "system-152116"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://snomed.info/sct"
                      :version #fhir/string "http://snomed.info/sct/11000274103"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "441510007"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri "system-152116")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code "441510007"
            [:expansion :contains 0 :display] := #fhir/string "Blood specimen with anticoagulant"))

        (testing "with displayLanguage = de"
          (with-system-data [{ts ::ts/local} sct-config]
            [[[:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri "system-152116"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "http://snomed.info/sct"
                        :version #fhir/string "http://snomed.info/sct/11000274103"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "441510007"}]}]}}]]]

            (given @(expand-value-set ts
                      "url" #fhir/uri "system-152116"
                      "displayLanguage" #fhir/code "de")
              :fhir/type := :fhir/ValueSet
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
              [:expansion :contains 0 #(contains? % :inactive)] := false
              [:expansion :contains 0 :code] := #fhir/code "441510007"
              [:expansion :contains 0 :display] := #fhir/string "Blood specimen with anticoagulant")))))

    (testing "with version"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "system-152139"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://snomed.info/sct"
                    :version #fhir/string "http://snomed.info/sct/900000000000207008/version/20231201"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code "441510007"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "system-152139")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code "441510007"
          [:expansion :contains 0 :display] := #fhir/string "Blood specimen with anticoagulant"))

      (testing "non-existing version"
        (with-system [{ts ::ts/local} sct-config]
          (given-failed-future (expand-value-set ts
                                 "valueSet"
                                 {:fhir/type :fhir/ValueSet
                                  :compose
                                  {:fhir/type :fhir.ValueSet/compose
                                   :include
                                   [{:fhir/type :fhir.ValueSet.compose/include
                                     :system #fhir/uri "http://snomed.info/sct"
                                     :version #fhir/string "http://snomed.info/sct/900000000000207008/version/none-existing"
                                     :concept
                                     [{:fhir/type :fhir.ValueSet.compose.include/concept
                                       :code #fhir/code "441510007"}]}]}})
            ::anom/category := ::anom/not-found
            ::anom/message := "Error while expanding the provided value set. The code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/none-existing` was not found."))))

    (testing "with designations"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "system-174336"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://snomed.info/sct"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code "441510007"}]}]}}]]]

        (given @(expand-value-set ts
                  "url" #fhir/uri "system-174336"
                  "includeDesignations" #fhir/boolean true)
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code "441510007"
          [:expansion :contains 0 :display] := #fhir/string "Blood specimen with anticoagulant"
          [:expansion :contains 0 :designation count] := 2
          [:expansion :contains 0 :designation 0 :language] := #fhir/code "en"
          [:expansion :contains 0 :designation 0 :use :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains 0 :designation 0 :use :code] := #fhir/code "900000000000003001"
          [:expansion :contains 0 :designation 0 :use :display] := #fhir/string "Fully specified name"
          [:expansion :contains 0 :designation 0 :value] := #fhir/string "Blood specimen with anticoagulant (specimen)"
          [:expansion :contains 0 :designation 1 :language] := #fhir/code "en"
          [:expansion :contains 0 :designation 1 :use :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains 0 :designation 1 :use :code] := #fhir/code "900000000000013009"
          [:expansion :contains 0 :designation 1 :use :display] := #fhir/string "Synonym"
          [:expansion :contains 0 :designation 1 :value] := #fhir/string "Blood specimen with anticoagulant"))

      (testing "Germany module"
        (with-system-data [{ts ::ts/local} sct-config]
          [[[:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "system-152116"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://snomed.info/sct"
                      :version #fhir/string "http://snomed.info/sct/11000274103"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "440500007"}]}]}}]]]

          (given @(expand-value-set ts
                    "url" #fhir/uri "system-152116"
                    "includeDesignations" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code "440500007"
            [:expansion :contains 0 :display] := #fhir/string "Dried blood spot specimen"
            [:expansion :contains 0 :designation count] := 5
            [:expansion :contains 0 :designation 0 :language] := #fhir/code "en"
            [:expansion :contains 0 :designation 0 :use :system] := #fhir/uri "http://snomed.info/sct"
            [:expansion :contains 0 :designation 0 :use :code] := #fhir/code "900000000000003001"
            [:expansion :contains 0 :designation 0 :use :display] := #fhir/string "Fully specified name"
            [:expansion :contains 0 :designation 0 :value] := #fhir/string "Dried blood spot specimen (specimen)"
            [:expansion :contains 0 :designation 1 :language] := #fhir/code "de"
            [:expansion :contains 0 :designation 1 :use :system] := #fhir/uri "http://snomed.info/sct"
            [:expansion :contains 0 :designation 1 :use :code] := #fhir/code "900000000000013009"
            [:expansion :contains 0 :designation 1 :use :display] := #fhir/string "Synonym"
            [:expansion :contains 0 :designation 1 :value] := #fhir/string "Trockenblutkarte"
            [:expansion :contains 0 :designation 2 :language] := #fhir/code "de"
            [:expansion :contains 0 :designation 2 :use :system] := #fhir/uri "http://snomed.info/sct"
            [:expansion :contains 0 :designation 2 :use :code] := #fhir/code "900000000000013009"
            [:expansion :contains 0 :designation 2 :use :display] := #fhir/string "Synonym"
            [:expansion :contains 0 :designation 2 :value] := #fhir/string "Dried Blood Spot Sample"
            [:expansion :contains 0 :designation 3 :language] := #fhir/code "en"
            [:expansion :contains 0 :designation 3 :use :system] := #fhir/uri "http://snomed.info/sct"
            [:expansion :contains 0 :designation 3 :use :code] := #fhir/code "900000000000013009"
            [:expansion :contains 0 :designation 3 :use :display] := #fhir/string "Synonym"
            [:expansion :contains 0 :designation 3 :value] := #fhir/string "Blood spot specimen"
            [:expansion :contains 0 :designation 4 :language] := #fhir/code "en"
            [:expansion :contains 0 :designation 4 :use :system] := #fhir/uri "http://snomed.info/sct"
            [:expansion :contains 0 :designation 4 :use :code] := #fhir/code "900000000000013009"
            [:expansion :contains 0 :designation 4 :use :display] := #fhir/string "Synonym"
            [:expansion :contains 0 :designation 4 :value] := #fhir/string "Dried blood spot specimen"))))))

(deftest expand-value-set-sct-include-filter-is-a-test
  (testing "with a single is-a filter"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-152706"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "is-a"
                    :value #fhir/string "441510007"}]}]}}]]]

      (doseq [request [["url" #fhir/uri "value-set-152706"]
                       ["url" #fhir/uri "value-set-152706"
                        "system-version" #fhir/canonical "http://snomed.info/sct|http://snomed.info/sct/900000000000207008"]
                       ["url" #fhir/uri "value-set-152706"
                        "system-version" #fhir/canonical "http://snomed.info/sct|http://snomed.info/sct/11000274103"]]]
        (given @(apply expand-value-set ts request)
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 3
          [:expansion :contains (concept "445295009") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "445295009") 0 :display] := #fhir/string "Blood specimen with EDTA"
          [:expansion :contains (concept "445295009") 0 #(contains? % :inactive)] := false
          [:expansion :contains (concept "57921000052103") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "57921000052103") 0 :display] := #fhir/string "Whole blood specimen with edetic acid"
          [:expansion :contains (concept "57921000052103") 0 #(contains? % :inactive)] := false
          [:expansion :contains (concept "441510007") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "441510007") 0 :display] := #fhir/string "Blood specimen with anticoagulant"
          [:expansion :contains (concept "441510007") 0 #(contains? % :inactive)] := false))

      (testing "with older version before 57921000052103 was introduced"
        (given @(expand-value-set ts
                  "url" #fhir/uri "value-set-152706"
                  "system-version" #fhir/canonical "http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20220131")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains (concept "445295009") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "445295009") 0 :display] := #fhir/string "Blood specimen with EDTA"
          [:expansion :contains (concept "445295009") 0 #(contains? % :inactive)] := false
          [:expansion :contains (concept "441510007") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "441510007") 0 :display] := #fhir/string "Blood specimen with anticoagulant"
          [:expansion :contains (concept "441510007") 0 #(contains? % :inactive)] := false)))

    (testing "with many children"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-152902"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://snomed.info/sct"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "concept"
                      :op #fhir/code "is-a"
                      :value #fhir/string "123038009"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-152902")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1812
          [:expansion :contains (concept "396807009") 0 :display] := #fhir/string "Specimen from pancreas obtained by pancreaticoduodenectomy, total pancreatectomy"
          [:expansion :contains (concept "433881000124103") 0 :display] := #fhir/string "Combined specimen from swab of anterior nares and throat")))

    (testing "with inactive concepts"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-152936"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://snomed.info/sct"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "concept"
                      :op #fhir/code "is-a"
                      :value #fhir/string "860958002"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-152936")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code "860958002"
          [:expansion :contains 0 :display] := #fhir/string "Temperature of blood")

        (testing "active only"
          (given @(expand-value-set ts
                    "url" #fhir/uri "value-set-152936"
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))))

(deftest expand-value-set-sct-include-filter-descendent-of-test
  (testing "with a single descendent-of filter"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-152706"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "descendent-of"
                    :value #fhir/string "441510007"}]}]}}]]]

      (doseq [request [["url" #fhir/uri "value-set-152706"]
                       ["url" #fhir/uri "value-set-152706"
                        "system-version" #fhir/canonical "http://snomed.info/sct|http://snomed.info/sct/900000000000207008"]]]
        (given @(apply expand-value-set ts request)
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains (concept "445295009") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "445295009") 0 :display] := #fhir/string "Blood specimen with EDTA"
          [:expansion :contains (concept "445295009") 0 #(contains? % :inactive)] := false
          [:expansion :contains (concept "57921000052103") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "57921000052103") 0 :display] := #fhir/string "Whole blood specimen with edetic acid"
          [:expansion :contains (concept "57921000052103") 0 #(contains? % :inactive)] := false))

      (testing "with older version before 57921000052103 was introduced"
        (given @(expand-value-set ts
                  "url" #fhir/uri "value-set-152706"
                  "system-version" #fhir/canonical "http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20220131")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains (concept "445295009") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "445295009") 0 :display] := #fhir/string "Blood specimen with EDTA"
          [:expansion :contains (concept "445295009") 0 #(contains? % :inactive)] := false)))

    (testing "with many children"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-152706"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://snomed.info/sct"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "concept"
                      :op #fhir/code "descendent-of"
                      :value #fhir/string "123038009"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-152706")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1811
          [:expansion :contains (concept "396807009") 0 :display] := #fhir/string "Specimen from pancreas obtained by pancreaticoduodenectomy, total pancreatectomy"
          [:expansion :contains (concept "433881000124103") 0 :display] := #fhir/string "Combined specimen from swab of anterior nares and throat")))

    (testing "with inactive concepts"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-152706"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://snomed.info/sct"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code "860958002"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-152706")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code "860958002"
          [:expansion :contains 0 :display] := #fhir/string "Temperature of blood")

        (testing "active only"
          (given @(expand-value-set ts
                    "url" #fhir/uri "value-set-152706"
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))))

(deftest expand-value-set-sct-include-filter-equals-test
  (testing "parent"
    (with-system [{ts ::ts/local} sct-config]
      (given @(expand-value-set ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://snomed.info/sct"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "parent"
                      :op #fhir/code "="
                      :value (type/string "119297000")}]}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 20
        [:expansion :contains (concept "441510007") 0 :system] := #fhir/uri "http://snomed.info/sct"
        [:expansion :contains (concept "441510007") 0 :display] := #fhir/string "Blood specimen with anticoagulant")))

  (testing "child"
    (testing "with one parent"
      (with-system [{ts ::ts/local} sct-config]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://snomed.info/sct"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "child"
                        :op #fhir/code "="
                        :value (type/string "441510007")}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains (concept "119297000") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "119297000") 0 :display] := #fhir/string "Blood specimen")))

    (testing "with two parents"
      (with-system [{ts ::ts/local} sct-config]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://snomed.info/sct"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "child"
                        :op #fhir/code "="
                        :value (type/string "878861003")}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains (concept "309051001") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "309051001") 0 :display] := #fhir/string "Body fluid specimen"
          [:expansion :contains (concept "446131002") 0 :system] := #fhir/uri "http://snomed.info/sct"
          [:expansion :contains (concept "446131002") 0 :display] := #fhir/string "Blood specimen obtained for blood culture")))))

(deftest expand-value-set-ucum-include-all-test
  (testing "including all of UCUM is not possible"
    (with-system-data [{ts ::ts/local} ucum-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-150638"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://unitsofmeasure.org"}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri "value-set-150638")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-150638`. Expanding all UCUM concepts is not possible."))))

(deftest expand-value-set-ucum-concept-test
  (with-system-data [{ts ::ts/local} ucum-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-152706"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "http://unitsofmeasure.org"
                :concept
                [{:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code "Cel"}
                 {:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code "[degF]"}]}]}}]]]

    (given @(expand-value-set ts "url" #fhir/uri "value-set-152706")
      :fhir/type := :fhir/ValueSet
      [:expansion :contains count] := 2
      [:expansion :contains 0 :system] := #fhir/uri "http://unitsofmeasure.org"
      [:expansion :contains 0 :code] := #fhir/code "Cel"
      [:expansion :contains 1 :system] := #fhir/uri "http://unitsofmeasure.org"
      [:expansion :contains 1 :code] := #fhir/code "[degF]")))

(deftest expand-value-set-other-test
  (testing "display from code system is preserved"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"
                 :display #fhir/string "display-182508"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-115910"
        [:expansion :contains 0 :code] := #fhir/code "code-115927"
        [:expansion :contains 0 :display] := #fhir/string "display-182508"))

    (testing "include only one code"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-163444"
                   :display #fhir/string "display-182523"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code "code-163444"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-163444"
          [:expansion :contains 0 :display] := #fhir/string "display-182523"))))

  (testing "display from value set is used"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-163444"
                 :display #fhir/string "display-182556"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "code-163444"
                    :display #fhir/string "display-182609"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-115910"
        [:expansion :contains 0 :code] := #fhir/code "code-163444"
        [:expansion :contains 0 :display] := #fhir/string "display-182609")))

  (testing "removes id, meta and compose, retains the url and has an expansion timestamp and total"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :meta #fhir/Meta{:versionId #fhir/id "163523"}
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
        :fhir/type := :fhir/ValueSet
        :id := nil
        :meta := nil
        :url := #fhir/uri "value-set-135750"
        :compose := nil
        [:expansion :timestamp] := #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
        [:expansion :identifier :value] :? uuid-urn?
        [:expansion :total] := #fhir/integer 1
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-115910"
        [:expansion :contains 0 :code] := #fhir/code "code-115927")))

  (testing "including definition"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :meta #fhir/Meta{:versionId #fhir/id "163523"}
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (given @(expand-value-set ts
                "url" #fhir/uri "value-set-135750"
                "includeDefinition" #fhir/boolean true)
        :fhir/type := :fhir/ValueSet
        :id := nil
        :meta := nil
        :url := #fhir/uri "value-set-135750"
        [:compose :include 0 :system] := #fhir/uri "system-115910"
        [:expansion :timestamp] := #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
        [:expansion :identifier :value] :? uuid-urn?
        [:expansion :total] := #fhir/integer 1
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-115910"
        [:expansion :contains 0 :code] := #fhir/code "code-115927")))

  (testing "retains status and version"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :version #fhir/string "version-132003"
               :status #fhir/code "active"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri "value-set-135750")
        :fhir/type := :fhir/ValueSet
        :url := #fhir/uri "value-set-135750"
        :version := #fhir/string "version-132003"
        :status := #fhir/code "active"
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri "system-115910"
        [:expansion :contains 0 :code] := #fhir/code "code-115927")))

  (testing "supports count"
    (testing "zero"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-153115"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (given @(expand-value-set ts
                  "url" #fhir/uri "value-set-135750"
                  "count" #fhir/integer 0)
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "count") 0 :name] := #fhir/string "count"
          [:expansion :total] := #fhir/integer 2
          [:expansion :contains count] := 0)))

    (testing "one"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-153115"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (given @(expand-value-set ts
                  "url" #fhir/uri "value-set-135750"
                  "count" #fhir/integer 1)
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "count") 0 :value] := #fhir/integer 1
          [:expansion :total] := #fhir/integer 2
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri "system-115910"
          [:expansion :contains 0 :code] := #fhir/code "code-115927"))))

  (testing "supports exclude-nested"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-115910"}]}}]]]

      (given @(expand-value-set ts
                "url" #fhir/uri "value-set-135750"
                "excludeNested" #fhir/boolean true)
        :fhir/type := :fhir/ValueSet
        [:expansion (parameter "excludeNested") 0 :value] := #fhir/boolean true))))

(defn- value-set-validate-code [ts & nvs]
  (ts/value-set-validate-code ts (apply fu/parameters nvs)))

(deftest value-set-validate-code-fails-test
  (with-system-data [{ts ::ts/local} complete-config]
    [[[:put {:fhir/type :fhir/CodeSystem :id "0"
             :url #fhir/uri "system-182822"
             :content #fhir/code "complete"
             :concept
             [{:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-182832"
               :display #fhir/string "display-182717"}]}]]]

    (testing "no parameters"
      (given-failed-future (value-set-validate-code ts)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing both parameters `url` and `valueSet`."))

    (testing "missing code"
      (given @(value-set-validate-code ts
                "valueSet" {:fhir/type :fhir/ValueSet})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Missing one of the parameters `code`, `coding` or `codeableConcept`."))

    (testing "missing system"
      (given @(value-set-validate-code ts
                "valueSet" {:fhir/type :fhir/ValueSet}
                "code" #fhir/code "code-211233")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Missing required parameter `system`."))

    (testing "incomplete coding"
      (given @(value-set-validate-code ts
                "valueSet" {:fhir/type :fhir/ValueSet}
                "coding" #fhir/Coding{})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Missing required parameter `coding.code`."))

    (testing "incomplete codeableConcept"
      (given @(value-set-validate-code ts
                "valueSet" {:fhir/type :fhir/ValueSet}
                "codeableConcept" #fhir/CodeableConcept{})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Incorrect parameter `codeableConcept` with no coding.")

      (given @(value-set-validate-code ts
                "valueSet" {:fhir/type :fhir/ValueSet}
                "codeableConcept"
                #fhir/CodeableConcept{:coding [#fhir/Coding{}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Missing required parameter `coding.code`."))

    (testing "codeableConcept with two codings"
      (given @(value-set-validate-code ts
                "valueSet" {:fhir/type :fhir/ValueSet}
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding [#fhir/Coding{}
                           #fhir/Coding{}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Unsupported parameter `codeableConcept` with more than one coding."))

    (testing "both url and valueSet parameters"
      (given-failed-future (value-set-validate-code ts
                             "url" #fhir/uri "value-set-161213"
                             "valueSet" {:fhir/type :fhir/ValueSet})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Both parameters `url` and `valueSet` are given."))

    (testing "unsupported param"
      (given-failed-future (value-set-validate-code ts
                             "date" #fhir/dateTime #system/date-time "2025")
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported parameter `date`."))

    (testing "invalid displayLanguage param"
      (given-failed-future (value-set-validate-code ts
                             "displayLanguage" #fhir/dateTime #system/date-time "2025")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid value for parameter `displayLanguage`. Expect FHIR code or string."))

    (testing "missing displayLanguage param value"
      (given-failed-future (value-set-validate-code ts
                             "displayLanguage" #fhir/code{:id "foo"})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid value for parameter `displayLanguage`. Missing value."))

    (testing "not found"
      (testing "url"
        (given-failed-future (value-set-validate-code ts
                               "url" #fhir/uri "url-194718"
                               "code" #fhir/code "code-083955"
                               "inferSystem" #fhir/boolean true)
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-194718` was not found."))

      (testing "url and version"
        (given-failed-future (value-set-validate-code ts
                               "url" #fhir/uri "url-144258"
                               "valueSetVersion" #fhir/string "version-144244"
                               "code" #fhir/code "code-083955"
                               "inferSystem" #fhir/boolean true)
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-144258|version-144244` was not found.")))

    (testing "unsupported filter operator"
      (doseq [[system code]
              [["system-182822" "code-182832"]
               ["urn:ietf:bcp:13" "text/plain"]
               ["http://unitsofmeasure.org" "s"]
               ["http://loinc.org" "26465-5"]
               ["http://snomed.info/sct" "441510007"]]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system (type/uri system)
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-160019"
                        :op #fhir/code "op-unsupported-120524"
                        :value #fhir/string "value-160032"}]}]}}
                  "code" (type/code code)
                  "system" (type/uri system))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unable to check whether the code is in the provided value set because the value set was invalid. Unsupported filter operator `op-unsupported-120524` in code system `%s`." system))
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := (type/uri system))))

    (testing "missing filter property"
      (doseq [[system code op]
              [["system-182822" "code-182832" "is-a"]
               ["system-182822" "code-182832" "descendent-of"]
               ["system-182822" "code-182832" "exists"]
               ["system-182822" "code-182832" "="]
               ["system-182822" "code-182832" "regex"]
               ["http://loinc.org" "26465-5" "="]
               ["http://loinc.org" "26465-5" "regex"]
               ["http://snomed.info/sct" "441510007" "is-a"]
               ["http://snomed.info/sct" "441510007" "descendent-of"]
               ["http://snomed.info/sct" "441510007" "="]]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system (type/uri system)
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :op (type/code op)
                        :value #fhir/string "value-173753"}]}]}}
                  "code" (type/code code)
                  "system" (type/uri system))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unable to check whether the code is in the provided value set because the value set was invalid. Missing %s filter property in code system `%s`." op system))
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := (type/uri system))))

    (testing "unsupported filter property"
      (doseq [[system code op]
              [["system-182822" "code-182832" "is-a"]
               ["system-182822" "code-182832" "descendent-of"]
               ["http://loinc.org" "26465-5" "="]
               ["http://loinc.org" "26465-5" "regex"]
               ["http://snomed.info/sct" "441510007" "is-a"]
               ["http://snomed.info/sct" "441510007" "descendent-of"]
               ["http://snomed.info/sct" "441510007" "="]]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system (type/uri system)
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-unsupported-174016"
                        :op (type/code op)
                        :value #fhir/string "value-173753"}]}]}}
                  "code" (type/code code)
                  "system" (type/uri system))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unable to check whether the code is in the provided value set because the value set was invalid. Unsupported %s filter property `property-unsupported-174016` in code system `%s`." op system))
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := (type/uri system))))

    (testing "missing filter value"
      (doseq [[system code op property]
              [["system-182822" "code-182832" "is-a" "concept"]
               ["system-182822" "code-182832" "descendent-of" "concept"]
               ["system-182822" "code-182832" "exists" "property-arbitrary-161647"]
               ["system-182822" "code-182832" "=" "property-arbitrary-161647"]
               ["system-182822" "code-182832" "regex" "property-arbitrary-161647"]
               ["http://loinc.org" "26465-5" "=" "COMPONENT"]
               ["http://loinc.org" "26465-5" "=" "PROPERTY"]
               ["http://loinc.org" "26465-5" "=" "TIME_ASPCT"]
               ["http://loinc.org" "26465-5" "=" "SYSTEM"]
               ["http://loinc.org" "26465-5" "=" "SCALE_TYP"]
               ["http://loinc.org" "26465-5" "=" "METHOD_TYP"]
               ["http://loinc.org" "26465-5" "=" "CLASS"]
               ["http://loinc.org" "26465-5" "=" "STATUS"]
               ["http://loinc.org" "26465-5" "=" "CLASSTYPE"]
               ["http://loinc.org" "26465-5" "=" "ORDER_OBS"]
               ["http://loinc.org" "26465-5" "=" "LIST"]
               ["http://loinc.org" "26465-5" "=" "answer-list"]
               ["http://loinc.org" "26465-5" "regex" "COMPONENT"]
               ["http://loinc.org" "26465-5" "regex" "PROPERTY"]
               ["http://loinc.org" "26465-5" "regex" "TIME_ASPCT"]
               ["http://loinc.org" "26465-5" "regex" "SYSTEM"]
               ["http://loinc.org" "26465-5" "regex" "SCALE_TYP"]
               ["http://loinc.org" "26465-5" "regex" "METHOD_TYP"]
               ["http://loinc.org" "26465-5" "regex" "CLASS"]
               ["http://snomed.info/sct" "441510007" "is-a" "concept"]
               ["http://snomed.info/sct" "441510007" "descendent-of" "concept"]
               ["http://snomed.info/sct" "441510007" "=" "parent"]
               ["http://snomed.info/sct" "441510007" "=" "child"]]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system (type/uri system)
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property (type/code property)
                        :op (type/code op)}]}]}}
                  "code" (type/code code)
                  "system" (type/uri system))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unable to check whether the code is in the provided value set because the value set was invalid. Missing %s %s filter value in code system `%s`." property op system))
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := (type/uri system))))

    (testing "invalid filter value"
      (doseq [[system code op property msg]
              [["system-182822" "code-182832" "exists" "property-arbitrary-161647" "Should be one of `true` or `false`."]
               ["system-182822" "code-182832" "regex" "property-arbitrary-161647" "Should be a valid regex pattern."]
               ["http://loinc.org" "26465-5" "=" "STATUS"]
               ["http://loinc.org" "26465-5" "=" "CLASSTYPE"]
               ["http://loinc.org" "26465-5" "=" "ORDER_OBS"]
               ["http://snomed.info/sct" "441510007" "is-a" "concept"]
               ["http://snomed.info/sct" "441510007" "descendent-of" "concept"]
               ["http://snomed.info/sct" "441510007" "=" "parent"]
               ["http://snomed.info/sct" "441510007" "=" "child"]]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system (type/uri system)
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property (type/code property)
                        :op (type/code op)
                        :value #fhir/string "value-invalid-[-174601"}]}]}}
                  "code" (type/code code)
                  "system" (type/uri system))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format (cond-> "Unable to check whether the code is in the provided value set because the value set was invalid. Invalid %s %s filter value `value-invalid-[-174601` in code system `%s`." msg (str " " msg)) property op system))
          [(parameter "code") 0 :value] := (type/code code)
          [(parameter "system") 0 :value] := (type/uri system)))))

  (testing "supplement not found"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-172718"
               :version #fhir/string "version-172730"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :extension
               [#fhir/Extension{:url "http://hl7.org/fhir/StructureDefinition/valueset-supplement"
                                :value #fhir/canonical "system-172718|version-172744"}]
               :url #fhir/uri "value-set-172753"}]]]

      (given-failed-future (value-set-validate-code ts
                             "url" #fhir/uri "value-set-172753"
                             "code" #fhir/code "code-172811"
                             "system" #fhir/uri "system-172822")
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `system-172718|version-172744` was not found."))))

(deftest value-set-validate-code-include-all-test
  (testing "version *"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-105710"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :version #fhir/string "*"}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-105710"
                "code" #fhir/code "code-182832"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-182832"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822"))))

(deftest value-set-validate-code-include-concept-test
  (testing "with one code system"
    (testing "with one code"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-115910"}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-135750"
                  "code" #fhir/code "code-115927"
                  "system" #fhir/uri "system-115910")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-115927"
          [(parameter "system") 0 :value] := #fhir/uri "system-115910"))

      (testing "with versions"
        (testing "choosing an explicit version"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "1.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "1"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "2.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-092722"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "2"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "3.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115357"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri "value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-115910"
                        :version #fhir/string "2.0.0"}]}}]]]

            (given @(value-set-validate-code ts
                      "url" #fhir/uri "value-set-135750"
                      "code" #fhir/code "code-092722"
                      "system" #fhir/uri "system-115910")
              :fhir/type := :fhir/Parameters
              [(parameter "result") 0 :value] := #fhir/boolean true
              [(parameter "code") 0 :value] := #fhir/code "code-092722"
              [(parameter "system") 0 :value] := #fhir/uri "system-115910")))

        (testing "choosing the newest version by default"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "1.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "1"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "2.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-092722"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "2"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "3.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115357"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri "value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-115910"}]}}]]]

            (given @(value-set-validate-code ts
                      "url" #fhir/uri "value-set-135750"
                      "code" #fhir/code "code-115357"
                      "system" #fhir/uri "system-115910")
              :fhir/type := :fhir/Parameters
              [(parameter "result") 0 :value] := #fhir/boolean true
              [(parameter "code") 0 :value] := #fhir/code "code-115357"
              [(parameter "system") 0 :value] := #fhir/uri "system-115910")))

        (testing "choosing the version by parameter"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "1.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115927"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "1"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "2.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-092722"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "2"
                     :url #fhir/uri "system-115910"
                     :version #fhir/string "3.0.0"
                     :content #fhir/code "complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code "code-115357"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri "value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-115910"}]}}]]]

            (given @(value-set-validate-code ts
                      "url" #fhir/uri "value-set-135750"
                      "code" #fhir/code "code-092722"
                      "system" #fhir/uri "system-115910"
                      "system-version" #fhir/canonical "system-115910|2.0.0")
              :fhir/type := :fhir/Parameters
              [(parameter "result") 0 :value] := #fhir/boolean true
              [(parameter "code") 0 :value] := #fhir/code "code-092722"
              [(parameter "system") 0 :value] := #fhir/uri "system-115910"
              [(parameter "version") 0 :value] := #fhir/string "2.0.0")

            (testing "code system with version not found"
              (given @(value-set-validate-code ts
                        "url" #fhir/uri "value-set-135750"
                        "code" #fhir/code "code-092722"
                        "system" #fhir/uri "system-115910"
                        "system-version" #fhir/canonical "system-115910|4.0.0")
                :fhir/type := :fhir/Parameters
                [(parameter "result") 0 :value] := #fhir/boolean false
                [(parameter "code") 0 :value] := #fhir/code "code-092722"
                [(parameter "system") 0 :value] := #fhir/uri "system-115910"
                [(parameter "version") 0 :value] := #fhir/string "4.0.0"
                [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
                [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "not-found"
                [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-found")
                [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "A definition for the code system `system-115910|4.0.0` could not be found, so the code cannot be validated.",
                [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "system"]))))))))

(deftest value-set-validate-code-include-value-set-refs-test
  (testing "one value set ref"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet [#fhir/canonical "value-set-135750"]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-161213"
                "code" #fhir/code "code-180828"
                "system" #fhir/uri "system-180814")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-180828"
        [(parameter "system") 0 :value] := #fhir/uri "system-180814")))

  (testing "two value set refs"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri "system-162531"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-162551"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-162451"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-162531"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "2"
               :url #fhir/uri "value-set-162456"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet
                  [#fhir/canonical "value-set-135750"
                   #fhir/canonical "value-set-162451"]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-162456"
                "code" #fhir/code "code-180828"
                "system" #fhir/uri "system-180814")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-180828"
        [(parameter "system") 0 :value] := #fhir/uri "system-180814")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-162456"
                "code" #fhir/code "code-162551"
                "system" #fhir/uri "system-162531")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-162551"
        [(parameter "system") 0 :value] := #fhir/uri "system-162531")))

  (testing "two value set refs including the same code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-162451"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "2"
               :url #fhir/uri "value-set-162456"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet
                  [#fhir/canonical "value-set-135750"
                   #fhir/canonical "value-set-162451"]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-162456"
                "code" #fhir/code "code-180828"
                "system" #fhir/uri "system-180814")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-180828"
        [(parameter "system") 0 :value] := #fhir/uri "system-180814")))

  (testing "with externally supplied value set"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-180814"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri "value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet [#fhir/canonical "value-set-135750"]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-161213"
                "code" #fhir/code "code-180828"
                "system" #fhir/uri "system-180814"
                "tx-resource"
                {:fhir/type :fhir/ValueSet
                 :url #fhir/uri "value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-180814"}]}})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-180828"
        [(parameter "system") 0 :value] := #fhir/uri "system-180814"))))

(deftest value-set-validate-code-include-filter-is-a-test
  (testing "with a single concept"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "is-a"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-182832"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-182832"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")))

  (testing "with two concepts, a parent and a child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :display #fhir/string "display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-182832"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "is-a"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-182832"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-182832"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-191445"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-191445"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822"))

    (testing "with inactive child"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-182822"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-182832"
                   :display #fhir/string "display-182717"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-191445"
                   :display #fhir/string "display-191448"
                   :property
                   [{:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code "parent"
                     :value #fhir/code "code-182832"}
                    {:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code "inactive"
                     :value #fhir/boolean true}]}]}]]]

        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "concept"
                        :op #fhir/code "is-a"
                        :value #fhir/string "code-182832"}]}]}}
                  "code" #fhir/code "code-191445"
                  "system" #fhir/uri "system-182822")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-191445"
          [(parameter "system") 0 :value] := #fhir/uri "system-182822"
          [(parameter "inactive") 0 :value] := #fhir/boolean true)

        (testing "including only active"
          (given @(value-set-validate-code ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :inactive #fhir/boolean false
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "system-182822"
                        :filter
                        [{:fhir/type :fhir.ValueSet.compose.include/filter
                          :property #fhir/code "concept"
                          :op #fhir/code "is-a"
                          :value #fhir/string "code-182832"}]}]}}
                    "code" #fhir/code "code-191445"
                    "system" #fhir/uri "system-182822")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "The provided code `system-182822#code-191445` was not found in the provided value set."
            [(parameter "code") 0 :value] := #fhir/code "code-191445"
            [(parameter "system") 0 :value] := #fhir/uri "system-182822"
            [(parameter "inactive") 0 :value] := #fhir/boolean true)))))

  (testing "with three concepts, a parent, a child and a child of the child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :display #fhir/string "display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-182832"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-192308"
                 :display #fhir/string "display-192313"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-191445"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "is-a"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-182832"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-182832"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-191445"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-191445"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-192308"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-192308"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822"))

    (testing "works if child of child comes before child"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-182822"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-182832"
                   :display #fhir/string "display-182717"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-192308"
                   :display #fhir/string "display-192313"
                   :property
                   [{:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code "parent"
                     :value #fhir/code "code-191445"}]}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-191445"
                   :display #fhir/string "display-191448"
                   :property
                   [{:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code "parent"
                     :value #fhir/code "code-182832"}]}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-182905"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "system-182822"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "concept"
                      :op #fhir/code "is-a"
                      :value #fhir/string "code-182832"}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-182905"
                  "code" #fhir/code "code-182832"
                  "system" #fhir/uri "system-182822")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-182832"
          [(parameter "system") 0 :value] := #fhir/uri "system-182822")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-182905"
                  "code" #fhir/code "code-191445"
                  "system" #fhir/uri "system-182822")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-191445"
          [(parameter "system") 0 :value] := #fhir/uri "system-182822")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-182905"
                  "code" #fhir/code "code-192308"
                  "system" #fhir/uri "system-182822")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "code-192308"
          [(parameter "system") 0 :value] := #fhir/uri "system-182822")))))

(deftest value-set-validate-code-include-filter-descendent-of-test
  (testing "with a single concept"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "descendent-of"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-182832"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `system-182822#code-182832` was not found in the value set `value-set-182905`."
        [(parameter "code") 0 :value] := #fhir/code "code-182832"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")))

  (testing "with two concepts, a parent and a child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :display #fhir/string "display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-182832"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "descendent-of"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-191445"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-191445"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")))

  (testing "with three concepts, a parent, a child and a child of the child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :display #fhir/string "display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-182832"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-192308"
                 :display #fhir/string "display-192313"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-191445"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "descendent-of"
                    :value #fhir/string "code-182832"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-191445"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-191445"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-192308"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-192308"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822"))))

(deftest value-set-validate-code-include-filter-exists-test
  (testing "with a single concept"
    (testing "without a property"
      (testing "that shouldn't exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-160622"
                        :op #fhir/code "exists"
                        :value #fhir/string "false"}]}]}}]]]

          (given @(value-set-validate-code ts
                    "url" #fhir/uri "value-set-182905"
                    "code" #fhir/code "code-182832"
                    "system" #fhir/uri "system-182822")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-182832"
            [(parameter "system") 0 :value] := #fhir/uri "system-182822")))

      (testing "that should exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-160622"
                        :op #fhir/code "exists"
                        :value #fhir/string "true"}]}]}}]]]

          (given @(value-set-validate-code ts
                    "url" #fhir/uri "value-set-182905"
                    "code" #fhir/code "code-182832"
                    "system" #fhir/uri "system-182822")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "The provided code `system-182822#code-182832` was not found in the value set `value-set-182905`."
            [(parameter "code") 0 :value] := #fhir/code "code-182832"
            [(parameter "system") 0 :value] := #fhir/uri "system-182822"))))

    (testing "with existing property"
      (testing "that shouldn't exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code "property-160631"
                       :value #fhir/string "value-161324"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-160631"
                        :op #fhir/code "exists"
                        :value #fhir/string "false"}]}]}}]]]

          (given @(value-set-validate-code ts
                    "url" #fhir/uri "value-set-182905"
                    "code" #fhir/code "code-182832"
                    "system" #fhir/uri "system-182822")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "The provided code `system-182822#code-182832` was not found in the value set `value-set-182905`."
            [(parameter "code") 0 :value] := #fhir/code "code-182832"
            [(parameter "system") 0 :value] := #fhir/uri "system-182822")))

      (testing "that should exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code "property-160631"
                       :value #fhir/string "value-161324"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "property-160631"
                        :op #fhir/code "exists"
                        :value #fhir/string "true"}]}]}}]]]

          (given @(value-set-validate-code ts
                    "url" #fhir/uri "value-set-182905"
                    "code" #fhir/code "code-182832"
                    "system" #fhir/uri "system-182822")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-182832"
            [(parameter "system") 0 :value] := #fhir/uri "system-182822"))))))

(deftest value-set-validate-code-include-filter-equals-test
  (with-system-data [{ts ::ts/local} config]
    [[[:put {:fhir/type :fhir/CodeSystem :id "0"
             :url #fhir/uri "system-182822"
             :content #fhir/code "complete"
             :concept
             [{:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-175652"
               :display #fhir/string "display-175659"
               :property
               [{:fhir/type :fhir.CodeSystem.concept/property
                 :code #fhir/code "property-175506"
                 :value #fhir/string "value-161324"}]}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-175607"
               :display #fhir/string "display-175610"
               :property
               [{:fhir/type :fhir.CodeSystem.concept/property
                 :code #fhir/code "property-175506"
                 :value #fhir/string "value-175614"}]}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-172215"
               :display #fhir/string "display-172220"
               :property
               [{:fhir/type :fhir.CodeSystem.concept/property
                 :code #fhir/code "property-172030"
                 :value #fhir/string "value-161324"}]}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code "code-175607"
               :display #fhir/string "display-175610"}]}]
      [:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-175628"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "system-182822"
                :filter
                [{:fhir/type :fhir.ValueSet.compose.include/filter
                  :property #fhir/code "property-175506"
                  :op #fhir/code "="
                  :value #fhir/string "value-161324"}]}]}}]]]

    (given @(value-set-validate-code ts
              "url" #fhir/uri "value-set-175628"
              "code" #fhir/code "code-175652"
              "system" #fhir/uri "system-182822")
      :fhir/type := :fhir/Parameters
      [(parameter "result") 0 :value] := #fhir/boolean true
      [(parameter "code") 0 :value] := #fhir/code "code-175652"
      [(parameter "system") 0 :value] := #fhir/uri "system-182822")))

(deftest value-set-validate-code-include-filter-regex-test
  (testing "code"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "a"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "aa"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "ab"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-175628"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "code"
                    :op #fhir/code "regex"
                    :value #fhir/string "a+"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-175628"
                "code" #fhir/code "a"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "a"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-175628"
                "code" #fhir/code "aa"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "aa"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-175628"
                "code" #fhir/code "ab"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `system-182822#ab` was not found in the value set `value-set-175628`."
        [(parameter "code") 0 :value] := #fhir/code "ab"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")))

  (testing "other property"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-145708"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "property-175506"
                   :value #fhir/string "a"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-145731"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "property-175506"
                   :value #fhir/string "aa"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-145738"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "property-150054"
                   :value #fhir/string "aa"}
                  {:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "property-175506"
                   :value #fhir/string "ab"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-175628"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "property-175506"
                    :op #fhir/code "regex"
                    :value #fhir/string "a+"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-175628"
                "code" #fhir/code "code-145731"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-145731"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-175628"
                "code" #fhir/code "code-145708"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-145708"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-175628"
                "code" #fhir/code "code-145738"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `system-182822#code-145738` was not found in the value set `value-set-175628`."
        [(parameter "code") 0 :value] := #fhir/code "code-145738"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822"))))

(deftest value-set-validate-code-include-filter-multiple-test
  (testing "is-a and exists (and the other way around)"
    (let [is-a-filter {:fhir/type :fhir.ValueSet.compose.include/filter
                       :property #fhir/code "concept"
                       :op #fhir/code "is-a"
                       :value #fhir/string "code-182832"}
          leaf-filter {:fhir/type :fhir.ValueSet.compose.include/filter
                       :property #fhir/code "child"
                       :op #fhir/code "exists"
                       :value #fhir/string "false"}]
      (doseq [filters (tu/permutations [is-a-filter leaf-filter])]
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri "system-182822"
                   :content #fhir/code "complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-182832"
                     :display #fhir/string "display-182717"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code "code-191445"
                     :display #fhir/string "display-191448"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code "parent"
                       :value #fhir/code "code-182832"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri "value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "system-182822"
                      :filter filters}]}}]]]

          (given @(value-set-validate-code ts
                    "url" #fhir/uri "value-set-182905"
                    "code" #fhir/code "code-191445"
                    "system" #fhir/uri "system-182822")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code "code-191445"
            [(parameter "system") 0 :value] := #fhir/uri "system-182822")

          (given @(value-set-validate-code ts
                    "url" #fhir/uri "value-set-182905"
                    "code" #fhir/code "code-182832"
                    "system" #fhir/uri "system-182822")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "The provided code `system-182822#code-182832` was not found in the value set `value-set-182905`."
            [(parameter "code") 0 :value] := #fhir/code "code-182832"
            [(parameter "system") 0 :value] := #fhir/uri "system-182822")))))

  (testing "with one exclusion"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-182822"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-182832"
                 :display #fhir/string "display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :display #fhir/string "display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-182832"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "is-a"
                    :value #fhir/string "code-182832"}]}]
                :exclude
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "child"
                    :op #fhir/code "exists"
                    :value #fhir/string "true"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-191445"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code-191445"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-182905"
                "code" #fhir/code "code-182832"
                "system" #fhir/uri "system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `system-182822#code-182832` was not found in the value set `value-set-182905`."
        [(parameter "code") 0 :value] := #fhir/code "code-182832"
        [(parameter "system") 0 :value] := #fhir/uri "system-182822"))))

(deftest value-set-validate-code-bcp-13-include-all-test
  (with-system-data [{ts ::ts/local} bcp-13-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-142647"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "urn:ietf:bcp:13"}]}}]]]

    (given @(value-set-validate-code ts
              "url" #fhir/uri "value-set-142647"
              "code" #fhir/code "text/plain"
              "system" #fhir/uri "urn:ietf:bcp:13")
      :fhir/type := :fhir/Parameters
      [(parameter "result") 0 :value] := #fhir/boolean true
      [(parameter "code") 0 :value] := #fhir/code "text/plain"
      [(parameter "system") 0 :value] := #fhir/uri "urn:ietf:bcp:13")))

(deftest value-set-validate-code-bcp-47-include-all-test
  (with-system-data [{ts ::ts/local} bcp-47-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-142647"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "urn:ietf:bcp:47"}]}}]]]

    (given @(value-set-validate-code ts
              "url" #fhir/uri "value-set-142647"
              "code" #fhir/code "ar"
              "system" #fhir/uri "urn:ietf:bcp:47")
      :fhir/type := :fhir/Parameters
      [(parameter "result") 0 :value] := #fhir/boolean true
      [(parameter "code") 0 :value] := #fhir/code "ar"
      [(parameter "system") 0 :value] := #fhir/uri "urn:ietf:bcp:47")))

(deftest value-set-validate-code-loinc-include-all-test
  (with-system-data [{ts ::ts/local} loinc-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-110250"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "http://loinc.org"}]}}]]]

    (given @(value-set-validate-code ts
              "url" #fhir/uri "value-set-110250"
              "code" #fhir/code "26465-5"
              "system" #fhir/uri "http://loinc.org")
      :fhir/type := :fhir/Parameters
      [(parameter "result") 0 :value] := #fhir/boolean true
      [(parameter "code") 0 :value] := #fhir/code "26465-5"
      [(parameter "display") 0 :value] := #fhir/string "Leukocytes [#/volume] in Cerebral spinal fluid"
      [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
      [(parameter "version") 0 :value] := #fhir/string "2.78"))

  (testing "with supplement"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-103927"
               :content #fhir/code "supplement"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "26465-5"
                 :designation
                 [{:fhir/type :fhir.CodeSystem.concept/designation
                   :language #fhir/code "de"
                   :value #fhir/string "designation-104319"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :extension
               [#fhir/Extension{:url "http://hl7.org/fhir/StructureDefinition/valueset-supplement"
                                :value #fhir/canonical "system-103927"}]
               :url #fhir/uri "value-set-102658"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://loinc.org"}]}}]]]

      (testing "without displayLanguage"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-102658"
                  "code" #fhir/code "26465-5"
                  "system" #fhir/uri "http://loinc.org"
                  "display" #fhir/string "designation-104319")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "26465-5"
          [(parameter "display") 0 :value] := #fhir/string "Leukocytes [#/volume] in Cerebral spinal fluid"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "version") 0 :value] := #fhir/string "2.78"))

      (testing "with displayLanguage"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-102658"
                  "code" #fhir/code "26465-5"
                  "system" #fhir/uri "http://loinc.org"
                  "display" #fhir/string "designation-104319"
                  "displayLanguage" #fhir/code "de")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "26465-5"
          [(parameter "display") 0 :value] := #fhir/string "designation-104319"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "version") 0 :value] := #fhir/string "2.78")))))

(deftest value-set-validate-code-loinc-include-concept-test
  (testing "non-matching concept"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-133356"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://loinc.org"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "26465-5"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-133356"
                "code" #fhir/code "718-7"
                "system" #fhir/uri "http://loinc.org")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://loinc.org#718-7` was not found in the value set `value-set-133356`.",
        [(parameter "code") 0 :value] := #fhir/code "718-7"
        [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://loinc.org#718-7` was not found in the value set `value-set-133356`.",
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"])))

  (testing "active concept"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-133540"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://loinc.org"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "26465-5"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-133540"
                "code" #fhir/code "26465-5"
                "system" #fhir/uri "http://loinc.org")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "26465-5"
        [(parameter "display") 0 :value] := #fhir/string "Leukocytes [#/volume] in Cerebral spinal fluid"
        [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
        [(parameter "version") 0 :value] := #fhir/string "2.78")))

  (testing "inactive concept"
    (with-system [{ts ::ts/local} loinc-config]
      (given @(value-set-validate-code ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code "1009-0"}]}]}}
                "code" #fhir/code "1009-0"
                "system" #fhir/uri "http://loinc.org")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "1009-0"
        [(parameter "display") 0 :value] := #fhir/string "Deprecated Direct antiglobulin test.poly specific reagent [Presence] on Red Blood Cells"
        [(parameter "inactive") 0 :value] := #fhir/boolean true
        [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
        [(parameter "version") 0 :value] := #fhir/string "2.78")

      (testing "with active only"
        (with-system [{ts ::ts/local} loinc-config]
          (given @(value-set-validate-code ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "http://loinc.org"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "1009-0"}]}]}}
                    "code" #fhir/code "1009-0"
                    "system" #fhir/uri "http://loinc.org"
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "The provided code `http://loinc.org#1009-0` was not found in the provided value set.",
            [(parameter "code") 0 :value] := #fhir/code "1009-0"
            [(parameter "display") 0 :value] := #fhir/string "Deprecated Direct antiglobulin test.poly specific reagent [Presence] on Red Blood Cells"
            [(parameter "inactive") 0 :value] := #fhir/boolean true
            [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
            [(parameter "version") 0 :value] := #fhir/string "2.78"
            [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
            [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
            [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
            [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://loinc.org#1009-0` was not found in the provided value set.",
            [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]
            [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "error"
            [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "business-rule"
            [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "code-rule")
            [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "The code `1009-0` is valid but is not active."
            [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string "code"]))))))

(deftest value-set-validate-code-loinc-include-filter-equals-test
  (testing "COMPONENT = LP14449-0/Hemoglobin"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP14449-0" "lp14449-0" "Hemoglobin" "hemoglobin" "HEMOGLOBIN"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "COMPONENT"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "718-7"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "718-7"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Hemoglobin [Mass/volume] in Blood"))))

  (testing "PROPERTY = LP6870-2/Susc"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP6870-2" "lp6870-2" "Susc" "susc" "SUSC"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "PROPERTY"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "18868-0"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "18868-0"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Aztreonam [Susceptibility]"))))

  (testing "TIME_ASPCT = LP6960-1/Pt"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP6960-1" "lp6960-1" "Pt" "pt" "PT"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "TIME_ASPCT"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "718-7"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "718-7"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Hemoglobin [Mass/volume] in Blood"))))

  (testing "SYSTEM = LP7057-5/Bld"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP7057-5" "lp7057-5" "Bld" "bld" "BLD"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "SYSTEM"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "718-7"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "718-7"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Hemoglobin [Mass/volume] in Blood"))))

  (testing "SCALE_TYP = LP7753-9/Qn"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP7753-9" "lp7753-9" "Qn" "qn" "QN"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "SCALE_TYP"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "718-7"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "718-7"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Hemoglobin [Mass/volume] in Blood"))))

  (testing "METHOD_TYP = LP28723-2/Genotyping"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP28723-2" "lp28723-2" "Genotyping" "genotyping" "GENOTYPING"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "METHOD_TYP"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "100983-6"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "100983-6"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "HIV reverse transcriptase failed codons [Identifier] by Genotype method"))))

  (testing "CLASS = LP7789-3/Cyto"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP7789-3" "lp7789-3" "Cyto" "cyto" "CYTO"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "CLASS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "50971-1"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "50971-1"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Cytology report of Bronchial brush Cyto stain"))))

  (testing "CLASS = LP94892-4/Laborders"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["LP94892-4" "lp94892-4" "Laborders" "laborders" "LABORDERS"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "CLASS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "82773-3"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "82773-3"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Lab result time reported"))))

  (testing "STATUS = DISCOURAGED"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["DISCOURAGED" "discouraged"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "STATUS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "69349-9"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "69349-9"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Presence of pressure ulcers - acute [CARE]"))))

  (testing "CLASSTYPE = 3 (Claims attachments)"
    (with-system [{ts ::ts/local} loinc-config]
      (given @(value-set-validate-code ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "CLASSTYPE"
                      :op #fhir/code "="
                      :value #fhir/string "3"}]}]}}
                "code" #fhir/code "39215-9"
                "system" #fhir/uri "http://loinc.org")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "39215-9"
        [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
        [(parameter "display") 0 :value] := #fhir/string "Vision screen finding recency CPHS")))

  (testing "ORDER_OBS = Observation"
    (with-system [{ts ::ts/local} loinc-config]
      (doseq [value ["Observation" "observation" "OBSERVATION"]]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://loinc.org"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code "ORDER_OBS"
                        :op #fhir/code "="
                        :value (type/string value)}]}]}}
                  "code" #fhir/code "18868-0"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "18868-0"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Aztreonam [Susceptibility]"))))

  (testing "LIST/answer-list = LL4049-4"
    (doseq [property-name ["LIST" "answer-list"]]
      (with-system-data [{ts ::ts/local} loinc-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-162809"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property (type/code property-name)
                      :op #fhir/code "="
                      :value #fhir/string "LL4049-4"}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-162809"
                  "code" #fhir/code "LA26421-0"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "LA26421-0"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Consider alternative medication")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-162809"
                  "code" #fhir/code "LA26426-9"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://loinc.org#LA26426-9` was not found in the value set `value-set-162809`."
          [(parameter "code") 0 :value] := #fhir/code "LA26426-9"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org")))))

(deftest value-set-validate-code-loinc-include-filter-regex-test
  (testing "COMPONENT =~ Hemoglobin|Amprenavir"
    (doseq [value ["Hemoglobin|Amprenavir" "hemoglobin|amprenavir" "HEMOGLOBIN|AMPRENAVIR"]]
      (with-system-data [{ts ::ts/local} loinc-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-183437"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "COMPONENT"
                      :op #fhir/code "regex"
                      :value (type/string value)}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "718-7"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "718-7"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Hemoglobin [Mass/volume] in Blood")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "30299-2"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "30299-2"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Amprenavir [Susceptibility]"))))

  (testing "PROPERTY =~ Susc|CCnc"
    (doseq [value ["Susc|CCnc" "susc|ccnc" "SUSC|CCNC"]]
      (with-system-data [{ts ::ts/local} loinc-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-183437"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "PROPERTY"
                      :op #fhir/code "regex"
                      :value (type/string value)}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "3036-1"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "3036-1"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Transketolase [Enzymatic activity/volume] in Serum")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "30299-2"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "30299-2"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Amprenavir [Susceptibility]"))))

  (testing "TIME_ASPCT =~ 10H|18H"
    (doseq [value ["10H|18H" "10h|18h"]]
      (with-system-data [{ts ::ts/local} loinc-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-183437"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "TIME_ASPCT"
                      :op #fhir/code "regex"
                      :value (type/string value)}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "63474-1"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "63474-1"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Microalbumin [Mass/time] in 18 hour Urine")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "8323-8"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "8323-8"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Body temperature 10 hour"))))

  (testing "SYSTEM =~ Bld|Ser/Plas"
    (doseq [value ["Bld|Ser/Plas" "bld|ser/plas" "BLD|SER/PLAS"]]
      (with-system-data [{ts ::ts/local} loinc-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-183437"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "SYSTEM"
                      :op #fhir/code "regex"
                      :value (type/string value)}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "718-7"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "718-7"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Hemoglobin [Mass/volume] in Blood")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "47595-4"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "47595-4"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "C peptide [Moles/volume] in Serum or Plasma --pre dose glucose"))))

  (testing "SCALE_TYP =~ Qn|Ord"
    (doseq [value ["Qn|Ord" "qn|ord" "QN|ORD"]]
      (with-system-data [{ts ::ts/local} loinc-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-183437"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "SCALE_TYP"
                      :op #fhir/code "regex"
                      :value (type/string value)}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "718-7"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "718-7"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Hemoglobin [Mass/volume] in Blood")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "4764-7"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "4764-7"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "HLA-B55(22) [Presence] in Donor"))))

  (testing "METHOD_TYP =~ Genotyping|Molgen"
    (doseq [value ["Genotyping|Molgen" "genotyping|molgen" "GENOTYPING|MOLGEN"]]
      (with-system-data [{ts ::ts/local} loinc-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-183437"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "METHOD_TYP"
                      :op #fhir/code "regex"
                      :value (type/string value)}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "100983-6"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "100983-6"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "HIV reverse transcriptase failed codons [Identifier] by Genotype method")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "48577-1"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "48577-1"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Deprecated HFE gene c.845G>A [Presence] in Blood or Tissue by Molecular genetics method"))))

  (testing "CLASS =~ Cyto|Laborders"
    (doseq [value ["Cyto|Laborders" "CYTO|LABORDERS" "cyto|laborders"]]
      (with-system-data [{ts ::ts/local} loinc-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri "value-set-183437"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://loinc.org"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code "CLASS"
                      :op #fhir/code "regex"
                      :value (type/string value)}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "50971-1"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "50971-1"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Cytology report of Bronchial brush Cyto stain")

        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-183437"
                  "code" #fhir/code "82773-3"
                  "system" #fhir/uri "http://loinc.org")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "82773-3"
          [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
          [(parameter "display") 0 :value] := #fhir/string "Lab result time reported")))))

(deftest value-set-validate-code-loinc-include-filter-multiple-test
  (testing "http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-190529"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "STATUS"
                    :op #fhir/code "="
                    :value #fhir/string "ACTIVE"}
                   {:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "CLASSTYPE"
                    :op #fhir/code "="
                    :value #fhir/string "1"}]}]
                :exclude
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "CLASS"
                    :op #fhir/code "regex"
                    :value #fhir/string "CYTO|HL7\\.CYTOGEN|HL7\\.GENETICS|^PATH(\\..*)?|^MOLPATH(\\..*)?|NR STATS|H&P\\.HX\\.LAB|CHALSKIN|LABORDERS"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-190529"
                "code" #fhir/code "100046-2"
                "system" #fhir/uri "http://loinc.org")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "100046-2"
        [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
        [(parameter "display") 0 :value] := #fhir/string "Cefquinome [Susceptibility]")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-190529"
                "code" #fhir/code "50971-1"
                "system" #fhir/uri "http://loinc.org")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://loinc.org#50971-1` was not found in the value set `value-set-190529`."
        [(parameter "code") 0 :value] := #fhir/code "50971-1"
        [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org")

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-190529"
                "code" #fhir/code "82773-3"
                "system" #fhir/uri "http://loinc.org")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://loinc.org#82773-3` was not found in the value set `value-set-190529`."
        [(parameter "code") 0 :value] := #fhir/code "82773-3"
        [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"))))

(deftest value-set-validate-code-loinc-implicit-test
  (testing "answer list LL4049-4"
    (with-system [{ts ::ts/local} loinc-config]
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://loinc.org/vs/LL4049-4"
                "code" #fhir/code "LA26422-8"
                "system" #fhir/uri "http://loinc.org")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "LA26422-8"
        [(parameter "system") 0 :value] := #fhir/uri "http://loinc.org"
        [(parameter "display") 0 :value] := #fhir/string "Decrease dose"
        [(parameter "version") 0 :value] := #fhir/string "2.78")))

  (testing "unknown"
    (with-system [{ts ::ts/local} loinc-config]
      (given-failed-future
       (value-set-validate-code ts
         "url" #fhir/uri "http://loinc.org/vs/unknown-210800"
         "code" #fhir/code "LA26422-8"
         "system" #fhir/uri "http://loinc.org")
        ::anom/category := ::anom/not-found
        ::anom/message := "The value set `http://loinc.org/vs/unknown-210800` was not found."))))

(deftest value-set-validate-code-sct-include-all-test
  (with-system-data [{ts ::ts/local} sct-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-102658"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "http://snomed.info/sct"}]}}]]]

    (given @(value-set-validate-code ts
              "url" #fhir/uri "value-set-102658"
              "code" #fhir/code "441510007"
              "system" #fhir/uri "http://snomed.info/sct")
      :fhir/type := :fhir/Parameters
      [(parameter "result") 0 :value] := #fhir/boolean true
      [(parameter "code") 0 :value] := #fhir/code "441510007"
      [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
      [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
      [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"))

  (testing "with supplement"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-103927"
               :content #fhir/code "supplement"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "441510007"
                 :designation
                 [{:fhir/type :fhir.CodeSystem.concept/designation
                   :language #fhir/code "de"
                   :value #fhir/string "designation-104319"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :extension
               [#fhir/Extension{:url "http://hl7.org/fhir/StructureDefinition/valueset-supplement"
                                :value #fhir/canonical "system-103927"}]
               :url #fhir/uri "value-set-102658"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"}]}}]]]

      (testing "without displayLanguage"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-102658"
                  "code" #fhir/code "441510007"
                  "system" #fhir/uri "http://snomed.info/sct"
                  "display" #fhir/string "designation-104319")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"))

      (testing "with displayLanguage"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-102658"
                  "code" #fhir/code "441510007"
                  "system" #fhir/uri "http://snomed.info/sct"
                  "display" #fhir/string "designation-104319"
                  "displayLanguage" #fhir/code "de")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "display") 0 :value] := #fhir/string "designation-104319"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001")))))

(deftest value-set-validate-code-sct-include-concept-test
  (testing "non-matching concept"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-120641"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "860958002"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-120641"
                "code" #fhir/code "441510007"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-120641`.",
        [(parameter "code") 0 :value] := #fhir/code "441510007"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-120641`.",
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"])))

  (testing "active concept"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-120641"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "441510007"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-120641"
                "code" #fhir/code "441510007"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "441510007"
        [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001")))

  (testing "inactive concept"
    (with-system [{ts ::ts/local} sct-config]
      (given @(value-set-validate-code ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri "http://snomed.info/sct"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code "860958002"}]}]}}
                "code" #fhir/code "860958002"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "860958002"
        [(parameter "display") 0 :value] := #fhir/string "Temperature of blood"
        [(parameter "inactive") 0 :value] := #fhir/boolean true
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001")

      (testing "with active only"
        (with-system [{ts ::ts/local} sct-config]
          (given @(value-set-validate-code ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri "http://snomed.info/sct"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code "860958002"}]}]}}
                    "code" #fhir/code "860958002"
                    "system" #fhir/uri "http://snomed.info/sct"
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#860958002` was not found in the provided value set.",
            [(parameter "code") 0 :value] := #fhir/code "860958002"
            [(parameter "display") 0 :value] := #fhir/string "Temperature of blood"
            [(parameter "inactive") 0 :value] := #fhir/boolean true
            [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
            [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
            [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
            [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
            [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
            [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#860958002` was not found in the provided value set.",
            [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]
            [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "error"
            [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "business-rule"
            [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "code-rule")
            [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "The code `860958002` is valid but is not active."
            [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string "code"])))))

  (testing "with version (module)"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-152014"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :version #fhir/string "http://snomed.info/sct/900000000000207008"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "441510007"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-152014"
                "code" #fhir/code "441510007"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "441510007"
        [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001")))

  (testing "with version"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-152138"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :version #fhir/string "http://snomed.info/sct/900000000000207008/version/20231201"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "441510007"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-152138"
                "code" #fhir/code "441510007"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "441510007"
        [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20231201")

      (testing "same version from request"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-152138"
                  "code" #fhir/code "441510007"
                  "system" #fhir/uri "http://snomed.info/sct"
                  "systemVersion" #fhir/string "http://snomed.info/sct/900000000000207008/version/20231201")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20231201"))

      (testing "different version from request"
        (doseq [[param-name param-value]
                [["systemVersion" #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"]
                 ["system-version" #fhir/canonical "http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20241001"]]]
          (given @(value-set-validate-code ts
                    "url" #fhir/uri "value-set-152138"
                    "code" #fhir/code "441510007"
                    "system" #fhir/uri "http://snomed.info/sct"
                    param-name param-value)
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "code") 0 :value] := #fhir/code "441510007"
            [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
            [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
            [(parameter "issues") 0 :resource :issue count] := 2
            [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
            [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
            [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
            [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-152138`."
            [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]
            [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "error"
            [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "not-found"
            [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "not-found")
            [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "A definition for the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20241001` could not be found, so the code cannot be validated."))))

    (testing "non-existing version"
      (with-system [{ts ::ts/local} sct-config]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri "http://snomed.info/sct"
                      :version #fhir/string "http://snomed.info/sct/900000000000207008/version/none-existing"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code "441510007"}]}]}}
                  "code" #fhir/code "441510007"
                  "system" #fhir/uri "http://snomed.info/sct")
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "A definition for the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/none-existing` could not be found, so the code cannot be validated.",
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/none-existing"
          [(parameter "issues") 0 :resource :issue count] := 2
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "not-found"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-found")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "A definition for the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/none-existing` could not be found, so the code cannot be validated."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "system"]
          [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "warning"
          [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "not-found"
          [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "vs-invalid")
          [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "Unable to check whether the code is in the provided value set because the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/none-existing` was not found."))))

  (testing "synonym display"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-120641"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code "441510007"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-120641"
                "code" #fhir/code "441510007"
                "system" #fhir/uri "http://snomed.info/sct"
                "display" #fhir/string "Blood specimen with anticoagulant")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "441510007"
        [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"))))

(deftest value-set-validate-code-sct-include-filter-is-a-test
  (with-system-data [{ts ::ts/local} sct-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-113851"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "http://snomed.info/sct"
                :filter
                [{:fhir/type :fhir.ValueSet.compose.include/filter
                  :property #fhir/code "concept"
                  :op #fhir/code "is-a"
                  :value #fhir/string "441510007"}]}]}}]]]

    (testing "direct code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-113851"
                "code" #fhir/code "441510007"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "441510007"
        [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"))

    (testing "child code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-113851"
                "code" #fhir/code "445295009"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "445295009"
        [(parameter "display") 0 :value] := #fhir/string "Blood specimen with EDTA"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"))

    (testing "grand child code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-113851"
                "code" #fhir/code "57921000052103"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "57921000052103"
        [(parameter "display") 0 :value] := #fhir/string "Whole blood specimen with edetic acid"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001")

      (testing "with older version before 57921000052103 was introduced"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "57921000052103"
                  "system" #fhir/uri "http://snomed.info/sct"
                  "systemVersion" #fhir/string "http://snomed.info/sct/900000000000207008/version/20220131")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#57921000052103` was not found in the value set `value-set-113851`."
          [(parameter "code") 0 :value] := #fhir/code "57921000052103"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20220131"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#57921000052103` was not found in the value set `value-set-113851`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"])))

    (testing "parent code is not included"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-113851"
                "code" #fhir/code "119297000"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
        [(parameter "code") 0 :value] := #fhir/code "119297000"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"])))

  (testing "Germany module"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-113851"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :version #fhir/string "http://snomed.info/sct/11000274103"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "concept"
                    :op #fhir/code "is-a"
                    :value #fhir/string "441510007"}]}]}}]]]

      (testing "direct code"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "441510007"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen with anticoagulant"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/11000274103/version/20241115"))

      (testing "child code"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "445295009"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "445295009"
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen with EDTA"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/11000274103/version/20241115"))

      (testing "grand child code"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "57921000052103"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "57921000052103"
          [(parameter "display") 0 :value] := #fhir/string "Whole blood specimen with edetic acid"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/11000274103/version/20241115"))

      (testing "parent code is not included"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "119297000"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
          [(parameter "code") 0 :value] := #fhir/code "119297000"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/11000274103/version/20241115"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"])))))

(deftest value-set-validate-code-sct-include-filter-descendent-of-test
  (with-system-data [{ts ::ts/local} sct-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri "value-set-113851"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri "http://snomed.info/sct"
                :filter
                [{:fhir/type :fhir.ValueSet.compose.include/filter
                  :property #fhir/code "concept"
                  :op #fhir/code "descendent-of"
                  :value #fhir/string "441510007"}]}]}}]]]

    (testing "direct code is not included"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-113851"
                "code" #fhir/code "441510007"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-113851`."
        [(parameter "code") 0 :value] := #fhir/code "441510007"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-113851`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]))

    (testing "child code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-113851"
                "code" #fhir/code "445295009"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "445295009"
        [(parameter "display") 0 :value] := #fhir/string "Blood specimen with EDTA"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"))

    (testing "grand child code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-113851"
                "code" #fhir/code "57921000052103"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "57921000052103"
        [(parameter "display") 0 :value] := #fhir/string "Whole blood specimen with edetic acid"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001")

      (testing "with older version before 57921000052103 was introduced"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "57921000052103"
                  "system" #fhir/uri "http://snomed.info/sct"
                  "systemVersion" #fhir/string "http://snomed.info/sct/900000000000207008/version/20220131")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#57921000052103` was not found in the value set `value-set-113851`."
          [(parameter "code") 0 :value] := #fhir/code "57921000052103"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20220131"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#57921000052103` was not found in the value set `value-set-113851`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"])))

    (testing "parent code is not included"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "value-set-113851"
                "code" #fhir/code "119297000"
                "system" #fhir/uri "http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
        [(parameter "code") 0 :value] := #fhir/code "119297000"
        [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]))))

(deftest value-set-validate-code-sct-include-filter-equals-test
  (testing "parent"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-113851"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "parent"
                    :op #fhir/code "="
                    :value #fhir/string "441510007"}]}]}}]]]

      (testing "direct code is not included"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "441510007"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-113851`."
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-113851`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]))

      (testing "child code"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "445295009"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "445295009"
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen with EDTA"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"))

      (testing "grand child code is not included"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "57921000052103"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#57921000052103` was not found in the value set `value-set-113851`."
          [(parameter "code") 0 :value] := #fhir/code "57921000052103"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#57921000052103` was not found in the value set `value-set-113851`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]))

      (testing "parent code is not included"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "119297000"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
          [(parameter "code") 0 :value] := #fhir/code "119297000"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]))))

  (testing "child"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri "value-set-113851"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri "http://snomed.info/sct"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code "child"
                    :op #fhir/code "="
                    :value #fhir/string "441510007"}]}]}}]]]

      (testing "direct code is not included"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "441510007"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-113851`."
          [(parameter "code") 0 :value] := #fhir/code "441510007"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-113851`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]))

      (testing "parent code"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "119297000"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code "119297000"
          [(parameter "display") 0 :value] := #fhir/string "Blood specimen"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"))

      (testing "grand parent code is not included"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "123038009"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#123038009` was not found in the value set `value-set-113851`."
          [(parameter "code") 0 :value] := #fhir/code "123038009"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#123038009` was not found in the value set `value-set-113851`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]))

      (testing "child code is not included"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri "value-set-113851"
                  "code" #fhir/code "445295009"
                  "system" #fhir/uri "http://snomed.info/sct")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string "The provided code `http://snomed.info/sct#445295009` was not found in the value set `value-set-113851`."
          [(parameter "code") 0 :value] := #fhir/code "445295009"
          [(parameter "system") 0 :value] := #fhir/uri "http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://snomed.info/sct#445295009` was not found in the value set `value-set-113851`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"])))))

(def ^:private parsing-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}))

(defn- load-resource [test name]
  (fhir-spec/parse-json parsing-context (slurp (io/resource (format "tx-ecosystem/%s/%s.json" test name)))))

(deftest tx-ecosystem-validation-tests
  (with-system-data [{ts ::ts/local} config]
    [[[:put (load-resource "simple" "codesystem-simple")]
      [:put (load-resource "simple" "valueset-all")]
      [:put (load-resource "simple" "valueset-import-bad")]
      [:put (load-resource "simple" "valueset-enumerated")]
      [:put (load-resource "simple" "valueset-filter-isa")]
      [:put (load-resource "simple" "valueset-filter-property")]
      [:put (load-resource "simple" "valueset-filter-regex")]
      [:put (load-resource "language" "codesystem-de-multi")]
      [:put (load-resource "language" "codesystem-de-single")]
      [:put (load-resource "language" "codesystem-en-multi")]
      [:put (load-resource "language" "codesystem-en-single")]
      [:put (load-resource "language" "valueset-de-multi")]
      [:put (load-resource "language" "valueset-de-single")]
      [:put (load-resource "language" "valueset-en-multi")]
      [:put (load-resource "language" "valueset-en-single")]
      [:put (load-resource "language" "valueset-en-enlang-multi")]
      [:put (load-resource "language" "valueset-en-en-multi")]
      [:put (load-resource "version" "codesystem-version-1")]
      [:put (load-resource "version" "codesystem-version-2")]
      [:put (load-resource "version" "valueset-all-version-1")]
      [:put (load-resource "version" "valueset-all-version-2")]
      [:put (load-resource "version" "valueset-all-version")]
      [:put (load-resource "inactive" "valueset-all")]
      [:put (load-resource "inactive" "codesystem-inactive")]]]

    (testing "validation-simple-code-good"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code "code1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string "0.1.0"))

    (testing "validation-simple-code-implied-good"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code "code1"
                "inferSystem" #fhir/boolean true)
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string "0.1.0"))

    (testing "validation-simple-coding-good"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
                  :code #fhir/code "code1"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string "0.1.0"))

    (testing "validation-simple-codeableconcept-good"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
                     :code #fhir/code "code1"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string "0.1.0"
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
                                                         :code #fhir/code "code1"}]}))

    (testing "validation-simple-code-bad-code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code "code1x"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code "code1x"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "invalid-code")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple`."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string "code"]))

    (testing "validation-simple-code-implied-bad-code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code "code1x"
                "inferSystem" #fhir/boolean true)
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code "code1x"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "not-found"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "cannot-infer")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "The provided code `code1x` is not known to belong to the inferred code system `http://hl7.org/fhir/test/CodeSystem/simple`."))

    (testing "validation-simple-coding-bad-code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
                  :code #fhir/code "code1x"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code "code1x"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "Coding.code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "invalid-code")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple`."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string "Coding.code"]))

    (testing "validation-simple-coding-bad-code-inactive"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/inactive-all"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/inactive"
                  :code #fhir/code "codeInactive"}
                "activeOnly" #fhir/boolean true)
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/inactive#codeInactive` was not found in the value set `http://hl7.org/fhir/test/ValueSet/inactive-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code "codeInactive"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/inactive"
        [(parameter "version") 0 :value] := #fhir/string "0.1.0"
        [(parameter "display") 0 :value] := #fhir/string "Display inactive"
        [(parameter "inactive") 0 :value] := #fhir/boolean true
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/inactive"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/inactive#codeInactive` was not found in the value set `http://hl7.org/fhir/test/ValueSet/inactive-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "Coding.code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "business-rule"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "code-rule")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "The code `codeInactive` is valid but is not active."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string "Coding.code"]))

    (testing "validation-simple-codeableconcept-bad-code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
                     :code #fhir/code "code1x"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
                                                         :code #fhir/code "code1x"}]}
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "CodeableConcept.coding[0].code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "invalid-code")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple`."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string "CodeableConcept.coding[0].code"]))

    (testing "validation-simple-code-bad-valueSet"
      (given-failed-future (value-set-validate-code ts
                             "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-allX"
                             "code" #fhir/code "code1"
                             "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple")
        ::anom/category := ::anom/not-found
        ::anom/message := "The value set `http://hl7.org/fhir/test/ValueSet/simple-allX` was not found."))

    (testing "validation-simple-code-bad-import"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-import-bad"
                "code" #fhir/code "code1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "A definition for the value Set `http://hl7.org/fhir/test/ValueSet/simple-filter-isaX` could not be found."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "not-found"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-found")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "A definition for the value Set `http://hl7.org/fhir/test/ValueSet/simple-filter-isaX` could not be found."
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "warning"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "not-found"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "vs-invalid")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "Unable to check whether the code is in the value set `http://hl7.org/fhir/test/ValueSet/simple-import-bad|5.0.0` because the value set `http://hl7.org/fhir/test/ValueSet/simple-filter-isaX` was not found."))

    (testing "validation-simple-code-bad-system"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code "code1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simplex")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "A definition for the code system `http://hl7.org/fhir/test/CodeSystem/simplex` could not be found, so the code cannot be validated."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simplex"
        [(parameter "issues") 0 :resource :issue count] := 2
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/simplex#code1` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "not-found"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "not-found")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "A definition for the code system `http://hl7.org/fhir/test/CodeSystem/simplex` could not be found, so the code cannot be validated."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string "system"]))

    (testing "validation-simple-coding-no-system"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "coding" #fhir/Coding{:code #fhir/code "code1"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `code1` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `code1` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "Coding.code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code "warning"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code "invalid"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "invalid-data")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string "Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string "Coding"]))

    (testing "validation-simple-code-bad-version1"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code "code1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
                "systemVersion" #fhir/string "1.0.0")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "A definition for the code system `http://hl7.org/fhir/test/CodeSystem/simple|1.0.0` could not be found, so the code cannot be validated."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "not-found"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-found")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "A definition for the code system `http://hl7.org/fhir/test/CodeSystem/simple|1.0.0` could not be found, so the code cannot be validated."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "system"]))

    (testing "validation-simple-code-good-version"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code "code1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                "systemVersion" #fhir/string "1.0.0")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.0)"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"))

    (testing "validation-simple-codeableconcept-good-version"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all-1"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                     :version #fhir/string "1.0.0"
                     :code #fhir/code "code1"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.0)"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                                                         :version #fhir/string "1.0.0"
                                                         :code #fhir/code "code1"}]}))

    (testing "validation-simple-code-good-display"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code "code1"
                "display" #fhir/string "Display 1 (1.0)"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                "systemVersion" #fhir/string "1.0.0")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.0)"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"))

    (testing "validation-simple-codeableconcept-good-display"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all-1"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                     :display #fhir/string "Display 1 (1.0)"
                     :code #fhir/code "code1"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.0)"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                                                         :display #fhir/string "Display 1 (1.0)"
                                                         :code #fhir/code "code1"}]}))

    (testing "validation-simple-code-bad-display"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code "code1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                "display" #fhir/string "Display 1X")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Invalid display `Display 1X` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.0)"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `Display 1X` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "display"]))

    (testing "validation-simple-code-bad-display-ws"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code "code1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                "display" #fhir/string "Display  1 (1.0)")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Invalid display `Display  1 (1.0)` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.0)"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `Display  1 (1.0)` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "display"]))

    (testing "validation-simple-coding-bad-display"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all-1"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                  :code #fhir/code "code1"
                  :display #fhir/string "Display 1X"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Invalid display `Display 1X` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.0)"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `Display 1X` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "Coding.display"]))

    (testing "validation-simple-code-bad-display-warning"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code "code1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                "display" #fhir/string "Display  1 (1.0)"
                "lenient-display-validation" #fhir/boolean true)
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "message") 0 :value] := #fhir/string "Invalid display `Display  1 (1.0)` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.0)"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "warning"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `Display  1 (1.0)` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "display"]))

    (testing "validation-simple-code-good-language"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/en-multi"
                "code" #fhir/code "code1"
                "display" #fhir/string "Anzeige 1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/en-multi"
                "displayLanguage" #fhir/code "de,it,zh")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Anzeige 1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/en-multi"))

    (testing "validation-simple-code-bad-language"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/en-multi"
                "code" #fhir/code "code1"
                "display" #fhir/code "Anzeige 1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/en-multi"
                "displayLanguage" #fhir/code "en")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/en-multi"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "display"]))

    (testing "validation-simple-code-good-regex"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-filter-regex"
                "code" #fhir/code "code1"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string "0.1.0"))

    (testing "validation-simple-code-bad-regex"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/simple-filter-regex"
                "code" #fhir/code "code2a"
                "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code2a` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-filter-regex|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code "code2a"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string "0.1.0"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code2a` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-filter-regex|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]))

    (testing "validation-simple-coding-bad-language-vs"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/en-en-multi"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/en-multi"
                  :code #fhir/code "code1"
                  :display #fhir/string "Anzeige 1"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/en-multi"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "Coding.display"]))

    (testing "validation-simple-coding-bad-language-vslang"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/en-enlang-multi"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/en-multi"
                  :code #fhir/code "code1"
                  :display #fhir/string "Anzeige 1"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/en-multi"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "Coding.display"]))

    (testing "validation-version-profile-none"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                  :code #fhir/code "code1"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.2)"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.2.0"))

    (testing "validation-version-profile-default"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all"
                "valueSetVersion" #fhir/string "1.0.0"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                  :code #fhir/code "code1"}
                "system-version" #fhir/canonical "http://hl7.org/fhir/test/CodeSystem/version|1.0.0")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.0)"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.0.0"))

    (testing "validation-version-profile-coding"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/version-all"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
                  :code #fhir/code "code1"
                  :version #fhir/string "1.2.0"}
                "system-version" #fhir/canonical "http://hl7.org/fhir/test/CodeSystem/version|1.0.0")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1 (1.2)"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string "1.2.0"))

    (testing "validation-cs-code-good"
      (given @(code-system-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
                "code" #fhir/code "code1")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string "0.1.0"))

    (testing "validation-cs-code-bad-code"
      (given @(code-system-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
                "code" #fhir/code "code1x")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple|0.1.0`."
        [(parameter "code") 0 :value] := #fhir/code "code1x"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-code")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple|0.1.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "code"]))))

(deftest tx-ecosystem-other-tests
  (with-system-data [{ts ::ts/local} config]
    [[[:put (load-resource "other" "codesystem-dual-filter")]
      [:put (load-resource "other" "valueset-dual-filter")]]]

    (testing "validation-dual-filter-in"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/dual-filter"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/dual-filter"
                     :code #fhir/code "AA1"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "AA1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/dual-filter"
        [(parameter "display") 0 :value] := #fhir/string "AA1"
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/dual-filter"
                                                         :code #fhir/code "AA1"}]}))

    (testing "validation-dual-filter-out"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/dual-filter"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/dual-filter"
                     :code #fhir/code "AA"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/dual-filter#AA` was not found in the value set `http://hl7.org/fhir/test/ValueSet/dual-filter`."
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/dual-filter"
                                                         :code #fhir/code "AA"}]}
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code "error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code "code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string "The provided code `http://hl7.org/fhir/test/CodeSystem/dual-filter#AA` was not found in the value set `http://hl7.org/fhir/test/ValueSet/dual-filter`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string "CodeableConcept.coding[0].code"]))))

(deftest tx-ecosystem-extensions-tests
  (with-system-data [{ts ::ts/local} config]
    [[[:put (load-resource "extensions" "codesystem-extensions")]
      [:put (load-resource "extensions" "codesystem-supplement")]
      [:put (load-resource "extensions" "valueset-extensions-all")]
      [:put (load-resource "extensions" "valueset-extensions-bad-supplement")]
      [:put (load-resource "extensions" "valueset-extensions-enumerated")]]]

    (testing "validate-code-bad-supplement"
      (given-failed-future
       (value-set-validate-code ts
         "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/extensions-bad-supplement"
         "code" #fhir/code "code1"
         "system" #fhir/uri "http://hl7.org/fhir/test/CodeSystem/extensions")
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `http://hl7.org/fhir/test/CodeSystem/supplementX` was not found."))

    (testing "validate-coding-bad-supplement"
      (given-failed-future
       (value-set-validate-code ts
         "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/extensions-bad-supplement"
         "coding"
         #fhir/Coding
          {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/extensions"
           :code #fhir/code "code1"})
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `http://hl7.org/fhir/test/CodeSystem/supplementX` was not found."))

    (testing "validate-codeableconcept-bad-supplement"
      (given-failed-future
       (value-set-validate-code ts
         "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/extensions-bad-supplement"
         "codeableConcept"
         #fhir/CodeableConcept
          {:coding
           [#fhir/Coding
             {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/extensions"
              :code #fhir/code "code1"}]})
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `http://hl7.org/fhir/test/CodeSystem/supplementX` was not found."))

    (testing "validate-coding-good-supplement"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/extensions-enumerated"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/extensions"
                  :code #fhir/code "code1"
                  :display #fhir/string "ectenoot"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "Display 1"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/extensions"))

    (testing "validate-coding-good2-supplement"
      (given @(value-set-validate-code ts
                "url" #fhir/uri "http://hl7.org/fhir/test/ValueSet/extensions-enumerated"
                "displayLanguage" #fhir/code "nl"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri "http://hl7.org/fhir/test/CodeSystem/extensions"
                  :code #fhir/code "code1"
                  :display #fhir/string "ectenoot"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code "code1"
        [(parameter "display") 0 :value] := #fhir/string "ectenoot"
        [(parameter "system") 0 :value] := #fhir/uri "http://hl7.org/fhir/test/CodeSystem/extensions"))))
