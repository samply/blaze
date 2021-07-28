(ns blaze.interaction.search-compartment-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#vsearch"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.fhir.spec.type]
    [blaze.interaction.search-compartment]
    [blaze.interaction.search.nav-spec]
    [blaze.interaction.search.params-spec]
    [blaze.interaction.test-util :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock Instant ZoneId]
    [java.util Random]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))


(def ^:private base-url "base-url-114238")


(defn fixed-random [n]
  (proxy [Random] []
    (nextLong []
      n)))


(def router
  (reitit/router
    [["/Patient/{id}/{type}" {:name :Patient/compartment}]
     ["/Observation" {:name :Observation/type}]]
    {:syntax :bracket}))


(def match
  {:data
   {:blaze/context-path ""
    :fhir.compartment/code "Patient"}
   :path "/Patient/0/Observation"})


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction/search-compartment
         {:node node
          :clock clock
          :rng-fn (fn [] (fixed-random 0))}})
      :blaze.interaction/search-compartment))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node)
        (assoc request
          :blaze/base-url base-url
          ::reitit/router router)))))


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/search-compartment nil})
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/search-compartment {}})
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-thrown (ig/init {:blaze.interaction/search-compartment {:node "foo"}})
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 2 :pred] := `blaze.db.spec/node?
      [:explain ::s/problems 2 :val] := "foo")))


(deftest handler-test
  (testing "Returns an Error on Invalid Id"
    (let [{:keys [status body]}
          ((handler-with [])
           {:path-params {:id "<invalid>" :type "Observation"}
            ::reitit/match match})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :diagnostics] := "The identifier `<invalid>` is invalid.")))

  (testing "Returns an Error on Invalid Type"
    (let [{:keys [status body]}
          ((handler-with [])
           {:path-params {:id "0" :type "<invalid>"}
            ::reitit/match match})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :diagnostics] := "The type `<invalid>` is invalid.")))

  (testing "on unknown search parameter"
    (testing "with strict handling"
      (testing "returns error"
        (testing "normal result"
          (let [{:keys [status body]}
                ((handler-with [])
                 {:path-params {:id "0" :type "Observation"}
                  ::reitit/match match
                  :headers {"prefer" "handling=strict"}
                  :params {"foo" "bar"}})]

            (is (= 404 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-found"
              [:issue 0 :diagnostics] := "The search-param with code `foo` and type `Observation` was not found.")))

        (testing "summary result"
          (let [{:keys [status body]}
                ((handler-with [])
                 {:path-params {:id "0" :type "Observation"}
                  ::reitit/match match
                  :headers {"prefer" "handling=strict"}
                  :params {"foo" "bar" "_summary" "count"}})]

            (is (= 404 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-found"
              [:issue 0 :diagnostics] := "The search-param with code `foo` and type `Observation` was not found.")))))

    (testing "with lenient handling"
      (testing "returns results with a self link lacking the unknown search parameter"
        (testing "where the unknown search parameter is the only one"
          (testing "normal result"
            (let [{:keys [status body]}
                  ((handler-with
                     [[[:put {:fhir/type :fhir/Patient :id "0"}]
                       [:put {:fhir/type :fhir/Observation :id "0"
                              :subject
                              #fhir/Reference{:reference "Patient/0"}}]]])
                   {:path-params {:id "0" :type "Observation"}
                    ::reitit/match match
                    :headers {"prefer" "handling=lenient"}
                    :params {"foo" "bar"}})]

              (is (= 200 status))

              (testing "the body contains a bundle"
                (is (= :fhir/Bundle (:fhir/type body))))

              (testing "the bundle contains an id"
                (is (string? (:id body))))

              (testing "the bundle type is searchset"
                (is (= #fhir/code"searchset" (:type body))))

              (testing "the total count is 1"
                (is (= #fhir/unsignedInt 1 (:total body))))

              (testing "the bundle contains one entry"
                (is (= 1 (count (:entry body)))))

              (testing "has a self link"
                (is (= #fhir/uri"base-url-114238/Patient/0/Observation?_count=50&__t=1&__page-offset=0"
                       (link-url body "self"))))))

          (testing "summary result"
            (let [{:keys [status body]}
                  ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]
                                   [:put {:fhir/type :fhir/Observation :id "0"
                                          :subject
                                          #fhir/Reference
                                              {:reference "Patient/0"}}]]])
                   {:path-params {:id "0" :type "Observation"}
                    ::reitit/match match
                    :headers {"prefer" "handling=lenient"}
                    :params {"foo" "bar" "_summary" "count"}})]

              (is (= 200 status))

              (testing "the body contains a bundle"
                (is (= :fhir/Bundle (:fhir/type body))))

              (testing "the bundle contains an id"
                (is (string? (:id body))))

              (testing "the bundle type is searchset"
                (is (= #fhir/code"searchset" (:type body))))

              (testing "the total count is 1"
                (is (= #fhir/unsignedInt 1 (:total body))))

              (testing "the bundle contains no entries"
                (is (empty? (:entry body))))

              (testing "has a self link"
                (is (= #fhir/uri"base-url-114238/Patient/0/Observation?_summary=count&_count=50&__t=1&__page-offset=0"
                       (link-url body "self")))))))

        (testing "with another search parameter"
          (testing "normal result"
            (let [{:keys [status body]}
                  ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]
                                   [:put {:fhir/type :fhir/Observation :id "0"
                                          :status #fhir/code"final"
                                          :subject
                                          #fhir/Reference
                                              {:reference "Patient/0"}}]
                                   [:put {:fhir/type :fhir/Observation :id "1"
                                          :status #fhir/code"preliminary"
                                          :subject
                                          #fhir/Reference
                                              {:reference "Patient/0"}}]]])
                   {:path-params {:id "0" :type "Observation"}
                    ::reitit/match match
                    :headers {"prefer" "handling=lenient"}
                    :params {"foo" "bar" "status" "preliminary"}})]

              (is (= 200 status))

              (testing "the body contains a bundle"
                (is (= :fhir/Bundle (:fhir/type body))))

              (testing "the bundle type is searchset"
                (is (= #fhir/code"searchset" (:type body))))

              (testing "the total count is 1"
                (is (= #fhir/unsignedInt 1 (:total body))))

              (testing "the bundle contains one entry"
                (is (= 1 (count (:entry body)))))

              (testing "has a self link"
                (is (= #fhir/uri"base-url-114238/Patient/0/Observation?status=preliminary&_count=50&__t=1&__page-offset=0"
                       (link-url body "self"))))))

          (testing "summary result"
            (let [{:keys [status body]}
                  ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]
                                   [:put {:fhir/type :fhir/Observation :id "0"
                                          :status #fhir/code"final"
                                          :subject
                                          #fhir/Reference
                                              {:reference "Patient/0"}}]
                                   [:put {:fhir/type :fhir/Observation :id "1"
                                          :status #fhir/code"preliminary"
                                          :subject
                                          #fhir/Reference
                                              {:reference "Patient/0"}}]]])
                   {:path-params {:id "0" :type "Observation"}
                    ::reitit/match match
                    :headers {"prefer" "handling=lenient"}
                    :params {"foo" "bar" "status" "preliminary" "_summary" "count"}})]

              (is (= 200 status))

              (testing "the body contains a bundle"
                (is (= :fhir/Bundle (:fhir/type body))))

              (testing "the bundle type is searchset"
                (is (= #fhir/code"searchset" (:type body))))

              (testing "the total count is 1"
                (is (= #fhir/unsignedInt 1 (:total body))))

              (testing "the bundle contains no entries"
                (is (empty? (:entry body))))

              (testing "has a self link"
                (is (= #fhir/uri"base-url-114238/Patient/0/Observation?status=preliminary&_summary=count&_count=50&__t=1&__page-offset=0"
                       (link-url body "self"))))))))))

  (testing "Returns an empty Bundle on Non-Existing Compartment"
    (let [{:keys [status body]}
          ((handler-with [])
           {:path-params {:id "0" :type "Observation"}
            ::reitit/match match})]

      (is (= 200 status))

      (given body
        :fhir/type := :fhir/Bundle
        :type := #fhir/code"searchset"
        :total := #fhir/unsignedInt 0)))

  (testing "with one Observation"
    (let [handler
          (handler-with
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Observation :id "0"
                     :status #fhir/code"final"
                     :subject #fhir/Reference{:reference "Patient/0"}}]
              [:put {:fhir/type :fhir/Observation :id "1"
                     :status #fhir/code"preliminary"
                     :subject #fhir/Reference{:reference "Patient/0"}}]]])
          request
          {:path-params {:id "0" :type "Observation"}
           ::reitit/match match}]

      (testing "with _summary=count"
        (let [{:keys [status body]}
              (handler (assoc-in request [:params "_summary"] "count"))]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains no entries"
            (is (empty? (:entry body))))))

      (testing "with _summary=count and status=final"
        (let [{:keys [status body]}
              (handler (-> (assoc-in request [:params "_summary"] "count")
                           (assoc-in [:params "status"] "final")))]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains no entries"
            (is (empty? (:entry body))))))

      (testing "with no query param"
        (let [{:keys [status body]} (handler request)]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114238/Patient/0/Observation?_count=50&__t=1&__page-offset=0"
                   (link-url body "self"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry"
            (given (-> body :entry first)
              :fullUrl := #fhir/uri"base-url-114238/Observation/0"
              [:resource :fhir/type] := :fhir/Observation
              [:resource :id] := "0"))

          (testing "the second entry"
            (given (-> body :entry second)
              :fullUrl := #fhir/uri"base-url-114238/Observation/1"
              [:resource :fhir/type] := :fhir/Observation
              [:resource :id] := "1")))))))
