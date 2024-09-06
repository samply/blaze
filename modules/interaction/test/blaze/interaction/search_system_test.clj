(ns blaze.interaction.search-system-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-store :as rs]
   [blaze.fhir.test-util :refer [link-url]]
   [blaze.interaction.search-system]
   [blaze.interaction.search.nav-spec]
   [blaze.interaction.search.params-spec]
   [blaze.interaction.search.util-spec]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.page-id-cipher.spec :refer [page-id-cipher?]]
   [blaze.page-store-spec]
   [blaze.page-store.local]
   [blaze.page-store.spec :refer [page-store?]]
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
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-url "base-url-114650")

(def router
  (reitit/router
   [["/__page/{page-id}" {:name :page}]
    ["/Patient" {:name :Patient/type}]]
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

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/search-system nil})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/search-system {}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :page-store))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze.interaction/search-system {:clock ::invalid}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :page-store))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))
      [:cause-data ::s/problems 3 :pred] := `time/clock?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-thrown (ig/init {:blaze.interaction/search-system {:rng-fn ::invalid}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :page-store))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))
      [:cause-data ::s/problems 3 :pred] := `fn?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid page-store"
    (given-thrown (ig/init {:blaze.interaction/search-system {:page-store ::invalid}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))
      [:cause-data ::s/problems 3 :pred] := `page-store?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid page-id-cipher"
    (given-thrown (ig/init {:blaze.interaction/search-system {:page-id-cipher ::invalid}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :page-store))
      [:cause-data ::s/problems 3 :pred] := `page-id-cipher?
      [:cause-data ::s/problems 3 :val] := ::invalid)))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.interaction/search-system
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref :blaze.page-store/local)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   :blaze.test/fixed-rng-fn {}
   :blaze.page-store/local {:secure-rng (ig/ref :blaze.test/fixed-rng)}
   :blaze.test/fixed-rng {}
   :blaze.test/page-id-cipher {}))

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
            (is (= #fhir/code"searchset" (:type body))))

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
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "Returns all existing resources"
        (let [{:keys [status] {[first-entry] :entry :as body} :body}
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
            (is (= (str base-url "?_count=50")
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

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
            (is (= #fhir/code"searchset" (:type body))))

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

          (testing "has a next link"
            (is (= (page-url page-id-cipher {"_count" "1" "__t" "1" "__page-type" "Patient" "__page-id" "1"})
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
                   "__page-id" "1"})})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has no self link"
            (is (nil? (link-url body "self"))))

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
                     "__page-id" "1"})})]

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has no self link"
              (is (nil? (link-url body "self"))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body))))))))))

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
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Missing search parameter code in _include search parameter with source type `Observation`.")))))

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
