(ns blaze.interaction.history.util-test
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.interaction.history.util :as history-util]
   [blaze.interaction.history.util-spec]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit])
  (:import
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest page-t-test
  (testing "no query param"
    (is (nil? (history-util/page-t {}))))

  (testing "invalid query param"
    (are [t] (nil? (history-util/page-t {"__page-t" t}))
      "<invalid>"
      "-1"
      ""))

  (testing "valid query param"
    (are [v t] (= t (history-util/page-t {"__page-t" v}))
      "1" 1
      ["<invalid>" "2"] 2
      ["3" "4"] 3)))

(def base-url "base-url-183028")
(def context-path "/context-path-183031")

(def router
  (reitit/router
   [["/Patient" {:name :Patient/type}]
    ["/Patient/{id}" {:name :Patient/instance}]]
   {:syntax :bracket
    :path context-path}))

(def context
  {:blaze/base-url base-url
   ::reitit/router router})

(deftest build-entry-test
  (testing "Initial version with server assigned id"
    (given (history-util/build-entry
            context
            (with-meta
              {:fhir/type :fhir/Patient
               :id "0"
               :meta #fhir/Meta{:versionId #fhir/id "1"}}
              {:blaze.db/op :create
               :blaze.db/num-changes 1
               :blaze.db/tx {:blaze.db/t 1 :blaze.db.tx/instant Instant/EPOCH}}))
      :fullUrl := (type/uri (str base-url context-path "/Patient/0"))
      [:request :method] := #fhir/code "POST"
      [:request :url] := #fhir/uri "Patient"
      [:resource :fhir/type] := :fhir/Patient
      [:resource :id] := "0"
      [:response :status] := #fhir/string "201"
      [:response :lastModified] := Instant/EPOCH
      [:response :etag] := #fhir/string "W/\"1\""))

  (testing "Initial version with client assigned id"
    (given (history-util/build-entry
            context
            (with-meta
              {:fhir/type :fhir/Patient
               :id "0"
               :meta #fhir/Meta{:versionId #fhir/id "1"}}
              {:blaze.db/op :put
               :blaze.db/num-changes 1
               :blaze.db/tx {:blaze.db/t 1 :blaze.db.tx/instant Instant/EPOCH}}))
      :fullUrl := (type/uri (str base-url context-path "/Patient/0"))
      [:request :method] := #fhir/code "PUT"
      [:request :url] := #fhir/uri "Patient/0"
      [:resource :fhir/type] := :fhir/Patient
      [:resource :id] := "0"
      [:response :status] := #fhir/string "201"
      [:response :lastModified] := Instant/EPOCH
      [:response :etag] := #fhir/string "W/\"1\""))

  (testing "Non-initial version"
    (given (history-util/build-entry
            context
            (with-meta
              {:fhir/type :fhir/Patient
               :id "0"
               :meta #fhir/Meta{:versionId #fhir/id "2"}}
              {:blaze.db/op :put
               :blaze.db/num-changes 2
               :blaze.db/tx {:blaze.db/t 1 :blaze.db.tx/instant Instant/EPOCH}}))
      :fullUrl := (type/uri (str base-url context-path "/Patient/0"))
      [:request :method] := #fhir/code "PUT"
      [:request :url] := #fhir/uri "Patient/0"
      [:resource :fhir/type] := :fhir/Patient
      [:resource :id] := "0"
      [:response :status] := #fhir/string "200"
      [:response :lastModified] := Instant/EPOCH
      [:response :etag] := #fhir/string "W/\"2\""))

  (testing "Deleted version"
    (given (history-util/build-entry
            context
            (with-meta
              {:fhir/type :fhir/Patient
               :id "0"
               :meta #fhir/Meta{:versionId #fhir/id "2"}}
              {:blaze.db/op :delete
               :blaze.db/num-changes 2
               :blaze.db/tx {:blaze.db/t 1 :blaze.db.tx/instant Instant/EPOCH}}))
      :fullUrl := (type/uri (str base-url context-path "/Patient/0"))
      [:request :method] := #fhir/code "DELETE"
      [:request :url] := #fhir/uri "Patient/0"
      [:response :status] := #fhir/string "204"
      [:response :lastModified] := Instant/EPOCH
      [:response :etag] := #fhir/string "W/\"2\"")))

(def ^:private config
  {::context
   {::search-util/link (ig/ref ::search-util/link)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :blaze/base-url ""
    :reitit.core/match (reitit/map->Match {})}
   ::search-util/link {:fhir/version "4.0.1"}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}})

(defmethod ig/init-key ::context [_ context] context)

(deftest build-bundle-test
  (testing "total"
    (testing "minimum allowed FHIR unsignedInt value"
      (let [min-int 0]
        (with-system [{::keys [context]} config]
          (given (history-util/build-bundle context min-int {})
            [:total type/value] := min-int))))

    (testing "maximum allowed FHIR unsignedInt value"
      (let [max-int (dec (bit-shift-left 1 31))]
        (with-system [{::keys [context]} config]
          (given (history-util/build-bundle context max-int {})
            [:total type/value] := max-int))))

    (testing "one above the maximum allowed FHIR unsignedInt value"
      (let [overflowed-int (bit-shift-left 1 31)]
        (with-system [{::keys [context]} config]
          (given (history-util/build-bundle context overflowed-int {})
            [:total type/value] := nil
            [:total :extension 0 :url] := "https://samply.github.io/blaze/fhir/StructureDefinition/grand-total"
            [:total :extension 0 :value] := (type/string (str overflowed-int))))))

    (testing "values of a trillion are possible"
      (let [trillion-int 1000000000000]
        (with-system [{::keys [context]} config]
          (given (history-util/build-bundle context trillion-int {})
            [:total type/value] := nil
            [:total :extension 0 :url] := "https://samply.github.io/blaze/fhir/StructureDefinition/grand-total"
            [:total :extension 0 :value] := (type/string (str trillion-int))))))))
