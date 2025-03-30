(ns blaze.interaction.history.instance-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
   [blaze.anomaly-spec]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-store :as rs]
   [blaze.db.tx-log :as-alias tx-log]
   [blaze.fhir.test-util :refer [link-url]]
   [blaze.interaction.history.instance]
   [blaze.interaction.history.util-spec]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.page-id-cipher.spec :refer [page-id-cipher?]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-url "base-url-135814")
(def context-path "/context-path-181842")

(def router
  (reitit/router
   [["/Patient"
     {:fhir.resource/type "Patient"
      :name :Patient/type}]
    ["/Patient/{id}/_history"
     {:fhir.resource/type "Patient"
      :name :Patient/history-instance}]
    ["/Patient/{id}/__history-page/{page-id}"
     {:fhir.resource/type "Patient"
      :name :Patient/history-instance-page}]]
   {:syntax :bracket
    :path context-path}))

(def default-match
  (reitit/map->Match
   {:data
    {:blaze/base-url ""
     :name :Patient/history-instance
     :fhir.resource/type "Patient"}
    :path (str context-path "/Patient/0/_history")}))

(def page-match
  (reitit/map->Match
   {:data
    {:blaze/base-url ""
     :name :Patient/history-instance-page
     :fhir.resource/type "Patient"}
    :path (str context-path "/Patient/0/_history/__page")}))

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction.history/instance nil})
      :key := :blaze.interaction.history/instance
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction.history/instance {}})
      :key := :blaze.interaction.history/instance
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze.interaction.history/instance {:clock ::invalid}})
      :key := :blaze.interaction.history/instance
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))
      [:cause-data ::s/problems 2 :pred] := `time/clock?
      [:cause-data ::s/problems 2 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-thrown (ig/init {:blaze.interaction.history/instance {:rng-fn ::invalid}})
      :key := :blaze.interaction.history/instance
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))
      [:cause-data ::s/problems 2 :pred] := `fn?
      [:cause-data ::s/problems 2 :val] := ::invalid))

  (testing "invalid page-id-cipher"
    (given-thrown (ig/init {:blaze.interaction.history/instance {:page-id-cipher ::invalid}})
      :key := :blaze.interaction.history/instance
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 2 :pred] := `page-id-cipher?
      [:cause-data ::s/problems 2 :val] := ::invalid)))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.interaction.history/instance
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   :blaze.test/fixed-rng-fn {}
   :blaze.test/page-id-cipher {}))

(def system-clock-config
  (-> (assoc config :blaze.test/system-clock {})
      (assoc-in [::tx-log/local :clock] (ig/ref :blaze.test/system-clock))))

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
                         handler# :blaze.interaction.history/instance} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults
                                  (wrap-db node# page-id-cipher#)
                                  wrap-error)
             ~(or node-binding '_) node#
             ~(or page-id-cipher-binding '_) page-id-cipher#]
         ~@body))))

(defn- page-url [page-id-cipher query-params]
  (str base-url context-path "/Patient/0/__history-page/" (decrypt-page-id/encrypt page-id-cipher query-params)))

(defn- page-path-params [page-id-cipher params]
  {:id "0" :page-id (decrypt-page-id/encrypt page-id-cipher params)})

(deftest handler-test
  (testing "returns not found on empty node"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"not-found"))))

  (testing "returns history with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is history"
          (is (= #fhir/code"history" (:type body))))

        (testing "the total count is 1"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "has a self link"
          (is (= (str base-url context-path "/Patient/0/_history")
                 (link-url body "self"))))

        (testing "has no next link"
          (is (nil? (link-url body "next"))))

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

        (testing "the second entry has the right request"
          (given (:request first-entry)
            :method := #fhir/code"PUT"
            :url := "Patient/0"))

        (testing "the entry has the right response"
          (given (:response first-entry)
            :status := "201"
            :etag := "W/\"1\""
            :lastModified := Instant/EPOCH)))))

  (testing "returns history with one currently deleted patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
            @(handler
              {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is history"
          (is (= #fhir/code"history" (:type body))))

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "has a self link"
          (is (= (str base-url context-path "/Patient/0/_history")
                 (link-url body "self"))))

        (testing "has no next link"
          (is (nil? (link-url body "next"))))

        (testing "the bundle contains two entries"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (:fullUrl first-entry))))

        (testing "the first entry has no resource"
          (is (nil? (:resource first-entry))))

        (testing "the first entry has the right request"
          (given (:request first-entry)
            :method := #fhir/code"DELETE"
            :url := "Patient/0"))

        (testing "the first entry has the right response"
          (given (:response first-entry)
            :status := "204"
            :etag := "W/\"2\""
            :lastModified := Instant/EPOCH))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (:fullUrl second-entry))))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the second entry has the right request"
          (given (:request second-entry)
            :method := #fhir/code"PUT"
            :url := "Patient/0"))

        (testing "the second entry has the right response"
          (given (:response second-entry)
            :status := "201"
            :etag := "W/\"1\""
            :lastModified := Instant/EPOCH)))))

  (testing "with two versions of one patient"
    (with-handler [handler node page-id-cipher]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"female"}]]]

      (let [{:keys [body]}
            @(handler
              {:path-params {:id "0"}
               :params {"_count" "1"}})]

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "has a self link"
          (is (= (str base-url context-path "/Patient/0/_history?_count=1")
                 (link-url body "self"))))

        (testing "has a next link"
          (is (= (page-url page-id-cipher {"_count" "1" "__t" "2" "__page-t" "1"})
                 (link-url body "next")))))

      (testing "calling the second page"

        (testing "updating the patient will not affect the second page"
          @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0" :active true}]]))

        (let [{{[first-entry] :entry :as body} :body}
              @(handler
                {::reitit/match page-match
                 :path-params
                 (page-path-params
                  page-id-cipher
                  {"_count" "1" "__t" "2" "__page-t" "1"})})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url context-path "/Patient/0/_history?_count=1")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :gender := #fhir/code"male"))))))

  (testing "with two versions, using since"
    (with-system-data [{:blaze.db/keys [node]
                        :blaze.test/keys [system-clock page-id-cipher]
                        handler :blaze.interaction.history/instance}
                       system-clock-config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]]]

      (Thread/sleep 2000)
      (let [since (time/instant system-clock)
            _ (Thread/sleep 2000)
            _ @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"
                                        :gender #fhir/code"female"}]])
            handler (-> handler wrap-defaults (wrap-db node page-id-cipher) wrap-error)
            {:keys [body]}
            @(handler
              {:path-params {:id "0"}
               :params {"_since" (str since)}})]

        (testing "the total count is 1"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "it shows the second version"
          (given (-> body :entry first)
            [:resource :gender] := #fhir/code"female")))))

  (testing "missing resource contents"
    (with-redefs [rs/multi-get (fn [_ _ _] (ac/completed-future {}))]
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [{:keys [status body]}
              @(handler {:path-params {:id "0"}})]

          (is (= 500 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"incomplete"
            [:issue 0 :diagnostics] := "The resource content of `Patient/0` with hash `C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F` was not found."))))))
