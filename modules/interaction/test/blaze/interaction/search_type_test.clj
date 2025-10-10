(ns blaze.interaction.search-type-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.query.plan.spec]
   [blaze.db.resource-store :as rs]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [link-url]]
   [blaze.interaction.search-type]
   [blaze.interaction.search.nav-spec]
   [blaze.interaction.search.params-spec]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util-spec]
   [blaze.interaction.test-util :refer [coding v3-ObservationValue wrap-error]]
   [blaze.job-scheduler-spec]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.module-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.page-id-cipher.spec]
   [blaze.page-store-spec]
   [blaze.page-store.local]
   [blaze.page-store.spec]
   [blaze.spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-url "base-url-113047")
(def context-path "/context-path-173858")

(def router
  (reitit/router
   [["/Patient" {:name :Patient/type}]
    ["/Patient/__page/{page-id}" {:name :Patient/page}]
    ["/MeasureReport" {:name :MeasureReport/type}]
    ["/MeasureReport/__page/{page-id}" {:name :MeasureReport/page}]
    ["/Library" {:name :Library/type}]
    ["/Library/__page/{page-id}" {:name :Library/page}]
    ["/List" {:name :List/type}]
    ["/List/__page/{page-id}" {:name :List/page}]
    ["/Condition" {:name :Condition/type}]
    ["/Condition/__page/{page-id}" {:name :Condition/page}]
    ["/Observation" {:name :Observation/type}]
    ["/Observation/__page/{page-id}" {:name :Observation/page}]
    ["/MedicationStatement" {:name :MedicationStatement/type}]
    ["/MedicationStatement/__page/{page-id}" {:name :MedicationStatement/page}]
    ["/Medication" {:name :Medication/type}]
    ["/Organization" {:name :Organization/type}]
    ["/Encounter" {:name :Encounter/type}]
    ["/Encounter/__page/{page-id}" {:name :Encounter/page}]]
   {:syntax :bracket
    :path context-path}))

(defn match-of [type]
  (reitit/map->Match
   {:data
    {:name (keyword type "type")
     :fhir.resource/type type}
    :path (str context-path "/" type)}))

(defn page-match-of [type]
  (reitit/map->Match
   {:data
    {:name (keyword type "page")
     :fhir.resource/type type}
    :path (str context-path "/" type)}))

(def patient-search-match
  (reitit/map->Match
   {:data
    {:name :Patient/search
     :fhir.resource/type "Patient"}
    :path (str context-path "/Patient")}))

(def patient-page-match
  (page-match-of "Patient"))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.interaction/search-type
   {::search-util/link (ig/ref ::search-util/link)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref :blaze.page-store/local)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)
    :context-path context-path}

   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   ::search-util/link {:fhir/version "4.0.1"}
   :blaze.page-store/local {}
   :blaze.test/fixed-rng-fn {}
   :blaze.test/fixed-rng {}
   :blaze.test/page-id-cipher {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction/search-type nil}
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/search-type {}}
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% ::search-util/link))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :page-store))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "invalid link function"
    (given-failed-system (assoc-in config [:blaze.interaction/search-type ::search-util/link] ::invalid)
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::search-util/link]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.interaction/search-type :clock] ::invalid)
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.interaction/search-type :rng-fn] ::invalid)
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid page-store"
    (given-failed-system (assoc-in config [:blaze.interaction/search-type :page-store] ::invalid)
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid page-id-cipher"
    (given-failed-system (assoc-in config [:blaze.interaction/search-type :page-id-cipher] ::invalid)
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-id-cipher]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid context-path"
    (given-failed-system (assoc-in config [:blaze.interaction/search-type :context-path] ::invalid)
      :key := :blaze.interaction/search-type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/context-path]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(defn- wrap-defaults [handler]
  (fn [{::reitit/keys [match] :as request}]
    (handler
     (cond-> (assoc request
                    :blaze/base-url base-url
                    ::reitit/router router)
       (nil? match)
       (assoc ::reitit/match (match-of "Patient"))))))

(defn- wrap-db [handler node page-id-cipher]
  (fn [{::reitit/keys [match] :as request}]
    (if (= "page" (some-> match :data :name name))
      ((decrypt-page-id/wrap-decrypt-page-id
        (db/wrap-snapshot-db handler node 100)
        page-id-cipher)
       request)
      ((db/wrap-db handler node 100) request))))

(defn- wrap-job-scheduler [handler job-scheduler]
  (fn [request]
    (handler (assoc request :blaze/job-scheduler job-scheduler))))

(defmacro with-handler [[handler-binding & [node-binding page-id-cipher-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         page-id-cipher# :blaze.test/page-id-cipher
                         job-scheduler# :blaze/job-scheduler
                         handler# :blaze.interaction/search-type} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node# page-id-cipher#)
                                  (wrap-job-scheduler job-scheduler#)
                                  wrap-error)
             ~(or node-binding '_) node#
             ~(or page-id-cipher-binding '_) page-id-cipher#]
         ~@body))))

(defn- page-url [page-id-cipher type query-params]
  (str base-url context-path "/" type "/__page/" (decrypt-page-id/encrypt page-id-cipher query-params)))

(defn- page-path-params [page-id-cipher params]
  {:page-id (decrypt-page-id/encrypt page-id-cipher params)})

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
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "not-found"
                [:issue 0 :diagnostics] := #fhir/string "The search-param with code `foo` and type `Patient` was not found.")))

          (testing "summary result"
            (let [{:keys [status body]}
                  @(handler
                    {:headers {"prefer" "handling=strict"}
                     :params {"foo" "bar" "_summary" "count"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "not-found"
                [:issue 0 :diagnostics] := #fhir/string "The search-param with code `foo` and type `Patient` was not found."))))))

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
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?_count=50")
                         (link-url body "self"))))))

            (testing "summary result"
              (doseq [prefer ["handling=lenient" "handling=lenient,respond-async"]]
                (let [{:keys [status body]}
                      @(handler
                        {:headers {"prefer" prefer}
                         :params {"foo" "bar" "_summary" "count"}})]

                  (is (= 200 status))

                  (testing "the body contains a bundle"
                    (is (= :fhir/Bundle (:fhir/type body))))

                  (testing "the bundle id is an LUID"
                    (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                  (testing "the bundle type is searchset"
                    (is (= #fhir/code "searchset" (:type body))))

                  (testing "the total count is 1"
                    (is (= #fhir/unsignedInt 1 (:total body))))

                  (testing "the bundle contains no entry"
                    (is (empty? (:entry body))))

                  (testing "has a self link"
                    (is (= (str base-url context-path "/Patient?_summary=count&_count=50")
                           (link-url body "self")))))))))

        (testing "with another search parameter"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Patient :id "1"
                     :active #fhir/boolean true}]]]

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
                  (is (= #fhir/code "searchset" (:type body))))

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
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?active=true&_summary=count&_count=50")
                         (link-url body "self")))))

              (testing "with async response"
                (let [{:keys [status headers]}
                      @(handler
                        {:request-method :get
                         :headers {"prefer" "handling=lenient,respond-async"}
                         :uri (str context-path "/Patient")
                         :query-string "foo=bar&active=true&_summary=count"
                         :params {"foo" "bar" "active" "true" "_summary" "count"}})]

                  (is (= 202 status))

                  (testing "the Content-Location header contains the status endpoint URL"
                    (is (= (get headers "Content-Location")
                           (str base-url context-path "/__async-status/AAAAAAAAAAAAAAAA")))))))))))

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
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?_count=50")
                         (link-url body "self"))))))

            (testing "summary result"
              (doseq [prefer ["" "respond-async"]]
                (let [{:keys [status body]}
                      @(handler
                        {:headers {"prefer" prefer}
                         :params {"foo" "bar" "_summary" "count"}})]

                  (is (= 200 status))

                  (testing "the body contains a bundle"
                    (is (= :fhir/Bundle (:fhir/type body))))

                  (testing "the bundle id is an LUID"
                    (is (= "AAAAAAAAAAAAAAAA" (:id body))))

                  (testing "the bundle type is searchset"
                    (is (= #fhir/code "searchset" (:type body))))

                  (testing "the total count is 1"
                    (is (= #fhir/unsignedInt 1 (:total body))))

                  (testing "the bundle contains no entry"
                    (is (empty? (:entry body))))

                  (testing "has a self link"
                    (is (= (str base-url context-path "/Patient?_summary=count&_count=50")
                           (link-url body "self")))))))))

        (testing "with another search parameter"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Patient :id "1"
                     :active #fhir/boolean true}]]]

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
                  (is (= #fhir/code "searchset" (:type body))))

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
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient?active=true&_summary=count&_count=50")
                         (link-url body "self")))))

              (testing "with async response"
                (let [{:keys [status headers]}
                      @(handler
                        {:request-method :get
                         :headers {"prefer" "respond-async"}
                         :uri (str context-path "/Patient")
                         :query-string "foo=bar&active=true&_summary=count"
                         :params {"foo" "bar" "active" "true" "_summary" "count"}})]

                  (is (= 202 status))

                  (testing "the Content-Location header contains the status endpoint URL"
                    (is (= (get headers "Content-Location")
                           (str base-url context-path "/__async-status/AAAAAAAAAAAAAAAA"))))))))))))

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
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "not-supported"
              [:issue 0 :diagnostics] := #fhir/string "More than one sort parameter is unsupported.")))

        (testing "summary result"
          (let [{:keys [status body]}
                @(handler
                  {:params {"_sort" "a,b" "_summary" "count"}})]

            (is (= 422 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "not-supported"
              [:issue 0 :diagnostics] := #fhir/string "More than one sort parameter is unsupported."))))))

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
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "invalid"
              [:issue 0 :diagnostics] := #fhir/string "Invalid date-time value `2021-12-09T00:00:00 01:00` in search parameter `date`.")))

        (testing "summary result"
          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   ;; the date is already URl decoded and so contains a space instead of a plus
                   :params {"date" "2021-12-09T00:00:00 01:00" "_summary" "count"}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "invalid"
              [:issue 0 :diagnostics] := #fhir/string "Invalid date-time value `2021-12-09T00:00:00 01:00` in search parameter `date`."))))))

  (testing "on invalid token"
    (testing "returns error"
      (with-handler [handler _ page-id-cipher]
        (let [{:keys [status body]}
              @(handler
                {::reitit/match patient-page-match
                 :path-params (page-path-params page-id-cipher {"__t" "0" "__token" "invalid-token-175424"})})]

          (is (= 422 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid token `invalid-token-175424`.")))))

  (testing "on missing token"
    (testing "returns error"
      (with-handler [handler _ page-id-cipher]
        (let [{:keys [status body]}
              @(handler
                {::reitit/match patient-page-match
                 :path-params (page-path-params page-id-cipher {"__t" "0" "__token" (str/join (repeat 64 "A"))})})]

          (is (= 404 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := (type/string (format "Clauses of token `%s` not found." (str/join (repeat 64 "A")))))))))

  (testing "with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :multipleBirth #fhir/boolean true}]]]

      (testing "Returns all existing resources of type"
        (doseq [params [{} {"_summary" "false"}]]
          (let [{:keys [status] {[first-entry] :entry :as body} :body}
                @(handler {:params params})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the entry has the right fullUrl"
              (is (= (str base-url context-path "/Patient/0")
                     (-> first-entry :fullUrl :value))))

            (testing "the entry has the right resource"
              (given (:resource first-entry)
                :fhir/type := :fhir/Patient
                :id := "0"
                [:meta :versionId] := #fhir/id "1"
                [:meta :lastUpdated] := Instant/EPOCH
                [:meta :tag (coding v3-ObservationValue) count] := 0
                :multipleBirth := #fhir/boolean true))

            (testing "the entry has the right search mode"
              (given (:search first-entry)
                fhir-spec/fhir-type := :fhir.Bundle.entry/search
                :mode := #fhir/code "match")))))

      (testing "with param _summary equal to true"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler {:params {"_summary" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_summary=true&_count=50")
                   (link-url body "self"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := Instant/EPOCH
              [:meta :tag (coding v3-ObservationValue) 0 :code] := #fhir/code "SUBSETTED"
              :multipleBirth := nil))

          (testing "the entry has the right search mode"
            (given (:search first-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code "match"))))

      (testing "with param _summary equal to count"
        (let [{:keys [status body]}
              @(handler {:params {"_summary" "count"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_summary=count&_count=50")
                   (link-url body "self"))))

          (testing "the bundle contains no entry"
            (is (empty? (:entry body))))))

      (testing "with param _count equal to zero"
        (let [{:keys [status body]}
              @(handler {:params {"_count" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_count=0")
                   (link-url body "self"))))

          (testing "the bundle contains no entry"
            (is (empty? (:entry body))))))))

  (testing "with two patients"
    (with-handler [handler node page-id-cipher]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "search for all patients with _count=1"
        (let [{:keys [body]}
              @(handler {:params {"_count" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_count=1")
                   (link-url body "self"))))

          (testing "has a first link"
            (is (= (page-url page-id-cipher "Patient" {"_count" "1" "__t" "1"})
                   (link-url body "first"))))

          (testing "has a next link"
            (is (= (page-url page-id-cipher "Patient" {"_count" "1" "__t" "1" "__page-id" "1"})
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @(handler
                {::reitit/match patient-page-match
                 :path-params (page-path-params page-id-cipher {"_count" "1" "__t" "1" "__page-id" "1"})})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has no self link"
            (is (nil? (link-url body "self"))))

          (testing "has a first link"
            (is (= (page-url page-id-cipher "Patient" {"_count" "1" "__t" "1"})
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
              (is (= (page-url page-id-cipher "Patient" {"_count" "1" "__t" "1"})
                     (link-url body "first"))))

            (testing "has a next link"
              (is (= (page-url page-id-cipher "Patient" {"_count" "1" "__t" "1" "__page-id" "1"})
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))

      (testing "adding a third patient doesn't influence the paging"
        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "2"}]])

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-page-match
                   :path-params (page-path-params page-id-cipher {"_count" "1" "__t" "1" "__page-id" "1"})})]

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has no self link"
              (is (nil? (link-url body "self"))))

            (testing "has a first link"
              (is (= (page-url page-id-cipher "Patient" {"_count" "1" "__t" "1"})
                     (link-url body "first"))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))))

  (testing "with three patients"
    (with-handler [handler _ page-id-cipher]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1" :active #fhir/boolean true}]
        [:put {:fhir/type :fhir/Patient :id "2" :active #fhir/boolean true}]]]

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

            (testing "there is no total count because we have clauses and we have
                    more hits than page-size"
              (is (nil? (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?active=true&_count=1")
                     (link-url body "self"))))

            (testing "has a first link"
              (is (= (page-url page-id-cipher "Patient" {"active" ["true"] "_count" "1" "__t" "1"})
                     (link-url body "first"))))

            (testing "has a next link with search params"
              (is (= (page-url page-id-cipher "Patient" {"active" ["true"] "_count" "1" "__t" "1" "__page-id" "2"})
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))

          (testing "with _total=accurate"
            (let [{:keys [body]}
                  @(handler
                    {:params {"active" "true" "_total" "accurate" "_count" "1"}})]

              (testing "the total count is 2"
                (is (= #fhir/unsignedInt 2 (:total body))))

              (testing "has a self link"
                (is (= (str base-url context-path "/Patient?active=true&_total=accurate&_count=1")
                       (link-url body "self"))))

              (testing "has a first link"
                (is (= (page-url page-id-cipher "Patient" {"active" ["true"] "_total" "accurate" "_count" "1" "__t" "1"})
                       (link-url body "first"))))

              (testing "has a next link with search params"
                (is (= (page-url page-id-cipher "Patient" {"active" ["true"] "_total" "accurate" "_count" "1" "__t" "1" "__page-id" "2"})
                       (link-url body "next"))))

              (testing "the bundle contains one entry"
                (is (= 1 (count (:entry body))))))))

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

            (testing "there is no total count because we have clauses and we have
                    more hits than page-size"
              (is (nil? (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?active=true&_count=1")
                     (link-url body "self"))))

            (testing "has a first link with token"
              (is (= (page-url page-id-cipher "Patient" {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_count" "1" "__t" "1"})
                     (link-url body "first"))))

            (testing "has a next link with token"
              (is (= (page-url page-id-cipher "Patient" {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_count" "1" "__t" "1" "__page-id" "2"})
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))

          (testing "with _total=accurate"
            (let [{:keys [body]}
                  @(handler
                    {::reitit/match patient-search-match
                     :params {"active" "true" "_total" "accurate" "_count" "1"}})]

              (testing "the total count is 2"
                (is (= #fhir/unsignedInt 2 (:total body))))

              (testing "has a self link"
                (is (= (str base-url context-path "/Patient?active=true&_total=accurate&_count=1")
                       (link-url body "self"))))

              (testing "has a first link with token"
                (is (= (page-url page-id-cipher "Patient" {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_total" "accurate" "_count" "1" "__t" "1"})
                       (link-url body "first"))))

              (testing "has a next link with token"
                (is (= (page-url page-id-cipher "Patient" {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_total" "accurate" "_count" "1" "__t" "1" "__page-id" "2"})
                       (link-url body "next"))))

              (testing "the bundle contains one entry"
                (is (= 1 (count (:entry body))))))))

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

      (testing "following the next link"
        (let [{:keys [body]}
              @(handler
                {::reitit/match patient-page-match
                 :path-params (page-path-params page-id-cipher {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_count" "1" "__t" "1" "__page-id" "2"})})]

          (testing "there is no total count because we have clauses and we have
                    more hits than page-size"
            (is (nil? (:total body))))

          (testing "has no self link"
            (is (nil? (link-url body "self"))))

          (testing "has a first link with token"
            (is (= (page-url page-id-cipher "Patient" {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_count" "1" "__t" "1"})
                   (link-url body "first"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))))

  (testing "with four patients"
    (with-handler [handler _ page-id-cipher]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1" :active #fhir/boolean true}]
        [:put {:fhir/type :fhir/Patient :id "2" :active #fhir/boolean true}]
        [:put {:fhir/type :fhir/Patient :id "3" :active #fhir/boolean true}]]]

      (testing "on normal request"
        (testing "search for active patients with _count=1"
          (let [{:keys [body]}
                @(handler
                  {:params {"active" "true" "_count" "1"}})]

            (testing "has a next link with search params"
              (is (= (page-url page-id-cipher "Patient" {"active" ["true"] "_count" "1" "__t" "1" "__page-id" "2"})
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-page-match
                   :path-params (page-path-params page-id-cipher {"active" "true" "_count" "1" "__t" "1" "__page-id" "2"})})]

            (testing "has no self link"
              (is (nil? (link-url body "self"))))

            (testing "has a next link with search params"
              (is (= (page-url page-id-cipher "Patient" {"active" ["true"] "_count" "1" "__t" "1" "__page-id" "3"})
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
              (is (= (page-url page-id-cipher "Patient" {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_count" "1" "__t" "1"})
                     (link-url body "first"))))

            (testing "has a first link with token"
              (is (= (page-url page-id-cipher "Patient" {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_count" "1" "__t" "1" "__page-id" "2"})
                     (link-url body "next"))))))

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match patient-page-match
                   :path-params (page-path-params page-id-cipher {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_count" "1" "__t" "1" "__page-id" "2"})})]

            (testing "has no self link"
              (is (nil? (link-url body "self"))))

            (testing "has a next link with token"
              (is (= (page-url page-id-cipher "Patient" {"__token" "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7" "_count" "1" "__t" "1" "__page-id" "3"})
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
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_id=0&_count=50")
                   (link-url body "self"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0")))))

    (testing "multiple id's"
      (with-handler [handler _ page-id-cipher]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"}]
          [:put {:fhir/type :fhir/Patient :id "2"}]
          [:put {:fhir/type :fhir/Patient :id "3"}]
          [:put {:fhir/type :fhir/Patient :id "4"}]]]

        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_id" "0,2,3,4" "_count" "2"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "there is no total count because we have clauses and we
                        have more hits than page-size"
              (is (nil? (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_id=0%2C2%2C3%2C4&_count=2")
                     (link-url body "self"))))

            (testing "has a first link"
              (is (= (page-url page-id-cipher "Patient" {"_id" ["0,2,3,4"] "_count" "2" "__t" "1"})
                     (link-url body "first"))))

            (testing "has a next link"
              (is (= (page-url page-id-cipher "Patient" {"_id" ["0,2,3,4"] "_count" "2" "__t" "1" "__page-id" "3"})
                     (link-url body "next"))))

            (testing "the bundle contains two entries"
              (is (= 2 (count (:entry body)))))

            (testing "the first entry has the right fullUrl"
              (is (= (str base-url context-path "/Patient/0")
                     (-> first-entry :fullUrl :value))))

            (testing "the second entry has the right fullUrl"
              (is (= (str base-url context-path "/Patient/2")
                     (-> second-entry :fullUrl :value))))

            (testing "the first entry has the right resource"
              (given (:resource first-entry)
                :fhir/type := :fhir/Patient
                :id := "0"))

            (testing "the second entry has the right resource"
              (given (:resource second-entry)
                :fhir/type := :fhir/Patient
                :id := "2")))

          (testing "following the next link"
            (let [{{[first-entry second-entry] :entry :as body} :body}
                  @(handler
                    {::reitit/match patient-page-match
                     :path-params (page-path-params page-id-cipher {"_id" ["0,2,3,4"] "_count" "2" "__t" "1" "__page-id" "3"})})]

              (testing "there is no total count because we have clauses and we
                        have more hits than page-size"
                (is (nil? (:total body))))

              (testing "has no self link"
                (is (nil? (link-url body "self"))))

              (testing "has a first link"
                (is (= (page-url page-id-cipher "Patient" {"_id" ["0,2,3,4"] "_count" "2" "__t" "1"})
                       (link-url body "first"))))

              (testing "has no next link"
                (is (nil? (link-url body "next"))))

              (testing "the bundle contains two entries"
                (is (= 2 (count (:entry body)))))

              (testing "the first entry has the right fullUrl"
                (is (= (str base-url context-path "/Patient/3")
                       (-> first-entry :fullUrl :value))))

              (testing "the second entry has the right fullUrl"
                (is (= (str base-url context-path "/Patient/4")
                       (-> second-entry :fullUrl :value))))

              (testing "the first entry has the right resource"
                (given (:resource first-entry)
                  :fhir/type := :fhir/Patient
                  :id := "3"))

              (testing "the second entry has the right resource"
                (given (:resource second-entry)
                  :fhir/type := :fhir/Patient
                  :id := "4")))))))

    (testing "with additional _profile search param"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :meta #fhir/Meta{:profile [#fhir/canonical "profile-uri-095443"]}}]]]

        (doseq [handling ["strict" "lenient"]]
          (let [{:keys [status] {[first-entry] :entry :as body} :body}
                @(handler
                  {:headers {"prefer" (str "handling=" handling)}
                   :params {"_id" "0" "_profile" "profile-uri-095443"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_id=0&_profile=profile-uri-095443&_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the entry has the right fullUrl"
              (is (= (str base-url context-path "/Patient/0")
                     (-> first-entry :fullUrl :value))))

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
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_id=0&_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the entry has the right fullUrl"
              (is (= (str base-url context-path "/Patient/0")
                     (-> first-entry :fullUrl :value))))

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
              (is (= #fhir/code "searchset" (:type body))))

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
              (is (= #fhir/code "searchset" (:type body))))

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
              (is (= #fhir/code "searchset" (:type body))))

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
    (with-handler [handler _ page-id-cipher]
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
              (is (= #fhir/code "searchset" (:type body))))

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
              (is (= (page-url page-id-cipher "Patient" {"_sort" "_id" "_count" "50" "__t" "3"})
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
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "not-supported"
              [:issue 0 :diagnostics] := #fhir/string "Unsupported sort direction `desc` for search param `_id`."))))))

  (testing "_lastUpdated sort"
    (with-handler [handler _ page-id-cipher]
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
              (is (= #fhir/code "searchset" (:type body))))

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
              (is (= (page-url page-id-cipher "Patient" {"_sort" "_lastUpdated" "_count" "50" "__t" "3"})
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
              (is (= #fhir/code "searchset" (:type body))))

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
              (is (= (page-url page-id-cipher "Patient" {"_sort" "-_lastUpdated" "_count" "50" "__t" "3"})
                     (link-url body "first")))))))))

  (testing "_profile search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put
         {:fhir/type :fhir/Patient :id "1"
          :meta #fhir/Meta{:profile [#fhir/canonical "profile-uri-151511"]}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_profile" "profile-uri-151511"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/1")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              [:meta :profile 0] := #fhir/canonical "profile-uri-151511"
              :id := "1"))))))

  (testing "_tag search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put
         {:fhir/type :fhir/Patient :id "1"
          :meta #fhir/Meta{:tag [#fhir/Coding{:code #fhir/code "code-085510"}]}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_tag" "code-085510"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/1")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              [:meta :tag 0] := #fhir/Coding{:code #fhir/code "code-085510"}
              :id := "1"))))))

  (testing "_profile:below search"
    (with-handler [handler]
      [[[:put
         {:fhir/type :fhir/Patient :id "0"
          :meta #fhir/Meta{:profile [#fhir/canonical "profile-uri-151511|1.1"]}}]
        [:put
         {:fhir/type :fhir/Patient :id "1"
          :meta #fhir/Meta{:profile [#fhir/canonical "profile-uri-151511|1.2"]}}]
        [:put
         {:fhir/type :fhir/Patient :id "2"
          :meta #fhir/Meta{:profile [#fhir/canonical "profile-uri-151511|2.1"]}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_profile:below" "profile-uri-151511|1"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the second entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/1")
                   (-> second-entry :fullUrl :value))))

          (testing "the first entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              [:meta :profile 0] := #fhir/canonical "profile-uri-151511|1.1"
              :id := "0"))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := :fhir/Patient
              [:meta :profile 0] := #fhir/canonical "profile-uri-151511|1.2"
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
            (is (= #fhir/code "searchset" (:type body))))

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
                  {:reference #fhir/string "Patient/0"}}]}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_list" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"))))))

  (testing "value-quantity search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Observation :id "0"
               :value
               #fhir/Quantity
                {:value #fhir/decimal 65M
                 :code #fhir/code "kg"
                 :system #fhir/uri "http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :value
               #fhir/Quantity
                {:value #fhir/decimal 75M
                 :code #fhir/code "kg"
                 :system #fhir/uri "http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :value
               #fhir/Quantity
                {:value #fhir/decimal 100M
                 :code #fhir/code "kg"
                 :system #fhir/uri "http://unitsofmeasure.org"}}]]]

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
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Observation/1")
                   (-> first-entry :fullUrl :value))))

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
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri "http://loinc.org"
                    :code #fhir/code "8480-6"}]}
               :value
               #fhir/Quantity
                {:value #fhir/decimal 130M
                 :code #fhir/code "mm[Hg]"
                 :system #fhir/uri "http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri "http://loinc.org"
                    :code #fhir/code "8480-6"}]}
               :value
               #fhir/Quantity
                {:value #fhir/decimal 150M
                 :code #fhir/code "mm[Hg]"
                 :system #fhir/uri "http://unitsofmeasure.org"}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :subject #fhir/Reference{:reference #fhir/string "Patient/1"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri "http://loinc.org"
                    :code #fhir/code "8480-6"}]}
               :value
               #fhir/Quantity
                {:value #fhir/decimal 100M
                 :code #fhir/code "mm[Hg]"
                 :system #fhir/uri "http://unitsofmeasure.org"}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"_has:Observation:patient:code-value-quantity" "8480-6$ge130"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"))))))

  (testing "Patient identifier search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :identifier [#fhir/Identifier{:value #fhir/string "0"}]}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :identifier [#fhir/Identifier{:value #fhir/string "1"}]}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"identifier" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              [:identifier 0 :value] := #fhir/string "0"))))))

  (testing "Patient language search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :communication
               [{:fhir/type :fhir.Patient/communication
                 :language
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "urn:ietf:bcp:47"
                      :code #fhir/code "de"}]}}
                {:fhir/type :fhir.Patient/communication
                 :language
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "urn:ietf:bcp:47"
                      :code #fhir/code "en"}]}}]}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :communication
               [{:fhir/type :fhir.Patient/communication
                 :language
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "urn:ietf:bcp:47"
                      :code #fhir/code "de"}]}}]}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {:headers {"prefer" (str "handling=" handling)}
                 :params {"language" ["de" "en"]}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (is (= "0" (-> body :entry first :resource :id))))))))

  (testing "Library title search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Library :id "0" :title #fhir/string "ab"}]
        [:put {:fhir/type :fhir/Library :id "1" :title #fhir/string "b"}]]]

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
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Library/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Library
              :id := "0"))))))

  (testing "MeasureReport measure search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/MeasureReport :id "0"
               :measure #fhir/canonical "http://server.com/Measure/0"}]]
       [[:put {:fhir/type :fhir/MeasureReport :id "1"
               :measure #fhir/canonical "http://server.com/Measure/1"}]]]

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
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/MeasureReport/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :measure := #fhir/canonical "http://server.com/Measure/0"))))))

  (testing "List item search"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/List :id "id-123058"
               :entry
               [{:fhir/type :fhir.List/entry
                 :item
                 #fhir/Reference
                  {:identifier
                   #fhir/Identifier
                    {:system #fhir/uri "system-122917"
                     :value #fhir/string "value-122931"}}}]}]
        [:put {:fhir/type :fhir/List :id "id-143814"
               :entry
               [{:fhir/type :fhir.List/entry
                 :item
                 #fhir/Reference
                  {:identifier
                   #fhir/Identifier
                    {:system #fhir/uri "system-122917"
                     :value #fhir/string "value-143818"}}}]}]]]

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
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/List/id-143814")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :id := "id-143814"))))))

  (testing "Observation combo-code-value-quantity search"
    (with-handler [handler _ page-id-cipher]
      [[[:put {:fhir/type :fhir/Observation :id "id-121049"
               :component
               [{:fhir/type :fhir.Observation/component
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "http://loinc.org"
                      :code #fhir/code "8480-6"}]}
                 :value
                 #fhir/Quantity
                  {:value #fhir/decimal 140M
                   :system #fhir/uri "http://unitsofmeasure.org"
                   :code #fhir/code "mm[Hg]"}}
                {:fhir/type :fhir.Observation/component
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "http://loinc.org"
                      :code #fhir/code "8462-4"}]}
                 :value
                 #fhir/Quantity
                  {:value #fhir/decimal 90M
                   :system #fhir/uri "http://unitsofmeasure.org"
                   :code #fhir/code "mm[Hg]"}}]}]]
       [[:put {:fhir/type :fhir/Observation :id "id-123130"
               :component
               [{:fhir/type :fhir.Observation/component
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "http://loinc.org"
                      :code #fhir/code "8480-6"}]}
                 :value
                 #fhir/Quantity
                  {:value #fhir/decimal 150M
                   :system #fhir/uri "http://unitsofmeasure.org"
                   :code #fhir/code "mm[Hg]"}}
                {:fhir/type :fhir.Observation/component
                 :code
                 #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "http://loinc.org"
                      :code #fhir/code "8462-4"}]}
                 :value
                 #fhir/Quantity
                  {:value #fhir/decimal 100M
                   :system #fhir/uri "http://unitsofmeasure.org"
                   :code #fhir/code "mm[Hg]"}}]}]]]

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
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "has a next link with search params"
            (is (= (page-url page-id-cipher "Observation"
                             {"combo-code-value-quantity"
                              ["http://loinc.org|8480-6$ge140|mm[Hg]"
                               "http://loinc.org|8462-4$ge90|mm[Hg]"]
                              "_count" "1" "__t" "2"
                              "__page-id" "id-123130"})
                   (link-url body "next"))))))))

  (testing "Duplicate OR Search Parameters have no Effect (#293)"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Condition :id "0"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri "http://fhir.de/CodeSystem/dimdi/icd-10-gm"
                    :code #fhir/code "C71.4"}]}}]]]

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
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Condition/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              [:code :coding 0 :code] := #fhir/code "C71.4"))))))

  (testing "Paging works with OR Search Parameters"
    (with-handler [handler _ page-id-cipher]
      [[[:put {:fhir/type :fhir/Condition :id "0"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:code #fhir/code "0"}]}}]
        [:put {:fhir/type :fhir/Condition :id "2"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:code #fhir/code "0"}]}}]
        [:put {:fhir/type :fhir/Condition :id "1"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:code #fhir/code "1"}]}}]]]

      (doseq [handling ["strict" "lenient"]]
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (page-match-of "Condition")
                 :headers {"prefer" (str "handling=" handling)}
                 :path-params (page-path-params page-id-cipher {"code" "0,1" "_count" "2"
                                                                "__t" "1" "__page-id" "2"})})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Condition/2")
                   (-> first-entry :fullUrl :value))))))))

  (testing "forward chaining"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Encounter
               :id "0"
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference #fhir/string "Condition/0"}}
                {:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference #fhir/string "Condition/2"}}]}]
        [:put {:fhir/type :fhir/Encounter
               :id "1"
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition
                 #fhir/Reference{:reference #fhir/string "Condition/1"}}]}]
        [:put {:fhir/type :fhir/Condition
               :id "0"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding{:code #fhir/code "foo"}]}}]
        [:put {:fhir/type :fhir/Condition
               :id "1"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding{:code #fhir/code "bar"}]}}]
        [:put {:fhir/type :fhir/Condition
               :id "2"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding{:code #fhir/code "foo"}]}}]]]

      (testing "success"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Encounter")
                 :params {"diagnosis:Condition.code" "foo"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Encounter/0")
                   (-> first-entry :fullUrl :value))))))

      (testing "ambiguous type"
        (let [{:keys [status body]}
              @(handler
                {::reitit/match (match-of "Encounter")
                 :headers {"prefer" "handling=strict"}
                 :params {"diagnosis.code" "foo"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Ambiguous target types `Condition, Procedure` in the chain `diagnosis.code`. Please use a modifier to constrain the type.")))))

  (testing "Include Resources"
    (testing "direct include"
      (with-handler [handler _ page-id-cipher]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match (match-of "Observation")
                 :params {"_include" "Observation:subject"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Observation?_include=Observation%3Asubject&_count=50")
                   (link-url body "self"))))

          (testing "has a first link"
            (is (= (page-url page-id-cipher "Observation"
                             {"_include" ["Observation:subject"]
                              "_count" "50" "__t" "1"})
                   (link-url body "first"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched Observation"
            (given (-> body :entry first)
              [:fullUrl :value] := (str base-url context-path "/Observation/0")
              [:resource :fhir/type] := :fhir/Observation
              [:search :mode] := #fhir/code "match"))

          (testing "the second entry is the included Patient"
            (given (-> body :entry second)
              [:fullUrl :value] := (str base-url context-path "/Patient/0")
              [:resource :fhir/type] := :fhir/Patient
              [:search :mode] := #fhir/code "include"))))

      (testing "with non-matching target type"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "0"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   :params {"_include" "Observation:subject:Group"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the first entry is the matched Observation"
              (given (-> body :entry first)
                [:fullUrl :value] := (str base-url context-path "/Observation/0")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code "match")))))

      (testing "includes don't appear twice"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "2"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   :params {"_include" "Observation:subject"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "the bundle contains three entries"
              (is (= 3 (count (:entry body)))))

            (testing "the first entry is the first matched Observation"
              (given (-> body :entry first)
                [:fullUrl :value] := (str base-url context-path "/Observation/1")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code "match"))

            (testing "the second entry is the second matched Observation"
              (given (-> body :entry second)
                [:fullUrl :value] := (str base-url context-path "/Observation/2")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code "match"))

            (testing "the third entry is the included Patient"
              (given (-> body :entry (nth 2))
                [:fullUrl :value] := (str base-url context-path "/Patient/0")
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code "include")))))

      (testing "two includes"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Encounter :id "1"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            [:put {:fhir/type :fhir/Observation :id "2"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
                   :encounter #fhir/Reference{:reference #fhir/string "Encounter/1"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   :params
                   {"_include" ["Observation:subject" "Observation:encounter"]}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "the bundle contains three entries"
              (is (= 3 (count (:entry body)))))

            (testing "the first entry is the matched Observation"
              (given (-> body :entry first)
                [:fullUrl :value] := (str base-url context-path "/Observation/2")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code "match"))

            (testing "the second entry is the included Encounter"
              (given (-> body :entry (nth 2))
                [:fullUrl :value] := (str base-url context-path "/Encounter/1")
                [:resource :fhir/type] := :fhir/Encounter
                [:search :mode] := #fhir/code "include"))

            (testing "the third entry is the included Patient"
              (given (-> body :entry second)
                [:fullUrl :value] := (str base-url context-path "/Patient/0")
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code "include")))))

      (testing "with paging"
        (with-handler [handler _ page-id-cipher]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            [:put {:fhir/type :fhir/Patient :id "2"}]
            [:put {:fhir/type :fhir/Observation :id "3"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/2"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {::reitit/match (match-of "Observation")
                   :params {"_include" "Observation:subject" "_count" "1"}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has a next link"
              (is (= (page-url page-id-cipher "Observation"
                               {"_include" ["Observation:subject"]
                                "_count" "1" "__t" "1"
                                "__page-id" "3"})
                     (link-url body "next"))))

            (testing "the bundle contains two entries"
              (is (= 2 (count (:entry body)))))

            (testing "the first entry is the matched Observation"
              (given (-> body :entry first)
                [:fullUrl :value] := (str base-url context-path "/Observation/1")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code "match"))

            (testing "the second entry is the included Patient"
              (given (-> body :entry second)
                [:fullUrl :value] := (str base-url context-path "/Patient/0")
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code "include"))

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
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 2"
                  (is (= #fhir/unsignedInt 2 (:total body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Observation?_include=Observation%3Asubject&_count=2")
                         (link-url body "self"))))

                (testing "has a first link"
                  (is (= (page-url page-id-cipher "Observation"
                                   {"_include" ["Observation:subject"]
                                    "_count" "2" "__t" "1"})
                         (link-url body "first"))))

                (testing "the bundle contains two entries"
                  (is (= 2 (count (:entry body)))))

                (testing "the first entry is the matched Observation"
                  (given (-> body :entry first)
                    [:fullUrl :value] := (str base-url context-path "/Observation/3")
                    [:resource :fhir/type] := :fhir/Observation
                    [:search :mode] := #fhir/code "match"))

                (testing "the second entry is the included Patient"
                  (given (-> body :entry second)
                    [:fullUrl :value] := (str base-url context-path "/Patient/2")
                    [:resource :fhir/type] := :fhir/Patient
                    [:search :mode] := #fhir/code "include"))))))))

    (testing "iterative include"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/MedicationStatement :id "0"
                 :medication
                 #fhir/Reference
                  {:reference #fhir/string "Medication/0"}}]
          [:put {:fhir/type :fhir/Medication :id "0"
                 :manufacturer
                 #fhir/Reference
                  {:reference #fhir/string "Organization/0"}}]
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
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains two entries"
            (is (= 3 (count (:entry body)))))

          (testing "the first entry is the matched MedicationStatement"
            (given (-> body :entry first)
              [:fullUrl :value] := (str base-url context-path "/MedicationStatement/0")
              [:resource :fhir/type] := :fhir/MedicationStatement
              [:search :mode] := #fhir/code "match"))

          (testing "the second entry is the included Organization"
            (given (-> body :entry second)
              [:fullUrl :value] := (str base-url context-path "/Organization/0")
              [:resource :fhir/type] := :fhir/Organization
              [:search :mode] := #fhir/code "include"))

          (testing "the third entry is the included Medication"
            (given (-> body :entry (nth 2))
              [:fullUrl :value] := (str base-url context-path "/Medication/0")
              [:resource :fhir/type] := :fhir/Medication
              [:search :mode] := #fhir/code "include")))))

    (testing "non-iterative include doesn't work iterative"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/MedicationStatement :id "0"
                 :medication
                 #fhir/Reference
                  {:reference #fhir/string "Medication/0"}}]
          [:put {:fhir/type :fhir/Medication :id "0"
                 :manufacturer
                 #fhir/Reference
                  {:reference #fhir/string "Organization/0"}}]
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
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched MedicationStatement"
            (given (-> body :entry first)
              [:fullUrl :value] := (str base-url context-path "/MedicationStatement/0")
              [:resource :fhir/type] := :fhir/MedicationStatement
              [:search :mode] := #fhir/code "match"))

          (testing "the second entry is the included Medication"
            (given (-> body :entry second)
              [:fullUrl :value] := (str base-url context-path "/Medication/0")
              [:resource :fhir/type] := :fhir/Medication
              [:search :mode] := #fhir/code "include")))))

    (testing "revinclude"
      (with-handler [handler _ page-id-cipher]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [{:keys [status body]}
              @(handler
                {:params {"_revinclude" "Observation:subject"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient?_revinclude=Observation%3Asubject&_count=50")
                   (link-url body "self"))))

          (testing "has a first link"
            (is (= (page-url page-id-cipher "Patient" {"_revinclude" ["Observation:subject"]
                                                       "_count" "50" "__t" "1"})
                   (link-url body "first"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched Patient"
            (given (-> body :entry first)
              [:fullUrl :value] := (str base-url context-path "/Patient/0")
              [:resource :fhir/type] := :fhir/Patient
              [:search :mode] := #fhir/code "match"))

          (testing "the second entry is the included Observation"
            (given (-> body :entry second)
              [:fullUrl :value] := (str base-url context-path "/Observation/1")
              [:resource :fhir/type] := :fhir/Observation
              [:search :mode] := #fhir/code "include"))))

      (testing "two revincludes"
        (with-handler [handler _ page-id-cipher]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]
            [:put {:fhir/type :fhir/Observation :id "1"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            [:put {:fhir/type :fhir/Condition :id "2"
                   :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

          (let [{:keys [status body]}
                @(handler
                  {:params
                   {"_revinclude" ["Observation:subject" "Condition:subject"]}})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient?_revinclude=Observation%3Asubject&_revinclude=Condition%3Asubject&_count=50")
                     (link-url body "self"))))

            (testing "has a first link"
              (is (= (page-url page-id-cipher "Patient" {"_revinclude" ["Observation:subject" "Condition:subject"]
                                                         "_count" "50" "__t" "1"})
                     (link-url body "first"))))

            (testing "the bundle contains two entries"
              (is (= 3 (count (:entry body)))))

            (testing "the first entry is the matched Patient"
              (given (-> body :entry first)
                [:fullUrl :value] := (str base-url context-path "/Patient/0")
                [:resource :fhir/type] := :fhir/Patient
                [:search :mode] := #fhir/code "match"))

            (testing "the second entry is the included Condition"
              (given (-> body :entry second)
                [:fullUrl :value] := (str base-url context-path "/Condition/2")
                [:resource :fhir/type] := :fhir/Condition
                [:search :mode] := #fhir/code "include"))

            (testing "the third entry is the included Observation"
              (given (-> body :entry (nth 2))
                [:fullUrl :value] := (str base-url context-path "/Observation/1")
                [:resource :fhir/type] := :fhir/Observation
                [:search :mode] := #fhir/code "include"))))))

    (testing "invalid include parameter"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:headers {"prefer" "handling=strict"}
                 :params {"_include" "Observation"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Missing search parameter code in _include search parameter with source type `Observation`.")))))

  (testing "_elements"
    (with-handler [handler _ page-id-cipher]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :value #fhir/string "foo"}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
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
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "has a next link"
          (is (= (page-url page-id-cipher "Observation"
                           {"_elements" "subject"
                            "_count" "1" "__t" "1"
                            "__page-id" "1"})
                 (link-url body "next"))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/0")
                 (-> entry :fullUrl :value))))

        (testing "the resource is subsetted"
          (given (-> resource :meta :tag (coding v3-ObservationValue) first)
            :code := #fhir/code "SUBSETTED"))

        (testing "the resource has still an id"
          (is (= "0" (:id resource))))

        (testing "the resource has a subject"
          (is (= #fhir/string "Patient/0" (-> resource :subject :reference))))

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
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "incomplete"
            [:issue 0 :diagnostics] := #fhir/string "The resource content of `Patient/0` with hash `C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F` was not found."))))))

(deftest handler-query-stats-test
  (with-handler [handler]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :status #fhir/code "final"
             :code #fhir/CodeableConcept
                    {:coding
                     [#fhir/Coding
                       {:system #fhir/uri "http://loinc.org"
                        :code #fhir/code "94564-2"}]}
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
             :effective #fhir/dateTime "2025"}]]]

    (testing "no search param"
      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler
              {::reitit/match (match-of "Observation")
               :params {"__explain" "true"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the bundle contains two entries"
          (is (= 1 (count (:entry body)))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Observation
            :id := "0"))))

    (testing "one unknown search param"
      (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
            @(handler
              {::reitit/match (match-of "Observation")
               :params {"foo" "bar" "__explain" "true"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the bundle contains two entries"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "information"
            [:issue 0 :code] := #fhir/code "informational"
            [:issue 0 :diagnostics] := #fhir/string "SCANS: NONE; SEEKS: NONE"))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Observation
            :id := "0")))

      (testing "with strict handling"
        (let [{:keys [status] body :body}
              @(handler
                {::reitit/match (match-of "Observation")
                 :headers {"prefer" "handling=strict"}
                 :params {"foo" "bar" "__explain" "true"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := #fhir/string "The search-param with code `foo` and type `Observation` was not found."))))

    (testing "one token search param"
      (testing "with match"
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Observation")
                 :params {"status" "final" "__explain" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "information"
              [:issue 0 :code] := #fhir/code "informational"
              [:issue 0 :diagnostics] := #fhir/string "SCANS(ordered): status; SEEKS: NONE"))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := :fhir/Observation
              :id := "0"))))

      (testing "without match"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Observation")
                 :params {"status" "preliminary" "__explain" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains two entries"
            (is (= 1 (count (:entry body)))))

          (testing "the first entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "information"
              [:issue 0 :code] := #fhir/code "informational"
              [:issue 0 :diagnostics] := #fhir/string "SCANS(ordered): status; SEEKS: NONE"))))

      (testing "with modifier"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Observation")
                 :params {"_profile:below" "foo" "__explain" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains two entries"
            (is (= 1 (count (:entry body)))))

          (testing "the first entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "information"
              [:issue 0 :code] := #fhir/code "informational"
              [:issue 0 :diagnostics] := #fhir/string "SCANS(ordered): _profile:below; SEEKS: NONE")))))

    (testing "two token search params"
      (testing "with match"
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Observation")
                 :params {"status" "final" "code" "94564-2" "__explain" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "information"
              [:issue 0 :code] := #fhir/code "informational"
              [:issue 0 :diagnostics] := #fhir/string "SCANS(ordered): status, code; SEEKS: NONE"))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := :fhir/Observation
              :id := "0")))))

    (testing "one token and one date search param"
      (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
            @(handler
              {::reitit/match (match-of "Observation")
               :params {"status" "final" "date" "2025" "__explain" "true"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the bundle contains two entries"
          (is (= 2 (count (:entry body)))))

        (testing "the entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "information"
            [:issue 0 :code] := #fhir/code "informational"
            [:issue 0 :diagnostics] := #fhir/string "SCANS(ordered): status; SEEKS: date"))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Observation
            :id := "0"))))

    (testing "patient compartment search"
      (testing "one token search param"
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Observation")
                 :params {"patient" "0" "code" "http://loinc.org|94564-2" "__explain" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "information"
              [:issue 0 :code] := #fhir/code "informational"
              [:issue 0 :diagnostics] := #fhir/string "TYPE: compartment; SCANS(ordered): code; SEEKS: NONE"))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := :fhir/Observation
              :id := "0"))))

      (testing "one unindexed token search param"
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Observation")
                 :params {"patient" "0" "code" "94564-2" "__explain" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "information"
              [:issue 0 :code] := #fhir/code "informational"
              [:issue 0 :diagnostics] := #fhir/string "TYPE: compartment; SCANS: NONE; SEEKS: code"))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := :fhir/Observation
              :id := "0"))))

      (testing "one token and one date search param"
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {::reitit/match (match-of "Observation")
                 :params {"patient" "0" "code" "http://loinc.org|94564-2" "date" "2025" "__explain" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "information"
              [:issue 0 :code] := #fhir/code "informational"
              [:issue 0 :diagnostics] := #fhir/string "TYPE: compartment; SCANS(ordered): code; SEEKS: date"))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := :fhir/Observation
              :id := "0")))))))
