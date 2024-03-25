(ns blaze.interaction.search-type-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-store :as rs]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [link-url]]
   [blaze.interaction.search-type]
   [blaze.interaction.search.nav-spec]
   [blaze.interaction.search.params-spec]
   [blaze.interaction.search.util-spec]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.page-store-spec]
   [blaze.page-store.local]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-url "base-url-113047")
(def context-path "/context-path-173858")

(def router
  (reitit/router
   [["/Patient" {:name :Patient/type}]
    ["/Patient/__page" {:name :Patient/page}]
    ["/MeasureReport" {:name :MeasureReport/type}]
    ["/MeasureReport/__page" {:name :MeasureReport/page}]
    ["/Library" {:name :Library/type}]
    ["/Library/__page" {:name :Library/page}]
    ["/List" {:name :List/type}]
    ["/List/__page" {:name :List/page}]
    ["/Condition" {:name :Condition/type}]
    ["/Condition/__page" {:name :Condition/page}]
    ["/Observation" {:name :Observation/type}]
    ["/Observation/__page" {:name :Observation/page}]
    ["/MedicationStatement" {:name :MedicationStatement/type}]
    ["/MedicationStatement/__page" {:name :MedicationStatement/page}]
    ["/Medication" {:name :Medication/type}]
    ["/Organization" {:name :Organization/type}]
    ["/Encounter" {:name :Encounter/type}]
    ["/Encounter/__page" {:name :Encounter/page}]]
   {:syntax :bracket
    :path context-path}))

(defn match-of [type]
  (reitit/map->Match
   {:data
    {:name (keyword type "type")
     :fhir.resource/type type}
    :path (str context-path "/" type)}))

(def patient-search-match
  (reitit/map->Match
   {:data
    {:name :Patient/search
     :fhir.resource/type "Patient"}
    :path (str context-path "/Patient")}))

(def patient-page-match
  (reitit/map->Match
   {:data
    {:name :Patient/page
     :fhir.resource/type "Patient"}
    :path (str context-path "/Patient")}))

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/search-type nil})
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/search-type {}})
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :page-store))))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze.interaction/search-type {:clock ::invalid}})
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :page-store))
      [:explain ::s/problems 2 :pred] := `time/clock?
      [:explain ::s/problems 2 :val] := ::invalid)))

(def config
  (assoc api-stub/mem-node-config
         :blaze.interaction/search-type
         {:clock (ig/ref :blaze.test/fixed-clock)
          :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
          :page-store (ig/ref :blaze.page-store/local)}
         :blaze.test/fixed-rng-fn {}
         :blaze.page-store/local {:secure-rng (ig/ref :blaze.test/fixed-rng)}
         :blaze.test/fixed-rng {}))

(defn wrap-defaults [handler]
  (fn [{::reitit/keys [match] :as request}]
    (handler
     (cond-> (assoc request
                    :blaze/base-url base-url
                    ::reitit/router router)
       (nil? match)
       (assoc ::reitit/match (match-of "Patient"))))))

(defn wrap-db [handler node]
  (fn [{::reitit/keys [match] :as request}]
    (if (= patient-page-match match)
      ((db/wrap-snapshot-db handler node 100) request)
      ((db/wrap-db handler node 100) request))))

(defmacro with-handler [[handler-binding & [node-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.interaction/search-type} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node#)
                                  wrap-error)
             ~(or node-binding '_) node#]
         ~@body))))

(deftest handler-test
  (testing "on unknown search parameter"
    (testing "with strict handling"
      (testing "returns error"
        (with-handler [handler]
          (testing "normal result"
            (let [{:keys [status body]}
                  @(handler
                    {:headers {"prefer" "handling=strict"}
                     :params {"foo" "bar"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"not-found"
                [:issue 0 :diagnostics] := "The search-param with code `foo` and type `Patient` was not found.")))

          (testing "summary result"
            (let [{:keys [status body]}
                  @(handler
                    {:headers {"prefer" "handling=strict"}
                     :params {"foo" "bar" "_summary" "count"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"not-found"
                [:issue 0 :diagnostics] := "The search-param with code `foo` and type `Patient` was not found."))))))

    (testing "with lenient handling"
      (testing "returns results with a self link lacking the unknown search parameter"
        (testing "where the unknown search parameter is the only one"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

            (testing "normal result"
              (let [{:keys [status body]}
                    @(handler
                      {:headers {"prefer" "handling=lenient"}
                       :params {"foo" "bar"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle id is an LUID"
                  (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?_count=50")
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    @(handler
                      {:headers {"prefer" "handling=lenient"}
                       :params {"foo" "bar" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle id is an LUID"
                  (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?_summary=count&_count=50")
                         (link-url body "self"))))))))

        (testing "with another search parameter"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Patient :id "1"
                     :active true}]]]

            (testing "normal result"
              (let [{:keys [status body]}
                    @(handler
                      {:headers {"prefer" "handling=lenient"}
                       :params {"foo" "bar" "active" "true"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle id is an LUID"
                  (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?active=true&_count=50")
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    @(handler
                      {:headers {"prefer" "handling=lenient"}
                       :params {"foo" "bar" "active" "true" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle id is an LUID"
                  (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?active=true&_summary=count&_count=50")
                         (link-url body "self"))))))))))

    (testing "with default handling"
      (testing "returns results with a self link lacking the unknown search parameter"
        (testing "where the unknown search parameter is the only one"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

            (testing "normal result"
              (let [{:keys [status body]}
                    @(handler
                      {:params {"foo" "bar"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle id is an LUID"
                  (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?_count=50")
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    @(handler
                      {:params {"foo" "bar" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle id is an LUID"
                  (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?_summary=count&_count=50")
                         (link-url body "self"))))))))

        (testing "with another search parameter"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Patient :id "1"
                     :active true}]]]

            (testing "normal result"
              (let [{:keys [status body]}
                    @(handler
                      {:params {"foo" "bar" "active" "true"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle id is an LUID"
                  (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?active=true&_count=50")
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    @(handler
                      {:params {"foo" "bar" "active" "true" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle id is an LUID"
                  (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?active=true&_summary=count&_count=50")
                         (link-url body "self")))))))))))

  (testing "on unsupported second sort parameter"
    (testing "returns error"
      (with-handler [handler]
        (testing "normal result"
          (let [{:keys [status body]}
                @(handler
                  {:params {"_sort" "a,b"}})]

            (is (= 422 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-supported"
              [:issue 0 :diagnostics] := "More than one sort parameter is unsupported.")))

        (testing "summary result"
          (let [{:keys [status body]}
                @(handler
                  {:params {"_sort" "a,b" "_summary" "count"}})]

            (is (= 422 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-supported"
              [:issue 0 :diagnostics] := "More than one sort parameter is unsupported."))))))

  (testing "on invalid date-time"
    (testing "returns error"
      (with-handler [handler]
        (testing "normal result"
          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   ;; the date is already URl decoded and so contains a space instead of a plus
                   :params {"date" "2021-12-09T00:00:00 01:00"}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"invalid"
              [:issue 0 :diagnostics] := "Invalid date-time value `2021-12-09T00:00:00 01:00` in search parameter `date`.")))

        (testing "summary result"
          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   ;; the date is already URl decoded and so contains a space instead of a plus
                   :params {"date" "2021-12-09T00:00:00 01:00" "_summary" "count"}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"invalid"
              [:issue 0 :diagnostics] := "Invalid date-time value `2021-12-09T00:00:00 01:00` in search parameter `date`."))))))

  (testing "on invalid token"
    (testing "returns error"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {::reitit/match patient-page-match
                 :params {"__t" "0" "__token" "invalid-token-175424"}})]

          (is (= 422 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Invalid token `invalid-token-175424`.")))))

  (testing "on missing token"
    (testing "returns error"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {::reitit/match patient-page-match
                 :params {"__t" "0" "__token" (str/join (repeat 32 "A"))}})]

          (is (= 404 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"not-found"
            [:issue 0 :diagnostics] := (format "Clauses of token `%s` not found."
                                               (str/join (repeat 32 "A"))))))))

  (testing "with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "Returns all existing resources of type"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler {})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_count=50")
                   (link-url body "self"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id"1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the entry has the right search mode"
            (given (:search first-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code"match"))))

      (testing "with param _summary equal to count"
        (let [{:keys [status body]}
              @(handler
                {:params {"_summary" "count"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_summary=count&_count=50")
                   (link-url body "self"))))

          (testing "the bundle contains no entry"
            (is (empty? (:entry body))))))

      (testing "with param _count equal to zero"
        (let [{:keys [status body]}
              @(handler
                {:params {"_count" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_count=0")
                   (link-url body "self"))))

          (testing "the bundle contains no entry"
            (is (empty? (:entry body))))))))

  (testing "with two patients"
    (with-handler [handler node]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "search for all patients with _count=1"
        (let [{:keys [body]}
              @(handler
                {:params {"_count" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_count=1")
                   (link-url body "self"))))

          (testing "has a first link"
            (is (= (str base-url context-path "/Patient/__page?_count=1&__t=1")
                   (link-url body "first"))))

          (testing "has a next link"
            (is (= (str base-url context-path "/Patient/__page?_count=1&__t=1&__page-id=1")
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the self link"
        (let [{:keys [body]}
              @(handler
                {:params {"_count" "1" "__t" "1" "__page-id" "0"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_count=1")
                   (link-url body "self"))))

          (testing "has a first link"
            (is (= (str base-url context-path "/Patient/__page?_count=1&__t=1")
                   (link-url body "first"))))

          (testing "has a next link"
            (is (= (str base-url context-path "/Patient/__page?_count=1&__t=1&__page-id=1")
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @(handler
                {:params {"_count" "1" "__t" "1" "__page-id" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_count=1")
                   (link-url body "self"))))

          (testing "has a first link"
            (is (= (str base-url context-path "/Patient/__page?_count=1&__t=1")
                   (link-url body "first"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "on /_search request"
        (testing "search for all patients with _count=1"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-search-match
                   :params {"_count" "1"}})]

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_count=1")
                     (link-url body "self"))))

            (testing "has a first link"
              (is (= (str base-url context-path "/Patient/__page?_count=1&__t=1")
                     (link-url body "first"))))

            (testing "has a next link"
              (is (= (str base-url context-path "/Patient/__page?_count=1&__t=1&__page-id=1")
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))

      (testing "adding a third patient doesn't influence the paging"
        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "2"}]])

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-page-match
                   :params {"_count" "1" "__t" "1" "__page-id" "1"}})]

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has no self link"
              (is (nil? (link-url body "self"))))

            (testing "has a first link"
              (is (= (str base-url context-path "/Patient/__page?_count=1&__t=1")
                     (link-url body "first"))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))))

  (testing "with three patients"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1" :active true}]
        [:put {:fhir/type :fhir/Patient :id "2" :active true}]]]

      (testing "search for active patients with _summary=count"
        (testing "with strict handling"
          (let [{:keys [body]}
                @(handler
                  {:headers {"prefer" "handling=strict"}
                   :params {"active" "true" "_summary" "count"}})]

            (testing "their is a total count because we used _summary=count"
              (is (= #fhir/unsignedInt 2 (:total body))))))

        (testing "with default handling"
          (let [{:keys [body]}
                @(handler
                  {:params {"active" "true" "_summary" "count"}})]

            (testing "their is a total count because we used _summary=count"
              (is (= #fhir/unsignedInt 2 (:total body)))))))

      (testing "on normal request"
        (testing "search for active patients with _count=1"
          (let [{:keys [body]}
                @(handler
                  {:params {"active" "true" "_count" "1"}})]

            (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
              (is (nil? (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?active=true&_count=1")
                     (link-url body "self"))))

            (testing "has a first link"
              (is (= (str base-url context-path "/Patient/__page?active=true&_count=1&__t=1")
                     (link-url body "first"))))

            (testing "has a next link with search params"
              (is (= (str base-url context-path "/Patient/__page?active=true&_count=1&__t=1&__page-id=2")
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))

        (testing "search for inactive patients"
          (let [{:keys [body]}
                @(handler
                  {:params {"active" "false"}})]

            (testing "the total is zero"
              (is (zero? (type/value (:total body)))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?active=false&_count=50")
                     (link-url body "self"))))

            (testing "has no first link"
              (is (nil? (link-url body "first"))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains no entry"
              (is (zero? (count (:entry body))))))))

      (testing "on /_search request"
        (testing "search for active patients with _count=1"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-search-match
                   :params {"active" "true" "_count" "1"}})]

            (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
              (is (nil? (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?active=true&_count=1")
                     (link-url body "self"))))

            (testing "has a first link with token"
              (is (= (str base-url context-path "/Patient/__page?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_count=1&__t=1")
                     (link-url body "first"))))

            (testing "has a next link with token"
              (is (= (str base-url context-path "/Patient/__page?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_count=1&__t=1&__page-id=2")
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))

        (testing "search for inactive patients"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-search-match
                   :params {"active" "false"}})]

            (testing "the total is zero"
              (is (zero? (type/value (:total body)))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?active=false&_count=50")
                     (link-url body "self"))))

            (testing "has no first link"
              (is (nil? (link-url body "first"))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains no entry"
              (is (zero? (count (:entry body))))))))

      (testing "following the self link"
        (let [{:keys [body]}
              @(handler
                {:params {"active" "true" "_count" "1" "__t" "1" "__page-id" "1"}})]

          (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
            (is (nil? (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?active=true&_count=1")
                   (link-url body "self"))))

          (testing "has a first link with search params"
            (is (= (str base-url context-path "/Patient/__page?active=true&_count=1&__t=1")
                   (link-url body "first"))))

          (testing "has a next link with search params"
            (is (= (str base-url context-path "/Patient/__page?active=true&_count=1&__t=1&__page-id=2")
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @(handler
                {::reitit/match patient-page-match
                 :params {"__token" "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB" "_count" "1" "__t" "1" "__page-id" "2"}})]

          (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
            (is (nil? (:total body))))

          (testing "has no self link"
            (is (nil? (link-url body "self"))))

          (testing "has a first link with token"
            (is (= (str base-url context-path "/Patient/__page?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_count=1&__t=1")
                   (link-url body "first"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))))

  (testing "with four patients"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1" :active true}]
        [:put {:fhir/type :fhir/Patient :id "2" :active true}]
        [:put {:fhir/type :fhir/Patient :id "3" :active true}]]]

      (testing "on normal request"
        (testing "search for active patients with _count=1"
          (let [{:keys [body]}
                @(handler
                  {:params {"active" "true" "_count" "1"}})]

            (testing "has a next link with search params"
              (is (= (str base-url context-path "/Patient/__page?active=true&_count=1&__t=1&__page-id=2")
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-page-match
                   :params {"active" "true" "_count" "1" "__t" "1" "__page-id" "2"}})]

            (testing "has no self link"
              (is (nil? (link-url body "self"))))

            (testing "has a next link with search params"
              (is (= (str base-url context-path "/Patient/__page?active=true&_count=1&__t=1&__page-id=3")
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))

      (testing "on /_search request"
        (testing "search for active patients with _count=1"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-search-match
                   :params {"active" "true" "_count" "1"}})]

            (testing "has a first link with token"
              (is (= (str base-url context-path "/Patient/__page?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_count=1&__t=1")
                     (link-url body "first"))))

            (testing "has a first link with token"
              (is (= (str base-url context-path "/Patient/__page?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_count=1&__t=1&__page-id=2")
                     (link-url body "next"))))))

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-page-match
                   :params {"__token" "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB" "_count" "1" "__t" "1" "__page-id" "2"}})]

            (testing "has no self link"
              (is (nil? (link-url body "self"))))

            (testing "has a next link with token"
              (is (= (str base-url context-path "/Patient/__page?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_count=1&__t=1&__page-id=3")
                     (link-url body "next")))))))))

  (testing "_id search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_id" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_id=0&_count=50")
                   (link-url body "self"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0")))))

    (testing "multiple id's"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"}]
          [:put {:fhir/type :fhir/Patient :id "2"}]]]

        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status] {[first-entry] :entry :as body} :body}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_id" "0,2"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_id=0%2C2&_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains two entries"
              (is (= 2 (count (:entry body)))))

            (testing "the first entry has the right fullUrl"
              (is (= (str base-url context-path "/Patient/0")
                     (:fullUrl first-entry))))

            (testing "the second entry has the right fullUrl"
              (is (= (str base-url context-path "/Patient/2")
                     (-> body :entry second :fullUrl))))

            (testing "the first entry has the right resource"
              (given (:resource first-entry)
                :fhir/type := :fhir/Patient
                :id := "0"))

            (testing "the second entry has the right resource"
              (given (-> body :entry second :resource)
                :fhir/type := :fhir/Patient
                :id := "2"))))))

    (testing "with additional _profile search param"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :meta #fhir/Meta{:profile [#fhir/canonical"profile-uri-095443"]}}]]]

        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status] {[first-entry] :entry :as body} :body}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_id" "0" "_profile" "profile-uri-095443"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_id=0&_profile=profile-uri-095443&_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the entry has the right fullUrl"
              (is (= (str base-url context-path "/Patient/0")
                     (:fullUrl first-entry))))

            (testing "the first entry has the right resource"
              (given (:resource first-entry)
                :fhir/type := :fhir/Patient
                :id := "0"))))))

    (testing "with additional _sort search param"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status] {[first-entry] :entry :as body} :body}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_id" "0" "_sort" "_id"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_id=0&_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the entry has the right fullUrl"
              (is (= (str base-url context-path "/Patient/0")
                     (:fullUrl first-entry))))

            (testing "the first entry has the right resource"
              (given (:resource first-entry)
                :fhir/type := :fhir/Patient
                :id := "0"))))))

    (testing "with additional _profile and _sort search param matching no patient"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status body]}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_id" "0" "_profile" "profile-uri-095443" "_sort" "_id"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is zero"
              (is (= #fhir/unsignedInt 0 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_id=0&_profile=profile-uri-095443&_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains no entry"
              (is (zero? (count (:entry body))))))))))

  (testing "_lastUpdated search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "the resource is created at EPOCH"
        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status body]}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_lastUpdated" "1970-01-01"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))

      (testing "no resource is created after EPOCH"
        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status body]}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_lastUpdated" "gt1970-01-02"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is zero"
              (is (= #fhir/unsignedInt 0 (:total body))))

            (testing "the bundle contains no entry"
              (is (zero? (count (:entry body)))))))))

    (testing "deleted resources are not found"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]
         [[:delete "Patient" "0"]]]

        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status body]}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_lastUpdated" "1970-01-01"}})]

            (is (= 200 status))

            (testing "the total count is zero"
              (is (= #fhir/unsignedInt 0 (:total body))))

            (testing "the bundle contains one entry"
              (is (zero? (count (:entry body))))))))))

  (testing "_id sort"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "2"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "ascending"
        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status body]}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_sort" "_id"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 3"
              (is (= #fhir/unsignedInt 3 (:total body))))

            (testing "the bundle contains three entries"
              (is (= 3 (count (:entry body)))))

            (testing "the resources are sorted ascending"
              (given (:entry body)
                [0 :resource :id] := "0"
                [1 :resource :id] := "1"
                [2 :resource :id] := "2"))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_sort=_id&_count=50")
                     (link-url body "self"))))

            (testing "has a first link"
              (is (= (str base-url context-path "/Patient/__page?_sort=_id&_count=50&__t=3")
                     (link-url body "first")))))))

      (testing "descending"
        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status body]}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_sort" "-_id"}})]

            (is (= 422 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-supported"
              [:issue 0 :diagnostics] := "Unsupported sort direction `desc` for search param `_id`."))))))

  (testing "_lastUpdated sort"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]
       [[:put {:fhir/type :fhir/Patient :id "2"}]]]

      (testing "ascending"
        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status body]}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_sort" "_lastUpdated"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 3"
              (is (= #fhir/unsignedInt 3 (:total body))))

            (testing "the bundle contains three entries"
              (is (= 3 (count (:entry body)))))

            (testing "the resources are sorted ascending"
              (given (:entry body)
                [0 :resource :id] := "0"
                [1 :resource :id] := "1"
                [2 :resource :id] := "2"))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_sort=_lastUpdated&_count=50")
                     (link-url body "self"))))

            (testing "has a first link"
              (is (= (str base-url context-path "/Patient/__page?_sort=_lastUpdated&_count=50&__t=3")
                     (link-url body "first")))))))

      (testing "descending"
        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status body]}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_sort" "-_lastUpdated"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 3"
              (is (= #fhir/unsignedInt 3 (:total body))))

            (testing "the bundle contains three entries"
              (is (= 3 (count (:entry body)))))

            (testing "the resources are sorted ascending"
              (given (:entry body)
                [0 :resource :id] := "2"
                [1 :resource :id] := "1"
                [2 :resource :id] := "0"))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_sort=-_lastUpdated&_count=50")
                     (link-url body "self"))))

            (testing "has a first link"
              (is (= (str base-url context-path "/Patient/__page?_sort=-_lastUpdated&_count=50&__t=3")
                     (link-url body "first")))))))))

  (testing "_profile search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put
         {:fhir/type :fhir/Patient :id "1"
          :meta #fhir/Meta{:profile [#fhir/canonical"profile-uri-151511"]}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_profile" "profile-uri-151511"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/1")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              [:meta :profile 0] := #fhir/canonical"profile-uri-151511"
              :id := "1"))))))

  (testing "_profile:below search"
    (with-handler [handler]
      [[[:put
         {:fhir/type :fhir/Patient :id "0"
          :meta #fhir/Meta{:profile [#fhir/canonical"profile-uri-151511|1.1"]}}]
        [:put
         {:fhir/type :fhir/Patient :id "1"
          :meta #fhir/Meta{:profile [#fhir/canonical"profile-uri-151511|1.2"]}}]
        [:put
         {:fhir/type :fhir/Patient :id "2"
          :meta #fhir/Meta{:profile [#fhir/canonical"profile-uri-151511|2.1"]}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_profile:below" "profile-uri-151511|1"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/1")
                   (:fullUrl second-entry))))

          (testing "the first entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              [:meta :profile 0] := #fhir/canonical"profile-uri-151511|1.1"
              :id := "0"))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := :fhir/Patient
              [:meta :profile 0] := #fhir/canonical"profile-uri-151511|1.2"
              :id := "1")))

        (let [{:keys [status body]}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_profile:below" "profile-uri-151511|1"
                          "_summary" "count"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains no entries"
            (is (zero? (count (:entry body)))))))))

  (testing "_list search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/List :id "0"
               :entry
               [{:fhir/type :fhir.List/entry
                 :item
                 #fhir/Reference
                  {:reference "Patient/0"}}]}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_list" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"))))))

  (testing "value-quantity search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :value
               #fhir/Quantity
                {:value 65M
                 :code #fhir/code"kg"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :value
               #fhir/Quantity
                {:value 75M
                 :code #fhir/code"kg"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :value
               #fhir/Quantity
                {:value 100M
                 :code #fhir/code"kg"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]]]

      (doseq [handling ["strict" "lenient"]
              value ["ge70" " ge70" "ge70 " "ge 70" " ge 70 "]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Observation")
                 :headers {"prefer" (str "handling=" handling)}
                 :params {"value-quantity" value}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Observation/1")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resources"
            (given (:resource first-entry)
              :fhir/type := :fhir/Observation
              :id := "1")
            (given (-> body :entry second :resource)
              :fhir/type := :fhir/Observation
              :id := "2"))))))

  (testing "_has search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"8480-6"}]}
               :value
               #fhir/Quantity
                {:value 130M
                 :code #fhir/code"mm[Hg]"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"8480-6"}]}
               :value
               #fhir/Quantity
                {:value 150M
                 :code #fhir/code"mm[Hg]"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :subject #fhir/Reference{:reference "Patient/1"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://loinc.org"
                    :code #fhir/code"8480-6"}]}
               :value
               #fhir/Quantity
                {:value 100M
                 :code #fhir/code"mm[Hg]"
                 :system #fhir/uri"http://unitsofmeasure.org"}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_has:Observation:patient:code-value-quantity" "8480-6$ge130"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"))))))

  (testing "Patient identifier search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :identifier [#fhir/Identifier{:value "0"}]}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :identifier [#fhir/Identifier{:value "1"}]}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"identifier" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              [:identifier 0 :value] := "0"))))))

  (testing "Patient language search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :communication
               [{:fhir/type :fhir.Patient/communication
                 :language
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"urn:ietf:bcp:47"
                      :code #fhir/code"de"}]}}
                {:fhir/type :fhir.Patient/communication
                 :language
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"urn:ietf:bcp:47"
                      :code #fhir/code"en"}]}}]}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :communication
               [{:fhir/type :fhir.Patient/communication
                 :language
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"urn:ietf:bcp:47"
                      :code #fhir/code"de"}]}}]}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"language" ["de" "en"]}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (is (= "0" (-> body :entry first :resource :id))))))))

  (testing "Library title search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Library :id "0" :title "ab"}]
        [:put {:fhir/type :fhir/Library :id "1" :title "b"}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Library")
                 :headers {"prefer" (str "handling=" handling)}
                 :params {"title" "A"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Library/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Library
              :id := "0"))))))

  (testing "MeasureReport measure search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/MeasureReport :id "0"
               :measure #fhir/canonical"http://server.com/Measure/0"}]]
       [[:put {:fhir/type :fhir/MeasureReport :id "1"
               :measure #fhir/canonical"http://server.com/Measure/1"}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "MeasureReport")
                 :headers {"prefer" (str "handling=" handling)}
                 :params {"measure" "http://server.com/Measure/0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/MeasureReport/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :measure := #fhir/canonical"http://server.com/Measure/0"))))))

  (testing "List item search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/List :id "id-123058"
               :entry
               [{:fhir/type :fhir.List/entry
                 :item
                 #fhir/Reference
                  {:identifier
                   #fhir/Identifier
                    {:system #fhir/uri"system-122917"
                     :value "value-122931"}}}]}]
        [:put {:fhir/type :fhir/List :id "id-143814"
               :entry
               [{:fhir/type :fhir.List/entry
                 :item
                 #fhir/Reference
                  {:identifier
                   #fhir/Identifier
                    {:system #fhir/uri"system-122917"
                     :value "value-143818"}}}]}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "List")
                 :headers {"prefer" (str "handling=" handling)}
                 :params {"item:identifier" "system-122917|value-143818"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/List/id-143814")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :id := "id-143814"))))))

  (testing "Observation combo-code-value-quantity search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Observation :id "id-121049"
               :component
               [{:fhir/type :fhir.Observation/component
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"http://loinc.org"
                      :code #fhir/code"8480-6"}]}
                 :value
                 #fhir/Quantity
                  {:value 140M
                   :system #fhir/uri"http://unitsofmeasure.org"
                   :code #fhir/code"mm[Hg]"}}
                {:fhir/type :fhir.Observation/component
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"http://loinc.org"
                      :code #fhir/code"8462-4"}]}
                 :value
                 #fhir/Quantity
                  {:value 90M
                   :system #fhir/uri"http://unitsofmeasure.org"
                   :code #fhir/code"mm[Hg]"}}]}]]
       [[:put {:fhir/type :fhir/Observation :id "id-123130"
               :component
               [{:fhir/type :fhir.Observation/component
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"http://loinc.org"
                      :code #fhir/code"8480-6"}]}
                 :value
                 #fhir/Quantity
                  {:value 150M
                   :system #fhir/uri"http://unitsofmeasure.org"
                   :code #fhir/code"mm[Hg]"}}
                {:fhir/type :fhir.Observation/component
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri"http://loinc.org"
                      :code #fhir/code"8462-4"}]}
                 :value
                 #fhir/Quantity
                  {:value 100M
                   :system #fhir/uri"http://unitsofmeasure.org"
                   :code #fhir/code"mm[Hg]"}}]}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status body]}
              @(handler
                {::reitit/match (match-of "Observation")
                 :headers {"prefer" (str "handling=" handling)}
                 :params
                 {"combo-code-value-quantity"
                  ["http://loinc.org|8480-6$ge140|mm[Hg]"
                   "http://loinc.org|8462-4$ge90|mm[Hg]"]
                  "_count" "1"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "has a next link with search params"
            (is (= (str base-url context-path "/Observation/__page?combo-code-value-quantity=http%3A%2F%2Floinc.org%7C8480-6%24ge140%7Cmm%5BHg%5D&combo-code-value-quantity=http%3A%2F%2Floinc.org%7C8462-4%24ge90%7Cmm%5BHg%5D&_count=1&__t=2&__page-id=id-123130")
                   (link-url body "next"))))))))

  (testing "Duplicate OR Search Parameters have no Effect (#293)"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Condition :id "0"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"http://fhir.de/CodeSystem/dimdi/icd-10-gm"
                    :code #fhir/code"C71.4"}]}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Condition")
                 :headers {"prefer" (str "handling=" handling)}
                 :params {"code" "C71.4,C71.4"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Condition/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              [:code :coding 0 :code] := #fhir/code"C71.4"))))))

  (testing "Paging works with OR Search Parameters"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Condition :id "0"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:code #fhir/code"0"}]}}]
        [:put {:fhir/type :fhir/Condition :id "2"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:code #fhir/code"0"}]}}]
        [:put {:fhir/type :fhir/Condition :id "1"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:code #fhir/code"1"}]}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Condition")
                 :headers {"prefer" (str "handling=" handling)}
                 :params {"code" "0,1" "_count" "2"
                          "__t" "1" "__page-id" "1"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Condition/1")
                   (:fullUrl first-entry))))))))

  (testing "forward chaining"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Encounter
               :id "0"
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference "Condition/0"}}
                {:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference "Condition/2"}}]}]
        [:put {:fhir/type :fhir/Encounter
               :id "1"
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference "Condition/1"}}]}]
        [:put {:fhir/type :fhir/Condition
               :id "0"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding{:code #fhir/code"foo"}]}}]
        [:put {:fhir/type :fhir/Condition
               :id "1"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding{:code #fhir/code"bar"}]}}]
        [:put {:fhir/type :fhir/Condition
               :id "2"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding{:code #fhir/code"foo"}]}}]]]

      (testing "success"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Encounter")
                 :params {"diagnosis:Condition.code" "foo"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Encounter/0")
                   (:fullUrl first-entry))))))

      (testing "ambiguous type"
        (let [{:keys [status body]}
              @(handler
                {::reitit/match (match-of "Encounter")
                 :headers {"prefer" "handling=strict"}
                 :params {"diagnosis.code" "foo"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Ambiguous target types `Condition, Procedure` in the chain `diagnosis.code`. Please use a modifier to constrain the type.")))))

  (testing "Include Resources"
    (testing "direct include"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match (match-of "Observation")
                 :params {"_include" "Observation:subject"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Observation?_include=Observation%3Asubject&_count=50")
                   (link-url body "self"))))

          (testing "has a first link"
            (is (= (str base-url context-path "/Observation/__page?_include=Observation%3Asubject&_count=50&__t=1")
                   (link-url body "first"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched Observation"
            (given (-> body :entry first)
              :fullUrl := (str base-url context-path "/Observation/0")
              [:resource :fhir/type] := :fhir/Observation
              [:search :mode] := #fhir/code"match"))

          (testing "the second entry is the included Patient"
            (given (-> body :entry second)
              :fullUrl := (str base-url context-path "/Patient/0")
              [:resource :fhir/type] := :fhir/Patient
              [:search :mode] := #fhir/code"include"))))

      (testing "with non-matching target type"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   :params {"_include" "Observation:subject:Group"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the first entry is the matched Observation"
              (given (-> body :entry first)
                :fullUrl := (str base-url context-path "/Observation/0")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"match")))))

      (testing "includes don't appear twice"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "2"
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   :params {"_include" "Observation:subject"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "the bundle contains three entries"
              (is (= 3 (count (:entry body)))))

            (testing "the first entry is the first matched Observation"
              (given (-> body :entry first)
                :fullUrl := (str base-url context-path "/Observation/1")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"match"))

            (testing "the second entry is the second matched Observation"
              (given (-> body :entry second)
                :fullUrl := (str base-url context-path "/Observation/2")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"match"))

            (testing "the third entry is the included Patient"
              (given (-> body :entry (nth 2))
                :fullUrl := (str base-url context-path "/Patient/0")
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code"include")))))

      (testing "two includes"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Encounter :id "1"
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "2"
                   :subject #fhir/Reference{:reference "Patient/0"}
                   :encounter #fhir/Reference{:reference "Encounter/1"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   :params
                   {"_include" ["Observation:subject" "Observation:encounter"]}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "the bundle contains three entries"
              (is (= 3 (count (:entry body)))))

            (testing "the first entry is the matched Observation"
              (given (-> body :entry first)
                :fullUrl := (str base-url context-path "/Observation/2")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"match"))

            (testing "the second entry is the included Encounter"
              (given (-> body :entry (nth 2))
                :fullUrl := (str base-url context-path "/Encounter/1")
                [:resource :fhir/type] := :fhir/Encounter
                [:search :mode] := #fhir/code"include"))

            (testing "the third entry is the included Patient"
              (given (-> body :entry second)
                :fullUrl := (str base-url context-path "/Patient/0")
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code"include")))))

      (testing "with paging"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Patient :id "2"}]
            [:put {:fhir/type :fhir/Observation :id "3"
                   :subject #fhir/Reference{:reference "Patient/2"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   :params {"_include" "Observation:subject" "_count" "1"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has a next link"
              (is (= (str base-url context-path "/Observation/__page?_include=Observation%3Asubject&_count=1&__t=1&__page-id=3")
                     (link-url body "next"))))

            (testing "the bundle contains two entries"
              (is (= 2 (count (:entry body)))))

            (testing "the first entry is the matched Observation"
              (given (-> body :entry first)
                :fullUrl := (str base-url context-path "/Observation/1")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"match"))

            (testing "the second entry is the included Patient"
              (given (-> body :entry second)
                :fullUrl := (str base-url context-path "/Patient/0")
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code"include"))

            (testing "second page"
              (let [{:keys [status body]}
                    @(handler
                      {::reitit/match (match-of "Observation")
                       :params {"_include" "Observation:subject" "_count" "2"
                                "__t" "1" "__page-id" "3"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 2"
                  (is (= #fhir/unsignedInt 2 (:total body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Observation?_include=Observation%3Asubject&_count=2")
                         (link-url body "self"))))

                (testing "has a first link"
                  (is (= (str base-url context-path "/Observation/__page?_include=Observation%3Asubject&_count=2&__t=1")
                         (link-url body "first"))))

                (testing "the bundle contains two entries"
                  (is (= 2 (count (:entry body)))))

                (testing "the first entry is the matched Observation"
                  (given (-> body :entry first)
                    :fullUrl := (str base-url context-path "/Observation/3")
                    [:resource :fhir/type] := :fhir/Observation
                    [:search :mode] := #fhir/code"match"))

                (testing "the second entry is the included Patient"
                  (given (-> body :entry second)
                    :fullUrl := (str base-url context-path "/Patient/2")
                    [:resource :fhir/type] := :fhir/Patient
                    [:search :mode] := #fhir/code"include"))))))))

    (testing "iterative include"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/MedicationStatement :id "0"
                 :medication
                 #fhir/Reference
                  {:reference "Medication/0"}}]
          [:put {:fhir/type :fhir/Medication :id "0"
                 :manufacturer
                 #fhir/Reference
                  {:reference "Organization/0"}}]
          [:put {:fhir/type :fhir/Organization :id "0"}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match (match-of "MedicationStatement")
                 :params
                 {"_include" "MedicationStatement:medication"
                  "_include:iterate" "Medication:manufacturer"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains two entries"
            (is (= 3 (count (:entry body)))))

          (testing "the first entry is the matched MedicationStatement"
            (given (-> body :entry first)
              :fullUrl := (str base-url context-path "/MedicationStatement/0")
              [:resource :fhir/type] := :fhir/MedicationStatement
              [:search :mode] := #fhir/code"match"))

          (testing "the second entry is the included Organization"
            (given (-> body :entry second)
              :fullUrl := (str base-url context-path "/Organization/0")
              [:resource :fhir/type] := :fhir/Organization
              [:search :mode] := #fhir/code"include"))

          (testing "the third entry is the included Medication"
            (given (-> body :entry (nth 2))
              :fullUrl := (str base-url context-path "/Medication/0")
              [:resource :fhir/type] := :fhir/Medication
              [:search :mode] := #fhir/code"include")))))

    (testing "non-iterative include doesn't work iterative"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/MedicationStatement :id "0"
                 :medication
                 #fhir/Reference
                  {:reference "Medication/0"}}]
          [:put {:fhir/type :fhir/Medication :id "0"
                 :manufacturer
                 #fhir/Reference
                  {:reference "Organization/0"}}]
          [:put {:fhir/type :fhir/Organization :id "0"}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match (match-of "MedicationStatement")
                 :params
                 {"_include"
                  ["MedicationStatement:medication" "Medication:manufacturer"]}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched MedicationStatement"
            (given (-> body :entry first)
              :fullUrl := (str base-url context-path "/MedicationStatement/0")
              [:resource :fhir/type] := :fhir/MedicationStatement
              [:search :mode] := #fhir/code"match"))

          (testing "the second entry is the included Medication"
            (given (-> body :entry second)
              :fullUrl := (str base-url context-path "/Medication/0")
              [:resource :fhir/type] := :fhir/Medication
              [:search :mode] := #fhir/code"include")))))

    (testing "revinclude"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]

        (let [{:keys [status body]}
              @(handler
                {:params {"_revinclude" "Observation:subject"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_revinclude=Observation%3Asubject&_count=50")
                   (link-url body "self"))))

          (testing "has a first link"
            (is (= (str base-url context-path "/Patient/__page?_revinclude=Observation%3Asubject&_count=50&__t=1")
                   (link-url body "first"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched Patient"
            (given (-> body :entry first)
              :fullUrl := (str base-url context-path "/Patient/0")
              [:resource :fhir/type] := :fhir/Patient
              [:search :mode] := #fhir/code"match"))

          (testing "the second entry is the included Observation"
            (given (-> body :entry second)
              :fullUrl := (str base-url context-path "/Observation/1")
              [:resource :fhir/type] := :fhir/Observation
              [:search :mode] := #fhir/code"include"))))

      (testing "two revincludes"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :subject #fhir/Reference{:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Condition :id "2"
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {:params
                   {"_revinclude" ["Observation:subject" "Condition:subject"]}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_revinclude=Observation%3Asubject&_revinclude=Condition%3Asubject&_count=50")
                     (link-url body "self"))))

            (testing "has a first link"
              (is (= (str base-url context-path "/Patient/__page?_revinclude=Observation%3Asubject&_revinclude=Condition%3Asubject&_count=50&__t=1")
                     (link-url body "first"))))

            (testing "the bundle contains two entries"
              (is (= 3 (count (:entry body)))))

            (testing "the first entry is the matched Patient"
              (given (-> body :entry first)
                :fullUrl := (str base-url context-path "/Patient/0")
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code"match"))

            (testing "the second entry is the included Condition"
              (given (-> body :entry second)
                :fullUrl := (str base-url context-path "/Condition/2")
                [:resource :fhir/type] := :fhir/Condition
                [:search :mode] := #fhir/code"include"))

            (testing "the third entry is the included Observation"
              (given (-> body :entry (nth 2))
                :fullUrl := (str base-url context-path "/Observation/1")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"include"))))))

    (testing "invalid include parameter"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:headers {"prefer" "handling=strict"}
                 :params {"_include" "Observation"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Missing search parameter code in _include search parameter with source type `Observation`.")))))

  (testing "_elements"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}
               :value #fhir/string "foo"}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}
               :value #fhir/string "foo"}]]]

      (let [{:keys [status body] {[{:keys [resource] :as entry}] :entry} :body}
            @(handler
              {::reitit/match (match-of "Observation")
               :params {"_elements" "subject"
                        "_count" "1"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "the next link includes the _elements query param"
          (is (str/includes? (type/value (link-url body "next")) "_elements=subject")))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/0") (:fullUrl entry))))

        (testing "the resource is subsetted"
          (given (-> resource :meta :tag first)
            :system := #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
            :code := #fhir/code"SUBSETTED"))

        (testing "the resource has still an id"
          (is (= "0" (:id resource))))

        (testing "the resource has a subject"
          (is (= "Patient/0" (-> resource :subject :reference))))

        (testing "the resource has no value"
          (is (nil? (:value resource)))))))

  (testing "missing resource contents"
    (with-redefs [rs/multi-get (fn [_ _] (ac/completed-future {}))]
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [{:keys [status body]}
              @(handler {})]

          (is (= 500 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"incomplete"
            [:issue 0 :diagnostics] := "The resource content of `Patient/0` with hash `C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F` was not found."))))))
