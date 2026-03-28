(ns blaze.db.impl.search-param.near-test
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string-spec]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.near :as near]
   [blaze.db.impl.search-param.near.spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.alpha.spec :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
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
    :terminology-service (ig/ref ::ts/not-available)}
   ::ts/not-available {}})

(defn- near-param [search-param-registry]
  (sr/get search-param-registry "near" "Location"))

(deftest near-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]

    (given (near-param search-param-registry)
      :name := "near"
      :code := "near")))

(deftest validate-modifier-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "unknown modifier"
      (given (search-param/validate-modifier
              (near-param search-param-registry) "unknown")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unknown modifier `unknown` on search parameter `near`."))))

(defn- compile-near-value [search-param-registry value]
  (when-ok [param (-> (near-param search-param-registry)
                      (search-param/compile-values nil [value]))]
    (first param)))

(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "With unit km"
      (given (compile-near-value search-param-registry "-83.694810|42.256500|11.20|km")
        :latitude := -83.69481M
        :longitude := 42.2565M
        :distance := 11200M))

    (testing "random input"
      (satisfies-prop 1000
        (prop/for-all [s gen/string]
          (let [val (compile-near-value search-param-registry s)]
            (or (ba/anomaly? val)
                (s/valid? ::near/compiled-value val))))))

    (testing "With unit m"
      (given (compile-near-value search-param-registry "-83.694810|42.256500|11200|m")
        :latitude := -83.69481M
        :longitude := 42.2565M
        :distance := 11200M))

    (testing "Without unit"
      (given (compile-near-value search-param-registry "-83.694810|42.256500|5")
        :latitude := -83.69481M
        :longitude := 42.2565M
        :distance := 5000M))

    (testing "Without unit and distance"
      (given (compile-near-value search-param-registry "-83.694810|42.256500")
        :latitude := -83.69481M
        :longitude := 42.2565M
        :distance := 1000M)
      (given (compile-near-value search-param-registry "0|0|||")
        :latitude := 0M
        :longitude := 0M
        :distance := 1000M)
      (given (compile-near-value search-param-registry "0|0||mi_us") ; works as missing dist ignores unit
        :latitude := 0M
        :longitude := 0M
        :distance := 1000M))

    (testing "With invalid latitude"
      (given (compile-near-value search-param-registry "")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing argument `latitude` in search parameter `near`.")
      (given (compile-near-value search-param-registry "invalid")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error parsing argument `latitude` in search parameter `near`. Invalid decimal value `invalid`.")
      (given (compile-near-value search-param-registry "90.1|0")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid argument `90.1` for latitude in search parameter `near`, must be between -90.0 and 90.0."))

    (testing "With invalid longitude"
      (given (compile-near-value search-param-registry "0")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing argument `longitude` in search parameter `near`.")
      (given (compile-near-value search-param-registry "0|")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing argument `longitude` in search parameter `near`.")
      (given (compile-near-value search-param-registry "0|invalid")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error parsing argument `longitude` in search parameter `near`. Invalid decimal value `invalid`.")
      (given (compile-near-value search-param-registry "0|180.1")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid argument `180.1` for longitude in search parameter `near`, must be between -180.0 and 180.0."))

    (testing "With invalid distance"
      (given (compile-near-value search-param-registry "0|0|a")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error parsing argument `distance` in search parameter `near`. Invalid decimal value `a`."))

    (testing "With invalid unit"
      (given (compile-near-value search-param-registry "0|0|1||")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unsupported unit `|` in search parameter `near`. Supported are 'km', 'm'.")
      (given (compile-near-value search-param-registry "0|0|1|mi_us")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unsupported unit `mi_us` in search parameter `near`. Supported are 'km', 'm'."))))

(deftest ordered-compartment-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (near-param search-param-registry)]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil nil))))))

(defn- index-entries [search-param linked-compartments hash resource]
  (vec (search-param/index-entries search-param linked-compartments hash resource)))

(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [location {:fhir/type :fhir/Location :id "id-140855"}
          hash (hash/generate location)]
      (is (empty? (index-entries
                   (near-param search-param-registry) [] hash location))))))
