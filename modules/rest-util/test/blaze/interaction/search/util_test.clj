(ns blaze.interaction.search.util-test
  (:require
   [blaze.db.search-param :as-alias sp]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.spec]
   [blaze.test-util :as tu :refer [given-thrown satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def router
  (reitit/router
   [["/Patient" {:name :Patient/type}]]
   {:syntax :bracket
    :path ""}))

(def context
  {:blaze/base-url ""
   ::reitit/router router})

(deftest match-entry-test
  (given-thrown (search-util/match-entry {} {:fhir/type :fhir/Patient :id "0"})
    [::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :blaze/base-url))
    [::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% ::reitit/router)))

  (given-thrown (search-util/match-entry context {})
    [::s/problems 0 :path] := [:resource]
    [::s/problems 0 :via] := [:fhir/Resource :fhir/Resource])

  (given (search-util/match-entry context {:fhir/type :fhir/Patient :id "0"})
    :fhir/type := :fhir.Bundle/entry
    :fullUrl := #fhir/uri "/Patient/0"
    [:resource :fhir/type] := :fhir/Patient
    [:resource :id] := "0"
    [:search :mode] := #fhir/code "match"
    [:search :extension] :? nil?)

  (let [resource (with-meta {:fhir/type :fhir/Patient :id "0"}
                            {::sp/match-extension [:extension-100623]})]
    (given (search-util/match-entry context resource)
      :fhir/type := :fhir.Bundle/entry
      :fullUrl := #fhir/uri "/Patient/0"
      [:resource :fhir/type] := :fhir/Patient
      [:resource :id] := "0"
      [:search :mode] := #fhir/code "match"
      [:search :extension] := [:extension-100623])))

(deftest include-entry-test
  (given-thrown (search-util/include-entry {} {:fhir/type :fhir/Patient :id "0"})
    [::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :blaze/base-url))
    [::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% ::reitit/router)))

  (given-thrown (search-util/include-entry context {})
    [::s/problems 0 :path] := [:resource]
    [::s/problems 0 :via] := [:fhir/Resource :fhir/Resource])

  (given (search-util/include-entry context {:fhir/type :fhir/Patient :id "0"})
    :fhir/type := :fhir.Bundle/entry
    :fullUrl := #fhir/uri "/Patient/0"
    [:resource :fhir/type] := :fhir/Patient
    [:resource :id] := "0"
    [:search :mode] #fhir/code "include"))

(deftest outcome-entry-test
  (given-thrown (search-util/outcome-entry {} {:fhir/type :fhir/Patient :id "0"})
    [::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :blaze/base-url))
    [::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% ::reitit/router)))

  (given-thrown (search-util/outcome-entry context {})
    [::s/problems 0 :path] := [:resource]
    [::s/problems 0 :via] := [:fhir/OperationOutcome :fhir/OperationOutcome])

  (given-thrown (search-util/outcome-entry context {:fhir/type :fhir/Patient})
    [::s/problems 0 :path] := [:resource]
    [::s/problems 0 :via] := [:fhir/OperationOutcome :fhir/OperationOutcome])

  (given (search-util/outcome-entry context {:fhir/type :fhir/OperationOutcome})
    :fhir/type := :fhir.Bundle/entry
    [:resource :fhir/type] := :fhir/OperationOutcome
    [:search :mode] #fhir/code "outcome"))

(deftest link-test
  (testing "init"
    (testing "nil config"
      (given-failed-system {::search-util/link nil}
        :key := ::search-util/link
        :reason := ::ig/build-failed-spec
        [:cause-data ::s/problems 0 :pred] := `map?))

    (testing "missing config"
      (given-failed-system {::search-util/link {}}
        :key := ::search-util/link
        :reason := ::ig/build-failed-spec
        [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :fhir/version)))))

  (testing "FHIR v4.0.1"
    (with-system [{::search-util/keys [link]} {::search-util/link {:fhir/version "4.0.1"}}]
      (satisfies-prop 10
        (prop/for-all [relation gen/string url gen/string]
          (= (link relation url)
             {:fhir/type :fhir.Bundle/link
              :relation (type/string relation)
              :url (type/uri url)})))))

  (testing "FHIR v6.0.0-ballot3"
    (with-system [{::search-util/keys [link]} {::search-util/link {:fhir/version "6.0.0-ballot3"}}]
      (satisfies-prop 10
        (prop/for-all [relation gen/string url gen/string]
          (= (link relation url)
             {:fhir/type :fhir.Bundle/link
              :relation (type/code relation)
              :url (type/uri url)}))))))
