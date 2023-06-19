(ns blaze.interaction.search-system-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.db.resource-store :as rs]
    [blaze.interaction.search-system]
    [blaze.interaction.search.nav-spec]
    [blaze.interaction.search.params-spec]
    [blaze.interaction.search.util-spec]
    [blaze.interaction.test-util :refer [wrap-error]]
    [blaze.middleware.fhir.db :as db]
    [blaze.middleware.fhir.db-spec]
    [blaze.page-store-spec]
    [blaze.page-store.local]
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


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def base-url "base-url-114650")


(def router
  (reitit/router
    [["/__page" {:name :page}]
     ["/Patient" {:name :Patient/type}]]
    {:syntax :bracket}))


(def match
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
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :page-store))))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze.interaction/search-system {:clock ::invalid}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :page-store))
      [:explain ::s/problems 2 :pred] := `time/clock?
      [:explain ::s/problems 2 :val] := ::invalid)))


(def system
  (assoc mem-node-system
    :blaze.interaction/search-system
    {:node (ig/ref :blaze.db/node)
     :clock (ig/ref :blaze.test/fixed-clock)
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
        ::reitit/router router
        ::reitit/match match))))


(defn wrap-db [handler node]
  (fn [{::reitit/keys [match] :as request}]
    (if (= page-match match)
      ((db/wrap-snapshot-db handler node 100) request)
      ((db/wrap-search-db handler node 100) request))))


(defmacro with-handler [[handler-binding & [node-binding]] & more]
  (let [[txs body] (tu/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.interaction/search-system} system]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node#)
                                  wrap-error)
             ~(or node-binding '_) node#]
         ~@body))))


(deftest handler-test
  (testing "on empty database"
    (with-handler [handler]
      (testing "Returns all existing resources"
        (let [{:keys [status body]}
              @(handler {::reitit/match match})]

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
            (is (= (str base-url "?_count=50&__t=0")
                   (link-url body "self"))))

          (testing "the bundle contains no entry"
            (is (zero? (count (:entry body)))))))))

  (testing "with one patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "Returns all existing resources"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
              @(handler {::reitit/match match})]

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
            (is (= (str base-url "?_count=50&__t=1&__page-type=Patient&__page-id=0")
                   (link-url body "self"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url "/Patient/0")
                   (:fullUrl first-entry))))

          (testing "the entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id"1"
              [:meta :lastUpdated] := Instant/EPOCH))))

      (testing "with param _summary equal to count"
        (let [{:keys [status body]}
              @(handler {::reitit/match match
                         :params {"_summary" "count"}})]

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
            (is (= (str base-url "?_summary=count&_count=50&__t=1")
                   (link-url body "self"))))

          (testing "the bundle contains no entry"
            (is (empty? (:entry body))))))

      (testing "with param _count equal to zero"
        (let [{:keys [status body]}
              @(handler {::reitit/match match
                         :params {"_count" "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "has a self link"
            (is (= (str base-url "?_count=0&__t=1") (link-url body "self"))))

          (testing "the bundle contains no entry"
            (is (empty? (:entry body))))))))

  (testing "with two patients"
    (with-handler [handler node]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (testing "search for all patients with _count=1"
        (let [{:keys [body]}
              @(handler {::reitit/match match
                         :params {"_count" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url "?_count=1&__t=1&__page-type=Patient&__page-id=0")
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= (str base-url "/__page?_count=1&__t=1&__page-type=Patient&__page-id=1")
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the self link"
        (let [{:keys [body]}
              @(handler
                 {::reitit/match match
                  :params {"_count" "1" "__t" "1" "__page-type" "Patient"
                           "__page-id" "0"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url "?_count=1&__t=1&__page-type=Patient&__page-id=0")
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= (str base-url "/__page?_count=1&__t=1&__page-type=Patient&__page-id=1")
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @(handler
                 {::reitit/match page-match
                  :params {"_count" "1" "__t" "1" "__page-type" "Patient"
                           "__page-id" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= (str base-url "?_count=1&__t=1&__page-type=Patient&__page-id=1")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "adding a third patient doesn't influence the paging"
        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "2"}]])

        (testing "following the self link"
          (let [{:keys [body]}
                @(handler
                   {::reitit/match match
                    :params {"_count" "1" "__t" "1" "__page-type" "Patient"
                             "__page-id" "0"}})]

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has a self link"
              (is (= (str base-url "?_count=1&__t=1&__page-type=Patient&__page-id=0")
                     (link-url body "self"))))

            (testing "has a next link"
              (is (= (str base-url "/__page?_count=1&__t=1&__page-type=Patient&__page-id=1")
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))

        (testing "following the next link"
          (let [{:keys [body]}
                @(handler
                   {::reitit/match page-match
                    :params {"_count" "1" "__t" "1" "__page-type" "Patient"
                             "__page-id" "1"}})]

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has a self link"
              (is (= (str base-url "?_count=1&__t=1&__page-type=Patient&__page-id=1")
                     (link-url body "self"))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))))

  (testing "Include Resources"
    (testing "invalid include parameter"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match match
                  :headers {"prefer" "handling=strict"}
                  :params {"_include" "Observation"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Missing search parameter code in _include search parameter with source type `Observation`.")))))

  (testing "missing resource contents"
    (with-redefs [rs/multi-get (fn [_ _] (ac/completed-future {}))]
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [{:keys [status body]}
              @(handler {::reitit/match match})]

          (is (= 500 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"incomplete"
            [:issue 0 :diagnostics] := "The resource content of `Patient/0` with hash `C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F` was not found."))))))
