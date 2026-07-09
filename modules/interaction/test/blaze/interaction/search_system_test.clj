(ns blaze.interaction.search-system-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-cache :as rc]
   [blaze.fhir.test-util :refer [link-url]]
   [blaze.interaction.search-system]
   [blaze.interaction.search.nav-spec]
   [blaze.interaction.search.page-spec]
   [blaze.interaction.search.params-spec]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util-spec]
   [blaze.interaction.test-util :refer [coding v3-ObservationValue wrap-error]]
   [blaze.metrics.spec]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.module-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.page-id-cipher.spec]
   [blaze.page-store-spec]
   [blaze.page-store.local]
   [blaze.page-store.spec]
   [blaze.test-util :as tu]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-url "base-url-114650")

(def router
  (reitit/router
   [["/__page/{page-id}" {:name :page}]
    ["/Patient" {:name :Patient/type}]
    ["/Observation" {:name :Observation/type}]
    ["/Group" {:name :Group/type}]]
   {:syntax :bracket}))

(def default-match
  (reitit/map->Match
   {:data
    {:blaze/base-url ""}
    :path ""}))

(def page-match
  (reitit/map->Match
   {:data
    {:name :page
     :blaze/base-url ""}
    :path ""}))

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.interaction/search-system
   {::search-util/link (ig/ref ::search-util/link)
    :node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref :blaze.page-store/local)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   ::search-util/link {:fhir/version "4.0.1"}
   :blaze.page-store/local {}
   :blaze.test/fixed-rng {}
   :blaze.test/page-id-cipher {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction/search-system nil}
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/search-system {}}
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% ::search-util/link))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :page-store))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "missing clock"
    (given-failed-system (update config :blaze.interaction/search-system dissoc :clock)
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "missing rng-fn"
    (given-failed-system (update config :blaze.interaction/search-system dissoc :rng-fn)
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "missing page-store"
    (given-failed-system (update config :blaze.interaction/search-system dissoc :page-store)
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :page-store))))

  (testing "missing page-id-cipher"
    (given-failed-system (update config :blaze.interaction/search-system dissoc :page-id-cipher)
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "invalid link function"
    (given-failed-system (assoc-in config [:blaze.interaction/search-system ::search-util/link] ::invalid)
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::search-util/link]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.interaction/search-system :clock] ::invalid)
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.interaction/search-system :rng-fn] ::invalid)
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid page-store"
    (given-failed-system (assoc-in config [:blaze.interaction/search-system :page-store] ::invalid)
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid page-id-cipher"
    (given-failed-system (assoc-in config [:blaze.interaction/search-system :page-id-cipher] ::invalid)
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-id-cipher]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest search-duration-seconds-collector-init-test
  (with-system [{collector :blaze.interaction.search-system/search-duration-seconds}
                {:blaze.interaction.search-system/search-duration-seconds nil}]
    (is (s/valid? :blaze.metrics/collector collector))))

(defn wrap-defaults [handler]
  (fn [{::reitit/keys [match] :as request}]
    (handler
     (cond-> (assoc request
                    :blaze/base-url base-url
                    ::reitit/router router)
       (nil? match)
       (assoc ::reitit/match default-match)))))

(defn wrap-db [handler node page-id-cipher]
  (fn [{::reitit/keys [match] :as request}]
    (if (= page-match match)
      ((decrypt-page-id/wrap-decrypt-page-id
        (db/wrap-snapshot-db handler node 100)
        page-id-cipher)
       request)
      ((db/wrap-db handler node 100) request))))

(defmacro with-handler [[handler-binding & [node-binding page-id-cipher-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         page-id-cipher# :blaze.test/page-id-cipher
                         handler# :blaze.interaction/search-system} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults
                                  (wrap-db node# page-id-cipher#)
                                  wrap-error)
             ~(or node-binding '_) node#
             ~(or page-id-cipher-binding '_) page-id-cipher#]
         ~@body))))

(defn- page-url [page-id-cipher query-params]
  (str base-url "/__page/" (decrypt-page-id/encrypt page-id-cipher query-params)))

(defn- page-path-params [page-id-cipher params]
  {:page-id (decrypt-page-id/encrypt page-id-cipher params)})

(deftest handler-test
  (testing "on empty database"
    (with-handler [handler]
      (testing "Returns all existing resources"
        (let [{:keys [status body]}
              @(handler {})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is zero"
            (is (= #fhir/unsignedInt 0 (:total body))))

          (testing "has a self link"
            (is (= (str base-url "?_count=50")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains no entry"
            (is (zero? (count (:entry body)))))))))

  (testing "with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :multipleBirth #fhir/boolean true}]]]

      (testing "Returns all existing resources"
        (doseq [params [{} {"_summary" "false"}]]
          (let [{:keys [status] {[first-entry] :entry :as body} :body}
                @(handler {:params params})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle id is an LUID"
              (is (= "AAAAAAAAAAAAAAAA" (:id body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link"
              (is (= (str base-url "?_count=50")
                     (link-url body "self"))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the entry has the right fullUrl"
              (is (= (str base-url "/Patient/0")
                     (-> first-entry :fullUrl :value))))

            (testing "the entry has the right resource"
              (given (:resource first-entry)
                :fhir/type := :fhir/Patient
                :id := "0"
                [:meta :versionId] := #fhir/id "1"
                [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z"
                [:meta :tag (coding v3-ObservationValue) count] := 0
                :multipleBirth := #fhir/boolean true)))))

      (testing "with param _summary equal to true"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler {:params {"_summary" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url "?_summary=true&_count=50")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z"
              [:meta :tag (coding v3-ObservationValue) 0 :code] := #fhir/code "SUBSETTED"
              :multipleBirth := nil))))

      (testing "with param _summary equal to count"
        (let [{:keys [status body]}
              @(handler {:params {"_summary" "count"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url "?_summary=count&_count=50")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

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
            (is (= (str base-url "?_count=0")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains no entry"
            (is (empty? (:entry body))))))))

  (testing "with two patients"
    (with-handler [handler node page-id-cipher]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "search for all patients with _count=1"
        (let [{:keys [status body]}
              @(handler {:params {"_count" "1"}})]

          (is (= 200 status))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url "?_count=1")
                   (link-url body "self"))))

          (testing "has a first link"
            (is (= (page-url page-id-cipher {"_count" "1" "__t" "1"})
                   (link-url body "first"))))

          (testing "has no previous link"
            (is (nil? (link-url body "previous"))))

          (testing "has a next link"
            (is (= (page-url page-id-cipher {"_count" "1" "__t" "1" "__page-type" "Patient" "__page-id" "1" "__page-id-stack" [""]})
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @(handler
                {::reitit/match page-match
                 :path-params
                 (page-path-params
                  page-id-cipher
                  {"_count" "1" "__t" "1" "__page-type" "Patient"
                   "__page-id" "1" "__page-id-stack" [""]})})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has no self link"
            (is (nil? (link-url body "self"))))

          (testing "has a first link"
            (is (= (page-url page-id-cipher {"_count" "1" "__t" "1"})
                   (link-url body "first"))))

          (testing "has a previous link pointing to the first page"
            (is (= (page-url page-id-cipher {"_count" "1" "__t" "1"})
                   (link-url body "previous"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "adding a third patient doesn't influence the paging"
        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "2"}]])

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                  {::reitit/match page-match
                   :path-params
                   (page-path-params
                    page-id-cipher
                    {"_count" "1" "__t" "1" "__page-type" "Patient"
                     "__page-id" "1" "__page-id-stack" [""]})})]

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has no self link"
              (is (nil? (link-url body "self"))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))))

  (testing "with unknown search parameter"
    (testing "with strict handling"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (doseq [params [{"foo" "bar"} {"foo" "bar" "_summary" "count"}]]
          (let [{:keys [status body]}
                @(handler
                  {:headers {"prefer" "handling=strict"}
                   :params params})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "not-found"
              [:issue 0 :diagnostics] := #fhir/string "The search-param with code `foo` and type `Resource` was not found.")))))

    (testing "with lenient handling"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (testing "normal result"
          (let [{:keys [status body]}
                @(handler {:params {"foo" "bar"}})]

            (is (= 200 status))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link lacking the unknown search parameter"
              (is (= (str base-url "?_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))

        (testing "summary result"
          (let [{:keys [status body]}
                @(handler {:params {"foo" "bar" "_summary" "count"}})]

            (is (= 200 status))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "has a self link lacking the unknown search parameter"
              (is (= (str base-url "?_summary=count&_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains no entry"
              (is (empty? (:entry body)))))))))

  (testing "with _tag search parameter"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :meta #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "system-190657" :code #fhir/code "code-190711"}]}}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :meta #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "system-190657" :code #fhir/code "code-190711"}]}}]]]

      (doseq [headers [{} {"prefer" "handling=strict"}]]
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {:headers headers
                 :params {"_tag" "system-190657|code-190711"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link containing the _tag search parameter"
            (is (= (str base-url "?_tag=system-190657%7Ccode-190711&_count=50")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entries contain only the tagged resources in the order
                    of their type hashes"
            (given (:resource first-entry)
              :fhir/type := :fhir/Observation
              :id := "0")
            (given (:resource second-entry)
              :fhir/type := :fhir/Patient
              :id := "0"))))

      (testing "with _summary=count"
        (let [{:keys [status body]}
              @(handler
                {:params {"_tag" "system-190657|code-190711"
                          "_summary" "count"}})]

          (is (= 200 status))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link containing the _tag search parameter"
            (is (= (str base-url "?_tag=system-190657%7Ccode-190711&_summary=count&_count=50")
                   (link-url body "self"))))

          (testing "the bundle contains no entry"
            (is (empty? (:entry body))))))))

  (testing "with _list search parameter"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Observation :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "1"}]
        [:put {:fhir/type :fhir/List :id "0"
               :entry
               [{:fhir/type :fhir.List/entry
                 :item #fhir/Reference{:reference #fhir/string "Patient/0"}}
                {:fhir/type :fhir.List/entry
                 :item #fhir/Reference{:reference #fhir/string "Observation/0"}}]}]]]

      (doseq [headers [{} {"prefer" "handling=strict"}]]
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {:headers headers
                 :params {"_list" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link containing the _list search parameter"
            (is (= (str base-url "?_list=0&_count=50")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entries contain only the resources referenced in the
                    list in the order of their type hashes"
            (given (:resource first-entry)
              :fhir/type := :fhir/Observation
              :id := "0")
            (given (:resource second-entry)
              :fhir/type := :fhir/Patient
              :id := "0"))))))

  (testing "with _has search parameter"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Group :id "0"}]
        [:put {:fhir/type :fhir/Group :id "1"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri "http://loinc.org"
                    :code #fhir/code "8480-6"}]}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Group/0"}
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri "http://loinc.org"
                    :code #fhir/code "8480-6"}]}}]]]

      (doseq [headers [{} {"prefer" "handling=strict"}]]
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {:headers headers
                 :params {"_has:Observation:subject:code" "8480-6"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link containing the _has search parameter"
            (is (= (str base-url "?_has%3AObservation%3Asubject%3Acode=8480-6&_count=50")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the entries contain only the resources referenced by the
                    matching observations in the order of their type hashes"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0")
            (given (:resource second-entry)
              :fhir/type := :fhir/Group
              :id := "0"))))))

  (testing "with _elements search parameter"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :gender #fhir/code "male"
               :birthDate #fhir/date #system/date "2024"}]]]

      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler {:params {"_elements" "gender"}})]

        (is (= 200 status))

        (testing "has a self link containing the _elements search parameter"
          (is (= (str base-url "?_elements=gender&_count=50")
                 (link-url body "self"))))

        (testing "the entry contains only the gender element"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "0"
            :gender := #fhir/code "male"
            :birthDate := nil
            [:meta :tag (coding v3-ObservationValue) 0 :code] := #fhir/code "SUBSETTED")))))

  (testing "paging with _tag search parameter"
    (with-handler [handler _ page-id-cipher]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :meta #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "system-190657" :code #fhir/code "code-190711"}]}}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :meta #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "system-190657" :code #fhir/code "code-190711"}]}}]
        [:put {:fhir/type :fhir/Patient :id "2"
               :meta #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "system-190657" :code #fhir/code "code-190711"}]}}]
        [:put {:fhir/type :fhir/Patient :id "3"}]]]

      (testing "search for tagged resources with _count=1"
        (let [{:keys [status body]}
              @(handler
                {:params {"_tag" "system-190657|code-190711" "_count" "1"}})]

          (is (= 200 status))

          (testing "there is no total count because we have clauses and we
                      have more hits than page-size"
            (is (nil? (:total body))))

          (testing "has a self link"
            (is (= (str base-url "?_tag=system-190657%7Ccode-190711&_count=1")
                   (link-url body "self"))))

          (testing "has a first link with search params"
            (is (= (page-url page-id-cipher {"_tag" ["system-190657|code-190711"] "_count" "1" "__t" "1"})
                   (link-url body "first"))))

          (testing "has no previous link"
            (is (nil? (link-url body "previous"))))

          (testing "has a next link with search params"
            (is (= (page-url page-id-cipher {"_tag" ["system-190657|code-190711"] "_count" "1" "__t" "1"
                                             "__page-type" "Patient" "__page-id" "1" "__page-id-stack" [""]})
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body))))
            (given (:resource (first (:entry body)))
              :fhir/type := :fhir/Patient
              :id := "0"))))

      (testing "with _total=accurate"
        (let [{:keys [body]}
              @(handler
                {:params {"_tag" "system-190657|code-190711"
                          "_total" "accurate" "_count" "1"}})]

          (testing "the total count is 3"
            (is (= #fhir/unsignedInt 3 (:total body))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @(handler
                {::reitit/match page-match
                 :path-params
                 (page-path-params
                  page-id-cipher
                  {"_tag" ["system-190657|code-190711"] "_count" "1" "__t" "1"
                   "__page-type" "Patient" "__page-id" "1" "__page-id-stack" [""]})})]

          (testing "there is no total count"
            (is (nil? (:total body))))

          (testing "has no self link"
            (is (nil? (link-url body "self"))))

          (testing "has a first link"
            (is (= (page-url page-id-cipher {"_tag" ["system-190657|code-190711"] "_count" "1" "__t" "1"})
                   (link-url body "first"))))

          (testing "has a previous link pointing to the first page"
            (is (= (page-url page-id-cipher {"_tag" ["system-190657|code-190711"] "_count" "1" "__t" "1"})
                   (link-url body "previous"))))

          (testing "has a next link with the current page start pushed onto the
                      page-id-stack"
            (is (= (page-url page-id-cipher {"_tag" ["system-190657|code-190711"] "_count" "1" "__t" "1"
                                             "__page-type" "Patient" "__page-id" "2" "__page-id-stack" ["" "Patient/1"]})
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body))))
            (given (:resource (first (:entry body)))
              :fhir/type := :fhir/Patient
              :id := "1"))))

      (testing "following the second next link"
        (let [{:keys [body]}
              @(handler
                {::reitit/match page-match
                 :path-params
                 (page-path-params
                  page-id-cipher
                  {"_tag" ["system-190657|code-190711"] "_count" "1" "__t" "1"
                   "__page-type" "Patient" "__page-id" "2" "__page-id-stack" ["" "Patient/1"]})})]

          (testing "has a previous link pointing to the second page"
            (is (= (page-url page-id-cipher {"_tag" ["system-190657|code-190711"] "_count" "1" "__t" "1"
                                             "__page-type" "Patient" "__page-id" "1" "__page-id-stack" [""]})
                   (link-url body "previous"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body))))
            (given (:resource (first (:entry body)))
              :fhir/type := :fhir/Patient
              :id := "2"))))))

  (testing "_sort search parameter is unsupported"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:params {"_sort" "_lastUpdated"}})]

        (is (= 422 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Sort clauses aren't supported in system-level queries."))))

  (testing "Include Resources"
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
            [:issue 0 :diagnostics] := #fhir/string "Missing search parameter code in _include search parameter with source type `Observation`."))))

    (testing "_include"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :meta #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "system-190657" :code #fhir/code "code-190711"}]}
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {:params {"_tag" "system-190657|code-190711"
                          "_include" "Observation:subject"}})]

          (is (= 200 status))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link containing the _include search parameter"
            (is (= (str base-url "?_tag=system-190657%7Ccode-190711&_include=Observation%3Asubject&_count=50")
                   (link-url body "self"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched Observation"
            (given first-entry
              [:fullUrl :value] := (str base-url "/Observation/0")
              [:resource :fhir/type] := :fhir/Observation
              [:search :mode] := #fhir/code "match"))

          (testing "the second entry is the included Patient"
            (given second-entry
              [:fullUrl :value] := (str base-url "/Patient/0")
              [:resource :fhir/type] := :fhir/Patient
              [:search :mode] := #fhir/code "include")))))

    (testing "_revinclude"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :meta #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "system-190657" :code #fhir/code "code-190711"}]}}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {:params {"_tag" "system-190657|code-190711"
                          "_revinclude" "Observation:subject"}})]

          (is (= 200 status))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link containing the _revinclude search parameter"
            (is (= (str base-url "?_tag=system-190657%7Ccode-190711&_revinclude=Observation%3Asubject&_count=50")
                   (link-url body "self"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry is the matched Patient"
            (given first-entry
              [:fullUrl :value] := (str base-url "/Patient/0")
              [:resource :fhir/type] := :fhir/Patient
              [:search :mode] := #fhir/code "match"))

          (testing "the second entry is the included Observation"
            (given second-entry
              [:fullUrl :value] := (str base-url "/Observation/0")
              [:resource :fhir/type] := :fhir/Observation
              [:search :mode] := #fhir/code "include")))))

    (testing "_include without search clauses"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [{:keys [status body]}
              @(handler {:params {"_include" "Observation:subject"}})]

          (is (= 200 status))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains only two entries because Patient/0 is
                    already a match and is therefore removed from the includes"
            (is (= 2 (count (:entry body)))))

          (testing "Patient/0 appears exactly once and as a match"
            (given (filterv (comp #{(str base-url "/Patient/0")} :value :fullUrl)
                            (:entry body))
              count := 1
              [0 :resource :fhir/type] := :fhir/Patient
              [0 :search :mode] := #fhir/code "match"))))))

  (testing "missing resource contents"
    (with-redefs [rc/get (fn [_ _] (ac/completed-future nil))]
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

(deftest handler-execute-query-anomaly-test
  (testing "an anomaly returned by d/execute-query is propagated as an error response"
    (with-redefs [d/execute-query (fn [& _] (ba/fault "msg-104114"))]
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [{:keys [status body]}
              @(handler {:params {"_id" "0"}})]

          (is (= 500 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :diagnostics] := #fhir/string "msg-104114"))))))
