(ns blaze.db.impl.search-param.near-test
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.byte-string-spec]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.near]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

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

(defn compile-near-value [search-param-registry value]
  (when-ok
   [param (-> (near-param search-param-registry)
              (search-param/compile-values nil [value]))]
    (first param)))

(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "With unit km"
      (given (compile-near-value search-param-registry "-83.694810|42.256500|11.20|km")
        :latitude := -83.69481M
        :longitude := 42.2565M
        :distance := 11200M))

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
        :distance := 1000M))

    (testing "With invalid unit"
      (is (ba/incorrect? (compile-near-value search-param-registry "-83.694810|42.256500|11.20|[mi_us]"))))))

(deftest estimated-scan-size-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (near-param search-param-registry)]
      (is (ba/unsupported? (p/-estimated-scan-size search-param nil nil nil nil))))))

(deftest ordered-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (near-param search-param-registry)]
      (is (false? (p/-supports-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil nil))))))

(deftest ordered-compartment-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (near-param search-param-registry)]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil))))))

(defn- index-entries [search-param linked-compartments hash resource]
  (vec (search-param/index-entries search-param linked-compartments hash resource)))

(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "position"
      (let [location {:fhir/type :fhir/Location
                      :id "id-134198"
                      :position
                      #fhir/Position{:latitude 52.5200 :longitude 13.4050}}
            hash (hash/generate location)
            [[_ k0]]
            (index-entries
             (near-param search-param-registry) [] hash location)]

        (testing "..."
          k0))))
  )

(comment
 ; Some test data TODO: Remove
 ;; Two locations in Berlin, very close to each other
 (def loc-1 {:latitude 52.5200 :longitude 13.4050})
 (def loc-2 {:latitude 52.5201 :longitude 13.4051})
 (near? loc-1 loc-2 5 "m")                                  ; false
 (near? loc-1 loc-2 200 "m")                                ; true
 (near? loc-1 loc-2 0.1 "km")                               ; true
 (haversine-distance loc-1 loc-2)                           ; ~13.02m

 ;; Two locations in Germany
 (def berlin {:latitude 52.5200 :longitude 13.4050})
 (def leipzig {:latitude 51.3397 :longitude 12.3731})
 (near? berlin leipzig 140 "km")                            ; false
 (near? berlin leipzig 150 "km")                            ; true
 (near? berlin leipzig 140000 "m")                          ; true
 (near? berlin leipzig 150000 "m")                          ; true
 (haversine-distance berlin leipzig)                        ; ~149km
 )
