(ns blaze.interaction.history.type-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-store :as rs]
   [blaze.db.tx-log :as-alias tx-log]
   [blaze.fhir.test-util :refer [link-url]]
   [blaze.interaction.history.type]
   [blaze.interaction.history.util-spec]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.middleware.fhir.db-spec]
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
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-url "base-url-144600")
(def context-path "/context-path-182518")

(def router
  (reitit/router
   [["/Patient" {:name :Patient/type}]]
   {:syntax :bracket
    :path context-path}))

(def match
  (reitit/map->Match
   {:data
    {:blaze/base-url ""
     :fhir.resource/type "Patient"}
    :path (str context-path "/Patient/_history")}))

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction.history/type nil})
      :key := :blaze.interaction.history/type
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction.history/type {}})
      :key := :blaze.interaction.history/type
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze.interaction.history/type {:clock ::invalid}})
      :key := :blaze.interaction.history/type
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 1 :pred] := `time/clock?
      [:explain ::s/problems 1 :val] := ::invalid)))

(def config
  (assoc api-stub/mem-node-config
         :blaze.interaction.history/type
         {:node (ig/ref :blaze.db/node)
          :clock (ig/ref :blaze.test/fixed-clock)
          :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
         :blaze.test/fixed-rng-fn {}))

(def system-clock-config
  (-> (assoc config :blaze.test/system-clock {})
      (assoc-in [::tx-log/local :clock] (ig/ref :blaze.test/system-clock))))

(defn wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router
            ::reitit/match match))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.interaction.history/type} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node# 100)
                                  wrap-error)]
         ~@body))))

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
          (is (= #fhir/code"history" (:type body))))

        (is (= #fhir/unsignedInt 0 (:total body)))

        (is (empty? (:entry body))))))

  (testing "with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status body]}
            @(handler {})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is history"
          (is (= #fhir/code"history" (:type body))))

        (is (= #fhir/unsignedInt 1 (:total body)))

        (testing "has self link"
          (is (= (str base-url context-path "/Patient/_history?__t=1&__page-t=1&__page-id=0")
                 (link-url body "self"))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (given (-> body :entry first)
          :fullUrl := (str base-url context-path "/Patient/0")
          [:request :method] := #fhir/code"PUT"
          [:request :url] := "Patient/0"
          [:resource :id] := "0"
          [:resource :fhir/type] := :fhir/Patient
          [:resource :meta :versionId] := #fhir/id"1"
          [:response :status] := "201"
          [:response :etag] := "W/\"1\""
          [:response :lastModified] := Instant/EPOCH))))

  (testing "with two versions, using since"
    (with-system-data [{:blaze.db/keys [node] :blaze.test/keys [system-clock]
                        handler :blaze.interaction.history/type}
                       system-clock-config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]]]

      (Thread/sleep 2000)
      (let [since (Instant/now system-clock)
            _ (Thread/sleep 2000)
            _ @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"
                                        :gender #fhir/code"female"}]])
            handler (-> handler wrap-defaults (wrap-db node 100) wrap-error)
            {:keys [body]}
            @(handler
              {:query-params {"_since" (str since)}})]

        (testing "the total count is one"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "it shows the second version"
          (given (-> body :entry first)
            [:resource :gender] := #fhir/code"female")))))

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
