(ns blaze.db.impl.search-param.composite.token-date-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.byte-string-spec]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.codec.date :as codec-date]
   [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
   [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.composite.token-date]
   [blaze.db.impl.search-param.composite.token-date-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref ::ts/not-available)
    :extra-bundle-file "test-resources/custom-search-parameters.json"}
   ::ts/not-available {}})

(defn- code-period-param [search-param-registry]
  (sr/get search-param-registry "provision-provision-code-period" "Consent"))

(defn- compile-value [search-param-registry value]
  (-> (code-period-param search-param-registry)
      (search-param/compile-values nil [value])
      (ac/join)
      (first)))

(deftest code-period-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (given (code-period-param search-param-registry)
      :name := "ConsentProvisionProvisionCodePeriod"
      :code := "provision-provision-code-period"
      :c-hash := (codec/c-hash "provision-provision-code-period"))))

(deftest estimated-scan-size-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (code-period-param search-param-registry)]
      (is (ba/unsupported? (p/-estimated-scan-size search-param nil nil nil nil))))))

(deftest ordered-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (code-period-param search-param-registry)]
      (is (false? (p/-supports-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil nil))))))

(deftest ordered-compartment-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (code-period-param search-param-registry)]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil nil))))))

(deftest validate-modifier-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "unknown modifier"
      (given (search-param/validate-modifier
              (code-period-param search-param-registry) "unknown")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unknown modifier `unknown` on search parameter `provision-provision-code-period`."))))

(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "eq"
      (given (compile-value search-param-registry "code-185723$2020")
        :op := :eq
        :token := (codec/v-hash "code-185723")
        :lower-bound := (codec-date/encode-lower-bound #system/date "2020")
        :upper-bound := (codec-date/encode-upper-bound #system/date "2020")))

    (testing "ge"
      (given (compile-value search-param-registry "system-185733|code-185723$ge2020-02")
        :op := :ge
        :token := (codec/v-hash "system-185733|code-185723")
        :lower-bound := (codec-date/encode-lower-bound #system/date "2020-02")
        :upper-bound := (codec-date/encode-upper-bound #system/date "2020-02")))

    (testing "sa"
      (given (compile-value search-param-registry "code-185723$sa2020")
        :op := :sa
        :token := (codec/v-hash "code-185723")
        :upper-bound := (codec-date/encode-upper-bound #system/date "2020")))

    (testing "missing second value part"
      (given-failed-future (search-param/compile-values
                            (code-period-param search-param-registry) nil ["code-185723"])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error in search parameter `provision-provision-code-period`: Miss the second part is composite search value `code-185723`."))

    (testing "invalid date value"
      (given-failed-future (search-param/compile-values
                            (code-period-param search-param-registry) nil ["code-185723$a"])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error in search parameter `provision-provision-code-period`: Invalid date-time value `a`."))))

(defn- split-value [bs]
  [(bs/subs bs 0 codec/v-hash-size) (bs/subs bs codec/v-hash-size)])

(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [consent
          {:fhir/type :fhir/Consent :id "id-185747"
           :provision
           {:fhir/type :fhir.Consent/provision
            :provision
            [{:fhir/type :fhir.Consent/provision
              :period
              #fhir/Period
               {:start #fhir/dateTime #system/date-time "2020-01-01"
                :end #fhir/dateTime #system/date-time "2020-12-31"}
              :code
              [#fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri "system-185733"
                    :code #fhir/code "code-185723"}]}]}]}}
          hash (hash/generate consent)
          date-range (codec-date/encode-range
                      (system/parse-date-time "2020-01-01")
                      (system/parse-date-time "2020-12-31"))
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] :as entries]
          (vec (search-param/index-entries
                (code-period-param search-param-registry)
                [] hash consent))]

      (is (= 6 (count entries)))

      (testing "`code` followed by `period`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :type := "Consent"
            [:v-hash split-value 0] := (codec/v-hash "code-185723")
            [:v-hash split-value 1] := date-range
            :id := "id-185747"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Consent"
            :id := "id-185747"
            :hash-prefix := (hash/prefix hash)
            [:v-hash split-value 0] := (codec/v-hash "code-185723")
            [:v-hash split-value 1] := date-range)))

      (testing "`system|` followed by `period`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k2))
          [:v-hash split-value 0] := (codec/v-hash "system-185733|")
          [:v-hash split-value 1] := date-range)
        (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
          [:v-hash split-value 0] := (codec/v-hash "system-185733|")
          [:v-hash split-value 1] := date-range))

      (testing "`system|code` followed by `period`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k4))
          [:v-hash split-value 0] := (codec/v-hash "system-185733|code-185723")
          [:v-hash split-value 1] := date-range)
        (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
          [:v-hash split-value 0] := (codec/v-hash "system-185733|code-185723")
          [:v-hash split-value 1] := date-range)))))
