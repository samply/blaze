(ns blaze.interaction.search.util-test
  (:require
   [blaze.fhir.test-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util-spec]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
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
    :fullUrl := "/Patient/0"
    [:resource :fhir/type] := :fhir/Patient
    [:resource :id] := "0"
    [:search :mode] #fhir/code"match"))

(deftest include-entry-test
  (given-thrown (search-util/include-entry {} {:fhir/type :fhir/Patient :id "0"})
    [::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :blaze/base-url))
    [::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% ::reitit/router)))

  (given-thrown (search-util/include-entry context {})
    [::s/problems 0 :path] := [:resource]
    [::s/problems 0 :via] := [:fhir/Resource :fhir/Resource])

  (given (search-util/include-entry context {:fhir/type :fhir/Patient :id "0"})
    :fhir/type := :fhir.Bundle/entry
    :fullUrl := "/Patient/0"
    [:resource :fhir/type] := :fhir/Patient
    [:resource :id] := "0"
    [:search :mode] #fhir/code"include"))

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
    [:search :mode] #fhir/code"outcome"))
