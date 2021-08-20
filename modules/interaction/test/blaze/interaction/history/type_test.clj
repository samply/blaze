(ns blaze.interaction.history.type-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.interaction.history.type]
    [blaze.interaction.history.util-spec]
    [blaze.middleware.fhir.db :refer [wrap-db]]
    [blaze.middleware.fhir.db-spec]
    [blaze.test-util :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [java-time :as time]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def base-url "base-url-144600")


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]]
    {:syntax :bracket}))


(def match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""
    :fhir.resource/type "Patient"}
   :path "/Patient/_history"})


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


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


(def system
  (assoc mem-node-system
    :blaze.interaction.history/type
    {:node (ig/ref :blaze.db/node)
     :clock (ig/ref :blaze.test/clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
    :blaze.test/fixed-rng-fn {}))


(defn wrap-defaults [handler]
  (fn [request]
    (handler
      (assoc request
        :blaze/base-url base-url
        ::reitit/router router
        ::reitit/match match))))


(defmacro with-handler [[handler-binding] txs & body]
  `(with-system-data [{node# :blaze.db/node
                       handler# :blaze.interaction.history/type} system]
     ~txs
     (let [~handler-binding (-> handler# wrap-defaults (wrap-db node#))]
       ~@body)))


(deftest handler-test
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

        (is (= #fhir/code"history" (:type body)))

        (is (= #fhir/unsignedInt 1 (:total body)))

        (testing "has self link"
          (is (= #fhir/uri"base-url-144600/Patient/_history?__t=1&__page-t=1&__page-id=0"
                 (link-url body "self"))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (given (-> body :entry first)
          :fullUrl := #fhir/uri"base-url-144600/Patient/0"
          [:request :method] := #fhir/code"PUT"
          [:request :url] := #fhir/uri"/Patient/0"
          [:resource :id] := "0"
          [:resource :fhir/type] := :fhir/Patient
          [:resource :meta :versionId] := #fhir/id"1"
          [:response :status] := "201"
          [:response :etag] := "W/\"1\""
          [:response :lastModified] := Instant/EPOCH)))))
