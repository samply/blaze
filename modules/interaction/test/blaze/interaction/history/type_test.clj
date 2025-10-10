(ns blaze.interaction.history.type-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-cache :as rc]
   [blaze.db.tx-log :as-alias tx-log]
   [blaze.fhir.test-util :refer [link-url]]
   [blaze.interaction.history.type]
   [blaze.interaction.history.util-spec]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util-spec]
   [blaze.interaction.test-util :refer [coding v3-ObservationValue wrap-error]]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.page-id-cipher.spec]
   [blaze.test-util :as tu]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-url "base-url-144600")
(def context-path "/context-path-182518")

(def router
  (reitit/router
   [["/Patient"
     {:fhir.resource/type "Patient"
      :name :Patient/type}]
    ["/Patient/_history"
     {:fhir.resource/type "Patient"
      :name :Patient/history}]
    ["/Patient/__history-page/{page-id}"
     {:fhir.resource/type "Patient"
      :name :Patient/history-page}]
    ["/CodeSystem"
     {:fhir.resource/type "CodeSystem"
      :name :CodeSystem/type}]
    ["/CodeSystem/_history"
     {:fhir.resource/type "CodeSystem"
      :name :CodeSystem/history}]]
   {:syntax :bracket
    :path context-path}))

(def default-match
  (reitit/map->Match
   {:data
    {:blaze/base-url ""
     :fhir.resource/type "Patient"
     :name :Patient/history}
    :path (str context-path "/Patient/_history")}))

(def page-match
  (reitit/map->Match
   {:data
    {:blaze/base-url ""
     :fhir.resource/type "Patient"
     :name :Patient/history-page}
    :path (str context-path "/Patient/__history-page")}))

(def code-system-match
  (reitit/map->Match
   {:data
    {:blaze/base-url ""
     :fhir.resource/type "CodeSystem"
     :name :CodeSystem/history}
    :path (str context-path "/CodeSystem/_history")}))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.interaction.history/type
   {::search-util/link (ig/ref ::search-util/link)
    :node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   ::search-util/link {:fhir/version "4.0.1"}
   :blaze.test/fixed-rng-fn {}
   :blaze.test/page-id-cipher {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction.history/type nil}
      :key := :blaze.interaction.history/type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction.history/type {}}
      :key := :blaze.interaction.history/type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% ::search-util/link))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "invalid link function"
    (given-failed-system (assoc-in config [:blaze.interaction.history/type ::search-util/link] ::invalid)
      :key := :blaze.interaction.history/type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::search-util/link]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.interaction.history/type :clock] ::invalid)
      :key := :blaze.interaction.history/type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.interaction.history/type :rng-fn] ::invalid)
      :key := :blaze.interaction.history/type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid page-id-cipher"
    (given-failed-system (assoc-in config [:blaze.interaction.history/type :page-id-cipher] ::invalid)
      :key := :blaze.interaction.history/type
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-id-cipher]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def system-clock-config
  (-> (assoc config :blaze.test/system-clock {})
      (assoc-in [::tx-log/local :clock] (ig/ref :blaze.test/system-clock))))

(defn- wrap-defaults [handler]
  (fn [{::reitit/keys [match] :as request}]
    (handler
     (cond-> (assoc request
                    :blaze/base-url base-url
                    ::reitit/router router)
       (nil? match)
       (assoc ::reitit/match default-match)))))

(defn- wrap-db [handler node page-id-cipher]
  (fn [{::reitit/keys [match] :as request}]
    (if (= page-match match)
      ((decrypt-page-id/wrap-decrypt-page-id
        (db/wrap-snapshot-db handler node 100)
        page-id-cipher)
       request)
      ((db/wrap-db handler node 100) request))))

(defn- wrap-middleware [handler node page-id-cipher]
  (-> handler
      wrap-defaults
      (wrap-db node page-id-cipher)
      wrap-error))

(defmacro with-handler [[handler-binding & [node-binding page-id-cipher-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         page-id-cipher# :blaze.test/page-id-cipher
                         handler# :blaze.interaction.history/type} config]
       ~txs
       (let [~handler-binding (wrap-middleware handler# node# page-id-cipher#)
             ~(or node-binding '_) node#
             ~(or page-id-cipher-binding '_) page-id-cipher#]
         ~@body))))

(defn- page-url [page-id-cipher query-params]
  (str base-url context-path "/Patient/__history-page/" (decrypt-page-id/encrypt page-id-cipher query-params)))

(defn- page-path-params [page-id-cipher params]
  {:page-id (decrypt-page-id/encrypt page-id-cipher params)})

(deftest handler-test
  (testing "with empty node"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is history"
          (is (= #fhir/code "history" (:type body))))

        (testing "the total count is zero"
          (is (= #fhir/unsignedInt 0 (:total body))))

        (is (empty? (:entry body))))))

  (testing "with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler {})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is history"
          (is (= #fhir/code "history" (:type body))))

        (testing "the total count is 1"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "has self link"
          (is (= (str base-url context-path "/Patient/_history")
                 (link-url body "self"))))

        (testing "has no next link"
          (is (nil? (link-url body "next"))))

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
            [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z"))

        (testing "the second entry has the right request"
          (given (:request first-entry)
            :method := #fhir/code "PUT"
            :url := #fhir/uri "Patient/0"))

        (testing "the entry has the right response"
          (given (:response first-entry)
            :status := #fhir/string "201"
            :etag := #fhir/string "W/\"1\""
            :lastModified := #fhir/instant #system/date-time "1970-01-01T00:00:00Z")))))

  (testing "with one code system"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri "system-115910"
               :version #fhir/string "version-170327"
               :content #fhir/code "complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-115927"}]}]]]

      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler {::reitit/match code-system-match})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is history"
          (is (= #fhir/code "history" (:type body))))

        (testing "the total count is 1"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "has self link"
          (is (= (str base-url context-path "/CodeSystem/_history")
                 (link-url body "self"))))

        (testing "has no next link"
          (is (nil? (link-url body "next"))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= (str base-url context-path "/CodeSystem/0")
                 (-> first-entry :fullUrl :value))))

        (testing "the entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/CodeSystem
            :id := "0"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z"
            [:concept 0 :code] := #fhir/code "code-115927"))

        (testing "the second entry has the right request"
          (given (:request first-entry)
            :method := #fhir/code "PUT"
            :url := #fhir/uri "CodeSystem/0"))

        (testing "the entry has the right response"
          (given (:response first-entry)
            :status := #fhir/string "201"
            :etag := #fhir/string "W/\"1\""
            :lastModified := #fhir/instant #system/date-time "1970-01-01T00:00:00Z")))

      (testing "in summary mode"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler {::reitit/match code-system-match
                         :params {"_summary" "true"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is history"
            (is (= #fhir/code "history" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has self link"
            (is (= (str base-url context-path "/CodeSystem/_history")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/CodeSystem/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/CodeSystem
              :id := "0"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z"
              [:meta :tag (coding v3-ObservationValue) 0 :code] := #fhir/code "SUBSETTED"
              :concept := nil))

          (testing "the second entry has the right request"
            (given (:request first-entry)
              :method := #fhir/code "PUT"
              :url := #fhir/uri "CodeSystem/0"))

          (testing "the entry has the right response"
            (given (:response first-entry)
              :status := #fhir/string "201"
              :etag := #fhir/string "W/\"1\""
              :lastModified := #fhir/instant #system/date-time "1970-01-01T00:00:00Z"))))))

  (testing "with two patients in one transaction"
    (with-handler [handler node page-id-cipher]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler {:params {"_count" "1"}})]

        (is (= 200 status))

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "has a self link"
          (is (= (str base-url context-path "/Patient/_history?_count=1")
                 (link-url body "self"))))

        (testing "has a next link"
          (is (= (page-url page-id-cipher {"_count" "1" "__t" "1" "__page-t" "1" "__page-id" "1"})
                 (link-url body "next"))))

        (testing "the entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (-> first-entry :fullUrl :value)))))

      (testing "calling the second page"
        (testing "updating the patient will not affect the second page"
          @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]))

        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match page-match
                 :path-params
                 (page-path-params
                  page-id-cipher
                  {"_count" "1" "__t" "1" "__page-t" "1"
                   "__page-type" "Patient" "__page-id" "1"})})]

          (is (= 200 status))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient/_history?_count=1")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/1")
                   (-> first-entry :fullUrl :value))))))))

  (testing "with two versions, using since"
    (with-system-data [{:blaze.db/keys [node]
                        :blaze.test/keys [system-clock page-id-cipher]
                        handler :blaze.interaction.history/type}
                       system-clock-config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]]]

      (Thread/sleep 2000)
      (let [after-init (time/instant system-clock)
            handler (wrap-middleware handler node page-id-cipher)]

        (Thread/sleep 2000)
        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"
                                  :gender #fhir/code "female"}]])

        (testing "since after initialization"
          (let [{:keys [status] {[first-entry] :entry :as body} :body}
                @(handler
                  {:params {"_since" (str after-init)}})]

            (is (= 200 status))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "it shows the second version"
              (given (:resource first-entry)
                :gender := #fhir/code "female")))))))

  (testing "missing resource contents"
    (with-redefs [rc/multi-get (fn [_ _] (ac/completed-future {}))]
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
