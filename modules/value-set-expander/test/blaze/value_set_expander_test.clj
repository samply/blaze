(ns blaze.value-set-expander-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.search-param-registry]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.terminology-service.protocols :as tsp]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [blaze.value-set-expander :as vse]
   [blaze.value-set-expander-spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(tu/set-default-locale-english!)
(test/use-fixtures :each tu/fixture)

(def ^:private system-1 "http://fhir.de/CodeSystem/bfarm/icd-10-gm")
(def ^:private system-2 "http://snomed.info/sct")

(defn- concept [system code]
  (cond-> {:fhir/type :fhir.ValueSet.expansion/contains
           :code (type/code code)}
    system (assoc :system (type/uri system))))

(defn- concepts [prefix n]
  (mapv #(concept system-2 (str prefix %)) (range n)))

(def ^:private expansions
  {"url-1" [(concept system-1 "C71.4") (concept system-1 "C69.4")]
   "url-2" [(concept system-1 "C73") (concept system-1 "C69.4")]
   "url-3" [(concept system-2 "254637007")]
   "url-without-system" [(concept nil "foo")]
   "url-empty" []
   "url-empty-2" []
   "url-10000" (concepts "a" 10000)
   "url-10001" (concepts "a" 10001)
   "url-5000-a" (concepts "a" 5000)
   "url-5001-b" (concepts "b" 5001)})

(defn- url [{[{{:keys [value]} :value}] :parameter}]
  value)

(defmethod ig/init-key ::terminology-service [_ _]
  (let [calls (atom [])]
    (with-meta
      (reify tsp/TerminologyService
        (-expand-value-set [_ params]
          (let [url (url params)]
            (swap! calls conj url)
            (ac/completed-future
             (if-let [contains (expansions url)]
               {:fhir/type :fhir/ValueSet
                :expansion
                {:fhir/type :fhir.ValueSet/expansion
                 :contains contains}}
               (ba/not-found (format "The value set `%s` was not found." url)))))))
      {:calls calls})))

(def ^:private config
  {:blaze/value-set-expander
   {:search-param-registry (ig/ref :blaze.db/search-param-registry)
    :terminology-service (ig/ref ::terminology-service)}
   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}
   ::terminology-service {}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/value-set-expander nil}
      :key := :blaze/value-set-expander
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze/value-set-expander {}}
      :key := :blaze/value-set-expander
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))))

  (testing "invalid search-param-registry"
    (given-failed-system (assoc-in config [:blaze/value-set-expander :search-param-registry] ::invalid)
      :key := :blaze/value-set-expander
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/search-param-registry]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid terminology-service"
    (given-failed-system (assoc-in config [:blaze/value-set-expander :terminology-service] ::invalid)
      :key := :blaze/value-set-expander
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/terminology-service]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "is a value set expander"
    (with-system [{:blaze/keys [value-set-expander]} config]
      (is (s/valid? :blaze/value-set-expander value-set-expander)))))

(defn- c1 [code] (str system-1 "|" code))

(deftest expand-clauses-test
  (with-system [{:blaze/keys [value-set-expander]} config]
    (testing "clauses without value sets stay the same"
      (let [clauses [["code" (c1 "C73")] [:sort "_lastUpdated" :desc]]]
        (is (= clauses @(vse/expand-clauses value-set-expander "Condition" clauses)))))

    (testing "a value set becomes a plain token clause with sorted codes"
      (is (= [["code" (c1 "C69.4") (c1 "C71.4")]]
             @(vse/expand-clauses value-set-expander "Condition" [["code:in" "url-1"]]))))

    (testing "the codes of multiple value sets are combined"
      (is (= [["code" (c1 "C69.4") (c1 "C71.4") (c1 "C73")]]
             @(vse/expand-clauses value-set-expander "Condition" [["code:in" "url-1" "url-2"]]))))

    (testing "codes without system"
      (is (= [["code" "|foo"]]
             @(vse/expand-clauses value-set-expander "Condition" [["code:in" "url-without-system"]]))))

    (testing "other clauses are kept"
      (is (= [[:sort "_lastUpdated" :desc]
              ["clinical-status" "active"]
              ["code" (str system-2 "|254637007")]]
             @(vse/expand-clauses
               value-set-expander "Condition"
               [[:sort "_lastUpdated" :desc]
                ["clinical-status" "active"]
                ["code:in" "url-3"]]))))

    (testing "chained search params"
      (is (= [["subject:Patient.gender" (str system-2 "|254637007")]]
             @(vse/expand-clauses value-set-expander "Observation" [["subject:Patient.gender:in" "url-3"]]))))

    (testing "system-level search params"
      (is (= [["_tag" (str system-2 "|254637007")]]
             @(vse/expand-clauses value-set-expander "Resource" [["_tag:in" "url-3"]]))))

    (testing "disjunctions"
      (is (= [[["code" (c1 "C69.4") (c1 "C71.4")] ["clinical-status" "active"]]]
             @(vse/expand-clauses
               value-set-expander "Condition"
               [[["code:in" "url-1"] ["clinical-status" "active"]]]))))

    (testing "the in modifier on other search params stays"
      (doseq [clause [["name:in" "url-1"]
                      ["identifier:in" "url-1"]
                      ["subject:in" "url-1"]
                      ["foo:in" "url-1"]
                      ["_has:Observation:patient:code:in" "url-1"]]]
        (is (= [clause] @(vse/expand-clauses value-set-expander "Patient" [clause])))))

    (testing "a value set clause without values stays"
      (is (= [["code:in"]]
             @(st/with-instrument-disabled
                (vse/expand-clauses value-set-expander "Condition" [["code:in"]])))))))

(deftest expand-clauses-empty-expansion-test
  (with-system [{:blaze/keys [value-set-expander]} config]
    (testing "a query with an empty value set can't match anything"
      (is (nil? @(vse/expand-clauses value-set-expander "Condition" [["code:in" "url-empty"]])))
      (is (nil? @(vse/expand-clauses value-set-expander "Condition" [["clinical-status" "active"]
                                                                     ["code:in" "url-empty"]]))))

    (testing "an empty value set contributes no codes"
      (is (= [["code" (c1 "C69.4") (c1 "C71.4")]]
             @(vse/expand-clauses value-set-expander "Condition" [["code:in" "url-empty" "url-1"]]))))

    (testing "an empty value set is dropped from a disjunction"
      (is (= [[["clinical-status" "active"]]]
             @(vse/expand-clauses
               value-set-expander "Condition"
               [[["code:in" "url-empty"] ["clinical-status" "active"]]]))))

    (testing "a disjunction of empty value sets can't match anything"
      (is (nil? @(vse/expand-clauses
                  value-set-expander "Condition"
                  [[["code:in" "url-empty"] ["category:in" "url-empty"]]]))))))

(deftest expand-condition-clauses-test
  (with-system [{:blaze/keys [value-set-expander]} config]
    (testing "clauses without value sets stay the same"
      (let [clauses [["code" (c1 "C73")]]]
        (is (= clauses @(vse/expand-condition-clauses value-set-expander "Condition" clauses)))))

    (testing "a value set becomes a plain token clause with sorted codes"
      (is (= [["clinical-status" "active"] ["code" (c1 "C69.4") (c1 "C71.4")]]
             @(vse/expand-condition-clauses
               value-set-expander "Condition"
               [["clinical-status" "active"] ["code:in" "url-1"]]))))

    (testing "an empty value set contributes no codes"
      (is (= [["code" (c1 "C69.4") (c1 "C71.4")]]
             @(vse/expand-condition-clauses value-set-expander "Condition" [["code:in" "url-empty" "url-1"]]))))

    (testing "a condition with an empty value set fails"
      (given-failed-future (vse/expand-condition-clauses
                            value-set-expander "Condition"
                            [["clinical-status" "active"] ["code:in" "url-empty"]])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error in search parameter `code:in`: The ValueSet `url-empty` expands to no codes. A condition with such a value set can't match any resource."
        :http/status := 422))

    (testing "a condition with multiple empty value sets fails"
      (given-failed-future (vse/expand-condition-clauses
                            value-set-expander "Condition"
                            [["code:in" "url-empty" "url-empty-2"]])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error in search parameter `code:in`: The ValueSets `url-empty`, `url-empty-2` expand to no codes. A condition with such value sets can't match any resource."
        :http/status := 422))

    (testing "a value set that can't be expanded fails"
      (given-failed-future (vse/expand-condition-clauses value-set-expander "Condition" [["code:in" "url-unknown"]])
        ::anom/category := ::anom/fault
        ::anom/message := "Error in search parameter `code:in`: Error while expanding the ValueSet `url-unknown`. Cause: The value set `url-unknown` was not found."))))

(deftest expand-condition-clauses-too-costly-test
  (with-system [{:blaze/keys [value-set-expander]} config]
    (testing "a condition can expand to 10,000 codes"
      (given @(vse/expand-condition-clauses value-set-expander "Condition" [["code:in" "url-10000"]])
        count := 1
        [0 count] := 10001))

    (testing "a condition with more than 10,000 codes is too costly"
      (given-failed-future (vse/expand-condition-clauses value-set-expander "Condition" [["code:in" "url-10001"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "The value sets of the condition expand to 10,001 codes, which is more than the limit of 10,000 codes."
        :fhir/issue := "too-costly"))

    (testing "the codes of all clauses count"
      (given-failed-future (vse/expand-condition-clauses
                            value-set-expander "Condition"
                            [["code:in" "url-5000-a"] ["category:in" "url-5001-b"]])
        ::anom/category := ::anom/conflict
        ::anom/message := "The value sets of the condition expand to 10,001 codes, which is more than the limit of 10,000 codes."
        :fhir/issue := "too-costly"))))

(deftest expand-clauses-error-test
  (with-system [{:blaze/keys [value-set-expander]} config]
    (given-failed-future (vse/expand-clauses value-set-expander "Condition" [["code:in" "url-1" "url-unknown"]])
      ::anom/category := ::anom/fault
      ::anom/message := "Error in search parameter `code:in`: Error while expanding the ValueSet `url-unknown`. Cause: The value set `url-unknown` was not found.")))

(deftest expand-clauses-cache-test
  (with-system [{:blaze/keys [value-set-expander] ::keys [terminology-service]} config]
    @(vse/expand-clauses value-set-expander "Condition" [["code:in" "url-1"]])
    @(vse/expand-clauses value-set-expander "Observation" [["code:in" "url-1"]])

    (testing "the value set was expanded only once"
      (is (= ["url-1"] @(:calls (meta terminology-service)))))

    (testing "failed expansions are not cached"
      (doseq [_ (range 2)]
        (is (ba/anomaly? (ba/try-anomaly @(vse/expand-clauses value-set-expander "Condition" [["code:in" "url-unknown"]])))))
      (is (= ["url-1" "url-unknown" "url-unknown"] @(:calls (meta terminology-service)))))))

(deftest original-clauses-test
  (with-system [{:blaze/keys [value-set-expander]} config]
    (let [clauses [["clinical-status" "active"]
                   ["code:in" "url-1"]
                   [["category" "foo"] ["code:in" "url-2"]]]
          expanded @(vse/expand-clauses value-set-expander "Condition" clauses)]

      (testing "the expanded clauses map back to the value set clauses"
        (is (= clauses (vse/original-clauses expanded expanded))))

      (testing "clauses decoded from a query map back too"
        (is (= [["code:in" "url-1"]]
               (vse/original-clauses expanded [["code" (c1 "C69.4") (c1 "C71.4")]]))))

      (testing "clauses without expansion stay the same"
        (let [clauses [["clinical-status" "active"]]]
          (is (= clauses (vse/original-clauses clauses clauses)))))))

  (testing "expanded?"
    (with-system [{:blaze/keys [value-set-expander]} config]
      (is (true? (vse/expanded? @(vse/expand-clauses value-set-expander "Condition" [["code:in" "url-1"]]))))
      (is (true? (vse/expanded? @(vse/expand-clauses value-set-expander "Condition" [[["code:in" "url-1"] ["category" "foo"]]]))))
      (is (false? (vse/expanded? @(vse/expand-clauses value-set-expander "Condition" [["code" "foo"]])))))))
