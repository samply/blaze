(ns blaze.interaction.history.instance-test
  "Specifications relevant for the FHIR history interaction:

  https://www.hl7.org/fhir/http.html#history
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.anomaly-spec]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.fhir.structure-definition-repo]
    [blaze.interaction.history.instance]
    [blaze.interaction.history.util-spec]
    [blaze.middleware.fhir.db :refer [wrap-db]]
    [blaze.middleware.fhir.db-spec]
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [blaze.test-util :as tu :refer [given-thrown]]
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
(tu/init-fhir-specs)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def base-url "base-url-135814")


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]]
    {:syntax :bracket}))


(def match
  (reitit/map->Match
    {:data
     {:blaze/base-url ""
      :fhir.resource/type "Patient"}
     :path "/Patient/0/_history"}))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction.history/instance nil})
      :key := :blaze.interaction.history/instance
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction.history/instance {}})
      :key := :blaze.interaction.history/instance
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze.interaction.history/instance {:clock ::invalid}})
      :key := :blaze.interaction.history/instance
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 1 :pred] := `time/clock?
      [:explain ::s/problems 1 :val] := ::invalid)))


(def system
  (assoc mem-node-system
    :blaze.interaction.history/instance
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
                       handler# :blaze.interaction.history/instance} system]
     ~txs
     (let [~handler-binding (-> handler# wrap-defaults (wrap-db node#)
                                wrap-error)]
       ~@body)))


(deftest handler-test
  (testing "returns not found on empty node"
    (with-handler [handler]
      []
      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-found"))))

  (testing "returns history with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (is (= #fhir/code "history" (:type body)))

        (is (= #fhir/unsignedInt 1 (:total body)))

        (is (= 1 (count (:entry body))))

        (is (= 1 (count (:link body))))

        (is (= "self" (-> body :link first :relation)))

        (is (= #fhir/uri "base-url-135814/Patient/0/_history?__t=1&__page-t=1"
               (-> body :link first :url)))

        (given (-> body :entry first)
          :fullUrl := #fhir/uri "base-url-135814/Patient/0"
          [:request :method] := #fhir/code "PUT"
          [:request :url] := #fhir/uri "/Patient/0"
          [:resource :id] := "0"
          [:resource :fhir/type] := :fhir/Patient
          [:resource :meta :versionId] := #fhir/id"1"
          [:response :status] := "201"
          [:response :etag] := "W/\"1\""
          [:response :lastModified] := Instant/EPOCH))))

  (testing "returns history with one currently deleted patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active true}]]
       [[:delete "Patient" "0"]]]

      (let [{:keys [status body]}
            @(handler
               {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (is (= #fhir/code "history" (:type body)))

        (is (= #fhir/unsignedInt 2 (:total body)))

        (is (= 2 (count (:entry body))))

        (is (= 1 (count (:link body))))

        (is (= "self" (-> body :link first :relation)))

        (is (= #fhir/uri "base-url-135814/Patient/0/_history?__t=2&__page-t=2"
               (-> body :link first :url)))

        (testing "first entry"
          (given (-> body :entry first)
            :fullUrl := #fhir/uri "base-url-135814/Patient/0"
            [:request :method] := #fhir/code "DELETE"
            [:request :url] := #fhir/uri "/Patient/0"
            keys :!> #{:resource}
            [:response :status] := "204"
            [:response :etag] := "W/\"2\""
            [:response :lastModified] := Instant/EPOCH))

        (testing "second entry"
          (given (-> body :entry second)
            :fullUrl := #fhir/uri "base-url-135814/Patient/0"
            [:request :method] := #fhir/code "PUT"
            [:request :url] := #fhir/uri "/Patient/0"
            [:resource :id] := "0"
            [:resource :fhir/type] := :fhir/Patient
            [:resource :meta :versionId] := #fhir/id"1"
            [:resource :active] := true
            [:response :status] := "201"
            [:response :etag] := "W/\"1\""
            [:response :lastModified] := Instant/EPOCH)))))

  (testing "contains a next link on node with two versions and _count=1"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "female"}]]]

      (let [{:keys [body]}
            @(handler
               {:path-params {:id "0"}
                :query-params {"_count" "1"}})]

        (is (= "next" (-> body :link second :relation)))

        (is (= #fhir/uri "base-url-135814/Patient/0/_history?_count=1&__t=2&__page-t=1"
               (-> body :link second :url))))))

  (testing "with two versions, calling the second page"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "female"}]]]

      (let [{:keys [body]}
            @(handler
               {:path-params {:id "0"}
                :query-params {"_count" "1" "t" "2" "__page-t" "1"}})]

        (testing "the total count is still two"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "is shows the first version"
          (given (-> body :entry first)
            [:resource :gender] := #fhir/code "male"))))))
