(ns blaze.interaction.search-system-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.interaction.search-system]
    [blaze.interaction.search.nav-spec]
    [blaze.interaction.search.params-spec]
    [blaze.middleware.fhir.db :refer [wrap-db]]
    [blaze.middleware.fhir.db-spec]
    [blaze.middleware.fhir.error :refer [wrap-error]]
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


(def base-url "base-url-114650")


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]]
    {:syntax :bracket}))


(def match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""}
   :path ""})


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/search-system nil})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/search-system {}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze.interaction/search-system {:clock ::invalid}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 1 :pred] := `time/clock?
      [:explain ::s/problems 1 :val] := ::invalid)))


(def system
  (assoc mem-node-system
    :blaze.interaction/search-system
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
                       handler# :blaze.interaction/search-system} system]
     ~txs
     (let [~handler-binding (-> handler# wrap-defaults (wrap-db node#)
                                wrap-error)]
       ~@body)))


(deftest handler-test
  (testing "on empty database"
    (with-handler [handler]
      []
      (testing "Returns all existing resources"
        (let [{:keys [status body]}
              @(handler {})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is zero"
            (is (= #fhir/unsignedInt 0 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_count=50&__t=0"
                   (link-url body "self"))))

          (testing "the bundle contains no entry"
            (is (zero? (count (:entry body)))))))))

  (testing "with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "Returns all existing resources"
        (let [{:keys [status body]}
              @(handler {})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_count=50&__t=1&__page-type=Patient&__page-id=0"
                   (link-url body "self"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= #fhir/uri"base-url-114650/Patient/0"
                   (-> body :entry first :fullUrl))))

          (testing "the entry has the right resource"
            (given (-> body :entry first :resource)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id"1"
              [:meta :lastUpdated] := Instant/EPOCH))))

      (testing "with param _summary equal to count"
        (let [{:keys [status body]}
              @(handler {:params {"_summary" "count"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_summary=count&_count=50&__t=1"
                   (link-url body "self"))))

          (testing "the bundle contains no entries"
            (is (empty? (:entry body))))))

      (testing "with param _count equal to zero"
        (let [{:keys [status body]}
              @(handler {:params {"_count" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_count=0&__t=1" (link-url body "self"))))

          (testing "the bundle contains no entries"
            (is (empty? (:entry body))))))))

  (testing "with two patients"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "search for all patients with _count=1"
        (let [{:keys [body]}
              @(handler {:params {"_count" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=1"
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the self link"
        (let [{:keys [body]}
              @(handler
                 {:params {"_count" "1" "__t" "1" "__page-type" "Patient"
                           "__page-id" "0"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=1"
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @(handler
                 {:params {"_count" "1" "__t" "1" "__page-type" "Patient"
                           "__page-id" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=1"
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))))

  (testing "Include Resources"
    (testing "invalid include parameter"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                 {:headers {"prefer" "handling=strict"}
                  :params {"_include" "Observation"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Missing search parameter code in _include search parameter with source type `Observation`."))))))
