(ns blaze.interaction.search-type-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.interaction.search-type]
    [blaze.interaction.search.nav-spec]
    [blaze.interaction.search.params-spec]
    [blaze.interaction.search.util-spec]
    [blaze.interaction.test-util :as itu :refer [wrap-error]]
    [blaze.middleware.fhir.db :refer [wrap-db]]
    [blaze.middleware.fhir.db-spec]
    [blaze.page-store-spec]
    [blaze.page-store.local]
    [blaze.test-util :as tu :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cuerdas.core :as c-str]
    [integrant.core :as ig]
    [java-time.api :as time]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


(st/instrument)
(tu/init-fhir-specs)
(log/set-level! :info)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def base-url "base-url-113047")


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]
     ["/Patient/__page" {:name :Patient/page}]
     ["/MeasureReport" {:name :MeasureReport/type}]
     ["/Library" {:name :Library/type}]
     ["/List" {:name :List/type}]
     ["/Condition" {:name :Condition/type}]
     ["/Observation" {:name :Observation/type}]
     ["/Observation/__page" {:name :Observation/page}]
     ["/MedicationStatement" {:name :MedicationStatement/type}]
     ["/Medication" {:name :Medication/type}]
     ["/Organization" {:name :Organization/type}]
     ["/Encounter" {:name :Encounter/type}]]
    {:syntax :bracket}))


(def patient-match
  (reitit/map->Match
    {:data
     {:fhir.resource/type "Patient"}
     :path "/Patient"}))


(def patient-search-match
  (reitit/map->Match
    {:data
     {:name :Patient/search
      :fhir.resource/type "Patient"}
     :path "/Patient"}))


(def patient-page-match
  (reitit/map->Match
    {:data
     {:name :Patient/page
      :fhir.resource/type "Patient"}
     :path "/Patient"}))


(def measure-report-match
  (reitit/map->Match
    {:data
     {:fhir.resource/type "MeasureReport"}
     :path "/MeasureReport"}))


(def list-match
  (reitit/map->Match
    {:data
     {:fhir.resource/type "List"}
     :path "/List"}))


(def observation-match
  (reitit/map->Match
    {:data
     {:fhir.resource/type "Observation"}
     :path "/Observation"}))


(def condition-match
  (reitit/map->Match
    {:data
     {:fhir.resource/type "Condition"}
     :path "/Condition"}))


(def encounter-match
  (reitit/map->Match
    {:data
     {:fhir.resource/type "Encounter"}
     :path "/Encounter"}))


(def medication-statement-match
  (reitit/map->Match
    {:data
     {:fhir.resource/type "MedicationStatement"}
     :path "/MedicationStatement"}))


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


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


(def system
  (assoc mem-node-system
    :blaze.interaction/search-type
    {:clock (ig/ref :blaze.test/fixed-clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
     :page-store (ig/ref :blaze.page-store/local)}
    :blaze.test/fixed-rng-fn {}
    :blaze.page-store/local {:secure-rng (ig/ref :blaze.test/fixed-rng)}
    :blaze.test/fixed-rng {}))


(defn wrap-defaults [handler]
  (fn [request]
    (handler
      (assoc request
        :blaze/base-url base-url
        ::reitit/router router))))


(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (itu/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.interaction/search-type} system]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node#)
                                  wrap-error)]
         ~@body))))


(deftest handler-test
  (testing "on unknown search parameter"
    (testing "with strict handling"
      (testing "returns error"
        (with-handler [handler]
          (testing "normal result"
            (let [{:keys [status body]}
                  @(handler
                     {::reitit/match patient-match
                      :headers {"prefer" "handling=strict"}
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
                     {::reitit/match patient-match
                      :headers {"prefer" "handling=strict"}
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
                       {::reitit/match patient-match
                        :headers {"prefer" "handling=lenient"}
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
                  (is (= #fhir/uri"base-url-113047/Patient?_count=50&__t=1&__page-id=0"
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    @(handler
                       {::reitit/match patient-match
                        :headers {"prefer" "handling=lenient"}
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

                (testing "the bundle contains no entries"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= #fhir/uri"base-url-113047/Patient?_summary=count&_count=50&__t=1"
                         (link-url body "self"))))))))

        (testing "with another search parameter"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Patient :id "1"
                     :active true}]]]

            (testing "normal result"
              (let [{:keys [status body]}
                    @(handler
                       {::reitit/match patient-match
                        :headers {"prefer" "handling=lenient"}
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
                  (is (= #fhir/uri"base-url-113047/Patient?active=true&_count=50&__t=1&__page-id=1"
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    @(handler
                       {::reitit/match patient-match
                        :headers {"prefer" "handling=lenient"}
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

                (testing "the bundle contains no entries"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= #fhir/uri"base-url-113047/Patient?active=true&_summary=count&_count=50&__t=1"
                         (link-url body "self"))))))))))

    (testing "with default handling"
      (testing "returns results with a self link lacking the unknown search parameter"
        (testing "where the unknown search parameter is the only one"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

            (testing "normal result"
              (let [{:keys [status body]}
                    @(handler
                       {::reitit/match patient-match
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
                  (is (= #fhir/uri"base-url-113047/Patient?_count=50&__t=1&__page-id=0"
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    @(handler
                       {::reitit/match patient-match
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

                (testing "the bundle contains no entries"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= #fhir/uri"base-url-113047/Patient?_summary=count&_count=50&__t=1"
                         (link-url body "self"))))))))

        (testing "with another search parameter"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Patient :id "1"
                     :active true}]]]

            (testing "normal result"
              (let [{:keys [status body]}
                    @(handler
                       {::reitit/match patient-match
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
                  (is (= #fhir/uri"base-url-113047/Patient?active=true&_count=50&__t=1&__page-id=1"
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    @(handler
                       {::reitit/match patient-match
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

                (testing "the bundle contains no entries"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= #fhir/uri"base-url-113047/Patient?active=true&_summary=count&_count=50&__t=1"
                         (link-url body "self")))))))))))

  (testing "on unsupported second sort parameter"
    (testing "returns error"
      (with-handler [handler]
        (testing "normal result"
          (let [{:keys [status body]}
                @(handler
                   {::reitit/match patient-match
                    :params {"_sort" "a,b"}})]

            (is (= 422 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-supported"
              [:issue 0 :diagnostics] := "More than one sort parameter is unsupported.")))

        (testing "summary result"
          (let [{:keys [status body]}
                @(handler
                   {::reitit/match patient-match
                    :params {"_sort" "a,b" "_summary" "count"}})]

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
                   {::reitit/match observation-match
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
                   {::reitit/match observation-match
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
                  :params {"__token" "invalid-token-175424" "_count" "1"
                           "__t" "1" "__page-id" "1"}})]

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
                  :params {"__token" (c-str/repeat "A" 32) "_count" "1" "__t" "1"
                           "__page-id" "1"}})]

          (is (= 422 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"not-found"
            [:issue 0 :diagnostics] := (format "Clauses of token `%s` not found."
                                               (c-str/repeat "A" 32)))))))

  (testing "with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "Returns all existing resources of type"
        (let [{:keys [status body]}
              @(handler {::reitit/match patient-match})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Patient?_count=50&__t=1&__page-id=0"
                   (link-url body "self"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= #fhir/uri"base-url-113047/Patient/0"
                   (-> body :entry first :fullUrl))))

          (testing "the entry has the right resource"
            (given (-> body :entry first :resource)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id"1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the entry has the right search information"
            (given (-> body :entry first :search)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code"match"))))

      (testing "with param _summary equal to count"
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_summary" "count"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Patient?_summary=count&_count=50&__t=1"
                   (link-url body "self"))))

          (testing "the bundle contains no entries"
            (is (empty? (:entry body))))))

      (testing "with param _count equal to zero"
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_count" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Patient?_count=0&__t=1"
                   (link-url body "self"))))

          (testing "the bundle contains no entries"
            (is (empty? (:entry body))))))))

  (testing "with two patients"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "search for all patients with _count=1"
        (let [{:keys [body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_count" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Patient?_count=1&__t=1&__page-id=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= #fhir/uri"base-url-113047/Patient/__page?_count=1&__t=1&__page-id=1"
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the self link"
        (let [{:keys [body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_count" "1" "__t" "1" "__page-id" "0"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Patient?_count=1&__t=1&__page-id=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= #fhir/uri"base-url-113047/Patient/__page?_count=1&__t=1&__page-id=1"
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_count" "1" "__t" "1" "__page-id" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Patient?_count=1&__t=1&__page-id=1"
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))))

  (testing "with three patients"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1" :active true}]
        [:put {:fhir/type :fhir/Patient :id "2" :active true}]]]

      (testing "search for active patients with _summary=count"
        (testing "with strict handling"
          (let [{:keys [body]}
                @(handler
                   {::reitit/match patient-match
                    :headers {"prefer" "handling=strict"}
                    :params {"active" "true" "_summary" "count"}})]

            (testing "their is a total count because we used _summary=count"
              (is (= #fhir/unsignedInt 2 (:total body))))))

        (testing "with default handling"
          (let [{:keys [body]}
                @(handler
                   {::reitit/match patient-match
                    :params {"active" "true" "_summary" "count"}})]

            (testing "their is a total count because we used _summary=count"
              (is (= #fhir/unsignedInt 2 (:total body)))))))

      (testing "on normal request"
        (testing "search for active patients with _count=1"
          (let [{:keys [body]}
                @(handler
                   {::reitit/match patient-match
                    :params {"active" "true" "_count" "1"}})]

            (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
              (is (nil? (:total body))))

            (testing "has a self link"
              (is (= #fhir/uri"base-url-113047/Patient?active=true&_count=1&__t=1&__page-id=1"
                     (link-url body "self"))))

            (testing "has a next link with search params"
              (is (= #fhir/uri"base-url-113047/Patient/__page?active=true&_count=1&__t=1&__page-id=2"
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))

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
              (is (= #fhir/uri"base-url-113047/Patient?active=true&_count=1&__t=1&__page-id=1"
                     (link-url body "self"))))

            (testing "has a next link with token"
              (is (= #fhir/uri"base-url-113047/Patient/__page?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_count=1&__t=1&__page-id=2"
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))

      (testing "following the self link"
        (let [{:keys [body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"active" "true" "_count" "1" "__t" "1" "__page-id" "1"}})]

          (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
            (is (nil? (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Patient?active=true&_count=1&__t=1&__page-id=1"
                   (link-url body "self"))))

          (testing "has a next link with search params"
            (is (= #fhir/uri"base-url-113047/Patient/__page?active=true&_count=1&__t=1&__page-id=2"
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

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Patient?active=true&_count=1&__t=1&__page-id=2"
                   (link-url body "self"))))

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
                   {::reitit/match patient-match
                    :params {"active" "true" "_count" "1"}})]

            (testing "has a next link with search params"
              (is (= #fhir/uri"base-url-113047/Patient/__page?active=true&_count=1&__t=1&__page-id=2"
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                   {::reitit/match patient-page-match
                    :params {"active" "true" "_count" "1" "__t" "1" "__page-id" "2"}})]

            (testing "has a next link with search params"
              (is (= #fhir/uri"base-url-113047/Patient/__page?active=true&_count=1&__t=1&__page-id=3"
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))

      (testing "on /_search request"
        (testing "search for active patients with _count=1"
          (let [{:keys [body]}
                @(handler
                   {::reitit/match patient-search-match
                    :params {"active" "true" "_count" "1"}})]

            (testing "has a next link with token"
              (is (= #fhir/uri"base-url-113047/Patient/__page?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_count=1&__t=1&__page-id=2"
                     (link-url body "next"))))))

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                   {::reitit/match patient-page-match
                    :params {"__token" "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB" "_count" "1" "__t" "1" "__page-id" "2"}})]

            (testing "has a next link with token"
              (is (= #fhir/uri"base-url-113047/Patient/__page?__token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB&_count=1&__t=1&__page-id=3"
                     (link-url body "next")))))))))


  (testing "_id search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (let [{:keys [status body]}
            @(handler
               {::reitit/match patient-match
                :params {"_id" "0"}})]

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
          (is (= #fhir/uri"base-url-113047/Patient/0"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :fhir/type := :fhir/Patient
            :id := "0"))))

    (testing "multiple id's"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"}]
          [:put {:fhir/type :fhir/Patient :id "2"}]]]

        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_id" "0,2"}})]

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
            (is (= #fhir/uri"base-url-113047/Patient/0"
                   (-> body :entry first :fullUrl))))

          (testing "the second entry has the right fullUrl"
            (is (= #fhir/uri"base-url-113047/Patient/2"
                   (-> body :entry second :fullUrl))))

          (testing "the first entry has the right resource"
            (given (-> body :entry first :resource)
              :fhir/type := :fhir/Patient
              :id := "0"))

          (testing "the second entry has the right resource"
            (given (-> body :entry second :resource)
              :fhir/type := :fhir/Patient
              :id := "2"))))))

  (testing "_lastUpdated search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (testing "the resource is created at EPOCH"
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_lastUpdated" "1970-01-01"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "no resource is created after EPOCH"
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_lastUpdated" "gt1970-01-02"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 0"
            (is (= #fhir/unsignedInt 0 (:total body))))

          (testing "the bundle contains no entry"
            (is (zero? (count (:entry body))))))))

    (testing "deleted resources are not found"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]
         [[:delete "Patient" "0"]]]

        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_lastUpdated" "1970-01-01"}})]

          (is (= 200 status))

          (testing "the total count is 0"
            (is (= #fhir/unsignedInt 0 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 0 (count (:entry body)))))))))

  (testing "_lastUpdated sort"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]
       [[:put {:fhir/type :fhir/Patient :id "2"}]]]

      (testing "ascending"
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
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
            (is (= #fhir/uri"base-url-113047/Patient?_sort=_lastUpdated&_count=50&__t=3&__page-id=0"
                   (link-url body "self"))))))

      (testing "descending"
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
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
            (is (= #fhir/uri"base-url-113047/Patient?_sort=-_lastUpdated&_count=50&__t=3&__page-id=2"
                   (link-url body "self"))))))))

  (testing "_profile search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put
         {:fhir/type :fhir/Patient :id "1"
          :meta
          #fhir/Meta
                  {:profile [#fhir/canonical"profile-uri-151511"]}}]]]

      (let [{:keys [status body]}
            @(handler
               {::reitit/match patient-match
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
          (is (= #fhir/uri"base-url-113047/Patient/1"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :fhir/type := :fhir/Patient
            [:meta :profile 0] := #fhir/canonical"profile-uri-151511"
            :id := "1")))))

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

      (let [{:keys [status body]}
            @(handler
               {::reitit/match patient-match
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
          (is (= #fhir/uri"base-url-113047/Patient/0"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :fhir/type := :fhir/Patient
            :id := "0")))))

  (testing "value-quantity search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :value
               #fhir/Quantity
                       {:value 65M
                        :code #fhir/code "kg"
                        :system #fhir/uri "http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :value
               #fhir/Quantity
                       {:value 75M
                        :code #fhir/code "kg"
                        :system #fhir/uri "http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :value
               #fhir/Quantity
                       {:value 100M
                        :code #fhir/code "kg"
                        :system #fhir/uri "http://unitsofmeasure.org"}}]]]

      (doseq [value ["ge70" " ge70" "ge70 " "ge 70" " ge 70 "]]
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match observation-match
                  :params {"value-quantity" value}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= #fhir/uri "base-url-113047/Observation/1"
                   (-> body :entry first :fullUrl))))

          (testing "the entry has the right resources"
            (given (-> body :entry first :resource)
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
               :subject
               #fhir/Reference
                       {:reference "Patient/0"}
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
               :subject
               #fhir/Reference
                       {:reference "Patient/0"}
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
               :subject
               #fhir/Reference
                       {:reference "Patient/1"}
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

      (let [{:keys [status body]}
            @(handler
               {::reitit/match patient-match
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
          (is (= #fhir/uri"base-url-113047/Patient/0"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :fhir/type := :fhir/Patient
            :id := "0")))))

  (testing "Patient identifier search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :identifier [#fhir/Identifier{:value "0"}]}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :identifier [#fhir/Identifier{:value "1"}]}]]]

      (let [{:keys [status body]}
            @(handler
               {::reitit/match patient-match
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
          (is (= #fhir/uri"base-url-113047/Patient/0"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            [:identifier 0 :value] := "0")))))

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

      (let [{:keys [status body]}
            @(handler
               {::reitit/match patient-match
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
          (is (= #fhir/uri"base-url-113047/Patient/0"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (is (= "0" (-> body :entry first :resource :id)))))))

  (testing "Library title search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Library :id "0" :title "ab"}]
        [:put {:fhir/type :fhir/Library :id "1" :title "b"}]]]

      (let [{:keys [status body]}
            @(handler
               {::reitit/match {:data {:fhir.resource/type "Library"}}
                :params {"title" "A"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= #fhir/uri"base-url-113047/Library/0"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :fhir/type := :fhir/Library
            :id := "0")))))

  (testing "MeasureReport measure search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/MeasureReport :id "0"
               :measure #fhir/canonical"http://server.com/Measure/0"}]]
       [[:put {:fhir/type :fhir/MeasureReport :id "1"
               :measure #fhir/canonical"http://server.com/Measure/1"}]]]

      (let [{:keys [status body]}
            @(handler
               {::reitit/match measure-report-match
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
          (is (= #fhir/uri"base-url-113047/MeasureReport/0"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :measure := #fhir/canonical"http://server.com/Measure/0")))))

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

      (let [{:keys [status body]}
            @(handler
               {::reitit/match list-match
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
          (is (= #fhir/uri"base-url-113047/List/id-143814"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :id := "id-143814")))))

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

      (let [{:keys [status body]}
            @(handler
               {::reitit/match observation-match
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
          (is (= #fhir/uri"base-url-113047/Observation/__page?combo-code-value-quantity=http%3A%2F%2Floinc.org%7C8480-6%24ge140%7Cmm%5BHg%5D&combo-code-value-quantity=http%3A%2F%2Floinc.org%7C8462-4%24ge90%7Cmm%5BHg%5D&_count=1&__t=2&__page-id=id-123130"
                 (link-url body "next")))))))

  (testing "Duplicate OR Search Parameters have no Effect (#293)"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Condition :id "0"
               :code
               #fhir/CodeableConcept
                       {:coding
                        [#fhir/Coding
                                {:system #fhir/uri"http://fhir.de/CodeSystem/dimdi/icd-10-gm"
                                 :code #fhir/code"C71.4"}]}}]]]

      (let [{:keys [status body]}
            @(handler
               {::reitit/match condition-match
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
          (is (= #fhir/uri"base-url-113047/Condition/0"
                 (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            [:code :coding 0 :code] := #fhir/code"C71.4")))))

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

      (let [{:keys [status body]}
            @(handler
               {::reitit/match condition-match
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
          (is (= #fhir/uri"base-url-113047/Condition/1"
                 (-> body :entry first :fullUrl)))))))

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
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match encounter-match
                  :params {"diagnosis:Condition.code" "foo"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= #fhir/uri"base-url-113047/Encounter/0"
                   (-> body :entry first :fullUrl))))))

      (testing "ambiguous type"
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match encounter-match
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
                 {::reitit/match observation-match
                  :params {"_include" "Observation:subject"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Observation?_include=Observation%3Asubject&_count=50&__t=1&__page-id=0"
                   (link-url body "self"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched Observation"
            (given (-> body :entry first)
              :fullUrl := #fhir/uri"base-url-113047/Observation/0"
              [:resource :fhir/type] := :fhir/Observation
              [:search :mode] := #fhir/code"match"))

          (testing "the second entry is the included Patient"
            (given (-> body :entry second)
              :fullUrl := #fhir/uri"base-url-113047/Patient/0"
              [:resource :fhir/type] := :fhir/Patient
              [:search :mode] := #fhir/code"include"))))

      (testing "with non-matching target type"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference "Patient/0"}}]]]

          (let [{:keys [status body]}
                @(handler
                   {::reitit/match observation-match
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
                :fullUrl := #fhir/uri"base-url-113047/Observation/0"
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
                   {::reitit/match observation-match
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
                :fullUrl := #fhir/uri"base-url-113047/Observation/1"
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"match"))

            (testing "the second entry is the second matched Observation"
              (given (-> body :entry second)
                :fullUrl := #fhir/uri"base-url-113047/Observation/2"
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"match"))

            (testing "the third entry is the included Patient"
              (given (-> body :entry (nth 2))
                :fullUrl := #fhir/uri"base-url-113047/Patient/0"
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
                   {::reitit/match observation-match
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
                :fullUrl := #fhir/uri"base-url-113047/Observation/2"
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"match"))

            (testing "the second entry is the included Encounter"
              (given (-> body :entry (nth 2))
                :fullUrl := #fhir/uri"base-url-113047/Encounter/1"
                [:resource :fhir/type] := :fhir/Encounter
                [:search :mode] := #fhir/code"include"))

            (testing "the third entry is the included Patient"
              (given (-> body :entry second)
                :fullUrl := #fhir/uri"base-url-113047/Patient/0"
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code"include")))))

      (testing "with paging"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :subject
                   #fhir/Reference
                           {:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Patient :id "2"}]
            [:put {:fhir/type :fhir/Observation :id "3"
                   :subject
                   #fhir/Reference
                           {:reference "Patient/2"}}]]]

          (let [{:keys [status body]}
                @(handler
                   {::reitit/match observation-match
                    :params {"_include" "Observation:subject" "_count" "1"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has a next link"
              (is (= #fhir/uri"base-url-113047/Observation/__page?_include=Observation%3Asubject&_count=1&__t=1&__page-id=3"
                     (link-url body "next"))))

            (testing "the bundle contains two entries"
              (is (= 2 (count (:entry body)))))

            (testing "the first entry is the matched Observation"
              (given (-> body :entry first)
                :fullUrl := #fhir/uri"base-url-113047/Observation/1"
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"match"))

            (testing "the second entry is the included Patient"
              (given (-> body :entry second)
                :fullUrl := #fhir/uri"base-url-113047/Patient/0"
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code"include"))

            (testing "second page"
              (let [{:keys [status body]}
                    @(handler
                       {::reitit/match observation-match
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
                  (is (= #fhir/uri"base-url-113047/Observation?_include=Observation%3Asubject&_count=2&__t=1&__page-id=3"
                         (link-url body "self"))))

                (testing "the bundle contains two entries"
                  (is (= 2 (count (:entry body)))))

                (testing "the first entry is the matched Observation"
                  (given (-> body :entry first)
                    :fullUrl := #fhir/uri"base-url-113047/Observation/3"
                    [:resource :fhir/type] := :fhir/Observation
                    [:search :mode] := #fhir/code"match"))

                (testing "the second entry is the included Patient"
                  (given (-> body :entry second)
                    :fullUrl := #fhir/uri"base-url-113047/Patient/2"
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
                 {::reitit/match medication-statement-match
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
              :fullUrl := #fhir/uri"base-url-113047/MedicationStatement/0"
              [:resource :fhir/type] := :fhir/MedicationStatement
              [:search :mode] := #fhir/code"match"))

          (testing "the second entry is the included Organization"
            (given (-> body :entry second)
              :fullUrl := #fhir/uri"base-url-113047/Organization/0"
              [:resource :fhir/type] := :fhir/Organization
              [:search :mode] := #fhir/code"include"))

          (testing "the third entry is the included Medication"
            (given (-> body :entry (nth 2))
              :fullUrl := #fhir/uri"base-url-113047/Medication/0"
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
                 {::reitit/match medication-statement-match
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
              :fullUrl := #fhir/uri"base-url-113047/MedicationStatement/0"
              [:resource :fhir/type] := :fhir/MedicationStatement
              [:search :mode] := #fhir/code"match"))

          (testing "the second entry is the included Medication"
            (given (-> body :entry second)
              :fullUrl := #fhir/uri"base-url-113047/Medication/0"
              [:resource :fhir/type] := :fhir/Medication
              [:search :mode] := #fhir/code"include")))))

    (testing "revinclude"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject
                 #fhir/Reference
                         {:reference "Patient/0"}}]]]

        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :params {"_revinclude" "Observation:subject"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-113047/Patient?_revinclude=Observation%3Asubject&_count=50&__t=1&__page-id=0"
                   (link-url body "self"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched Patient"
            (given (-> body :entry first)
              :fullUrl := #fhir/uri"base-url-113047/Patient/0"
              [:resource :fhir/type] := :fhir/Patient
              [:search :mode] := #fhir/code"match"))

          (testing "the second entry is the included Observation"
            (given (-> body :entry second)
              :fullUrl := #fhir/uri"base-url-113047/Observation/1"
              [:resource :fhir/type] := :fhir/Observation
              [:search :mode] := #fhir/code"include"))))

      (testing "two revincludes"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :subject
                   #fhir/Reference
                           {:reference "Patient/0"}}]
            [:put {:fhir/type :fhir/Condition :id "2"
                   :subject
                   #fhir/Reference
                           {:reference "Patient/0"}}]]]

          (let [{:keys [status body]}
                @(handler
                   {::reitit/match patient-match
                    :params
                    {"_revinclude" ["Observation:subject" "Condition:subject"]}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code"searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link"
              (is (= #fhir/uri"base-url-113047/Patient?_revinclude=Observation%3Asubject&_revinclude=Condition%3Asubject&_count=50&__t=1&__page-id=0"
                     (link-url body "self"))))

            (testing "the bundle contains two entries"
              (is (= 3 (count (:entry body)))))

            (testing "the first entry is the matched Patient"
              (given (-> body :entry first)
                :fullUrl := #fhir/uri"base-url-113047/Patient/0"
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code"match"))

            (testing "the second entry is the included Condition"
              (given (-> body :entry second)
                :fullUrl := #fhir/uri"base-url-113047/Condition/2"
                [:resource :fhir/type] := :fhir/Condition
                [:search :mode] := #fhir/code"include"))

            (testing "the third entry is the included Observation"
              (given (-> body :entry (nth 2))
                :fullUrl := #fhir/uri"base-url-113047/Observation/1"
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code"include"))))))

    (testing "invalid include parameter"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :headers {"prefer" "handling=strict"}
                  :params {"_include" "Observation"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Missing search parameter code in _include search parameter with source type `Observation`."))))))
