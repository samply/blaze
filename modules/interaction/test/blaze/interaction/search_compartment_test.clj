(ns blaze.interaction.search-compartment-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#vsearch"
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-store :as rs]
   [blaze.fhir.spec.type]
   [blaze.fhir.test-util :refer [link-url]]
   [blaze.interaction.search-compartment]
   [blaze.interaction.search.nav-spec]
   [blaze.interaction.search.params-spec]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util-spec]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.module-spec]
   [blaze.module.test-util :refer [given-failed-system]]
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

(def base-url "base-url-114238")
(def context-path "/context-path-173854")

(def router
  (reitit/router
   [["/Patient/{id}/{type}" {:name :Patient/compartment}]
    ["/Patient/{id}/{type}/__page/{page-id}" {:name :Patient/compartment-page}]
    ["/Observation" {:name :Observation/type}]]
   {:syntax :bracket
    :path context-path}))

(def match
  (reitit/map->Match
   {:data
    {:fhir.compartment/code "Patient"
     :name :Patient/compartment}
    :path (str context-path "/Patient/0/Observation")}))

(def page-match
  (reitit/map->Match
   {:data
    {:fhir.compartment/code "Patient"
     :name :Patient/compartment-page}
    :path (str context-path "/Patient/0/Observation")}))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.interaction/search-compartment
   {::search-util/link (ig/ref ::search-util/link)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref :blaze.page-store/local)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   ::search-util/link {:fhir/version "4.0.1"}
   :blaze.test/fixed-rng-fn {}
   :blaze.page-store/local {}
   :blaze.test/fixed-rng {}
   :blaze.test/page-id-cipher {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction/search-compartment nil}
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/search-compartment {}}
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% ::search-util/link))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :page-store))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "invalid link function"
    (given-failed-system (assoc-in config [:blaze.interaction/search-compartment ::search-util/link] ::invalid)
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::search-util/link]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.interaction/search-compartment :clock] ::invalid)
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.interaction/search-compartment :rng-fn] ::invalid)
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid page-store"
    (given-failed-system (assoc-in config [:blaze.interaction/search-compartment :page-store] ::invalid)
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid page-id-cipher"
    (given-failed-system (assoc-in config [:blaze.interaction/search-compartment :page-id-cipher] ::invalid)
      :key := :blaze.interaction/search-compartment
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-id-cipher]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(defn wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router
            ::reitit/match match))))

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
                         handler# :blaze.interaction/search-compartment} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node# page-id-cipher#)
                                  wrap-error)
             ~(or node-binding '_) node#
             ~(or page-id-cipher-binding '_) page-id-cipher#]
         ~@body))))

(defn- page-url [page-id-cipher type query-params]
  (str base-url context-path "/Patient/0/" type "/__page/" (decrypt-page-id/encrypt page-id-cipher query-params)))

(defn- page-path-params [page-id-cipher params]
  {:id "0" :type "Observation" :page-id (decrypt-page-id/encrypt page-id-cipher params)})

(deftest handler-test
  (testing "Returns an Error on Invalid Id"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "<invalid>" :type "Observation"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "value"
          [:issue 0 :diagnostics] := #fhir/string "The identifier `<invalid>` is invalid."))))

  (testing "Returns an Error on Invalid Type"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0" :type "<invalid>"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "value"
          [:issue 0 :diagnostics] := #fhir/string "The type `<invalid>` is invalid."))))

  (testing "on unknown search parameter"
    (testing "with strict handling"
      (testing "returns error"
        (testing "normal result"
          (with-handler [handler]
            (let [{:keys [status body]}
                  @(handler
                    {:path-params {:id "0" :type "Observation"}
                     :headers {"prefer" "handling=strict"}
                     :params {"foo" "bar"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "not-found"
                [:issue 0 :diagnostics] := #fhir/string "The search-param with code `foo` and type `Observation` was not found."))))

        (testing "summary result"
          (with-handler [handler]
            (let [{:keys [status body]}
                  @(handler
                    {:path-params {:id "0" :type "Observation"}
                     :headers {"prefer" "handling=strict"}
                     :params {"foo" "bar" "_summary" "count"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "not-found"
                [:issue 0 :diagnostics] := #fhir/string "The search-param with code `foo` and type `Observation` was not found."))))))

    (testing "with lenient handling"
      (testing "returns results with a self link lacking the unknown search parameter"
        (testing "where the unknown search parameter is the only one"
          (testing "normal result"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"}]
                [:put {:fhir/type :fhir/Observation :id "0"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

              (let [{:keys [status body]}
                    @(handler
                      {:path-params {:id "0" :type "Observation"}
                       :headers {"prefer" "handling=lenient"}
                       :params {"foo" "bar"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle contains an id"
                  (is (string? (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient/0/Observation?_count=50")
                         (link-url body "self")))))))

          (testing "summary result"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"}]
                [:put {:fhir/type :fhir/Observation :id "0"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

              (let [{:keys [status body]}
                    @(handler
                      {:path-params {:id "0" :type "Observation"}
                       :headers {"prefer" "handling=lenient"}
                       :params {"foo" "bar" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle contains an id"
                  (is (string? (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient/0/Observation?_summary=count&_count=50")
                         (link-url body "self"))))))))

        (testing "with another search parameter"
          (testing "normal result"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"}]
                [:put {:fhir/type :fhir/Observation :id "0"
                       :status #fhir/code "final"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
                [:put {:fhir/type :fhir/Observation :id "1"
                       :status #fhir/code "preliminary"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

              (let [{:keys [status body]}
                    @(handler
                      {:path-params {:id "0" :type "Observation"}
                       :headers {"prefer" "handling=lenient"}
                       :params {"foo" "bar" "status" "preliminary"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient/0/Observation?status=preliminary&_count=50")
                         (link-url body "self")))))))

          (testing "summary result"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"}]
                [:put {:fhir/type :fhir/Observation :id "0"
                       :status #fhir/code "final"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
                [:put {:fhir/type :fhir/Observation :id "1"
                       :status #fhir/code "preliminary"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

              (let [{:keys [status body]}
                    @(handler
                      {:path-params {:id "0" :type "Observation"}
                       :headers {"prefer" "handling=lenient"}
                       :params {"foo" "bar" "status" "preliminary" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient/0/Observation?status=preliminary&_summary=count&_count=50")
                         (link-url body "self"))))))))))

    (testing "with default handling"
      (testing "returns results with a self link lacking the unknown search parameter"
        (testing "where the unknown search parameter is the only one"
          (testing "normal result"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"}]
                [:put {:fhir/type :fhir/Observation :id "0"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

              (let [{:keys [status body]}
                    @(handler
                      {:path-params {:id "0" :type "Observation"}
                       :params {"foo" "bar"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle contains an id"
                  (is (string? (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient/0/Observation?_count=50")
                         (link-url body "self")))))))

          (testing "summary result"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"}]
                [:put {:fhir/type :fhir/Observation :id "0"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

              (let [{:keys [status body]}
                    @(handler
                      {:path-params {:id "0" :type "Observation"}
                       :params {"foo" "bar" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle contains an id"
                  (is (string? (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient/0/Observation?_summary=count&_count=50")
                         (link-url body "self"))))))))

        (testing "with another search parameter"
          (testing "normal result"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"}]
                [:put {:fhir/type :fhir/Observation :id "0"
                       :status #fhir/code "final"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
                [:put {:fhir/type :fhir/Observation :id "1"
                       :status #fhir/code "preliminary"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

              (let [{:keys [status body]}
                    @(handler
                      {:path-params {:id "0" :type "Observation"}
                       :params {"foo" "bar" "status" "preliminary"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient/0/Observation?status=preliminary&_count=50")
                         (link-url body "self")))))))

          (testing "summary result"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"}]
                [:put {:fhir/type :fhir/Observation :id "0"
                       :status #fhir/code "final"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
                [:put {:fhir/type :fhir/Observation :id "1"
                       :status #fhir/code "preliminary"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

              (let [{:keys [status body]}
                    @(handler
                      {:path-params {:id "0" :type "Observation"}
                       :params {"foo" "bar" "status" "preliminary" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code "searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entry"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= (str base-url context-path "/Patient/0/Observation?status=preliminary&_summary=count&_count=50")
                         (link-url body "self")))))))))))

  (testing "Returns an empty Bundle on Non-Existing Compartment"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0" :type "Observation"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Bundle
          :type := #fhir/code "searchset"
          :total := #fhir/unsignedInt 0))))

  (testing "with two Observations"
    (with-handler [handler _ page-id-cipher]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :status #fhir/code "final"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :status #fhir/code "preliminary"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (let [request {:path-params {:id "0" :type "Observation"}}]

        (testing "with _summary=count"
          (let [{:keys [status body]}
                @(handler (assoc-in request [:params "_summary"] "count"))]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "the bundle contains no entry"
              (is (empty? (:entry body))))))

        (testing "with _summary=count and status=final"
          (let [{:keys [status body]}
                @(handler (-> (assoc-in request [:params "_summary"] "count")
                              (assoc-in [:params "status"] "final")))]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 1"
              (is (= #fhir/unsignedInt 1 (:total body))))

            (testing "the bundle contains no entry"
              (is (empty? (:entry body))))))

        (testing "with no query param"
          (let [{:keys [status body]} @(handler request)]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient/0/Observation?_count=50")
                     (link-url body "self"))))

            (testing "the bundle contains two entries"
              (is (= 2 (count (:entry body)))))

            (testing "the first entry"
              (given (-> body :entry first)
                [:fullUrl :value] := (str base-url context-path "/Observation/0")
                [:resource :fhir/type] := :fhir/Observation
                [:resource :id] := "0"))

            (testing "the second entry"
              (given (-> body :entry second)
                [:fullUrl :value] := (str base-url context-path "/Observation/1")
                [:resource :fhir/type] := :fhir/Observation
                [:resource :id] := "1"))))

        (testing "with _count=1"
          (let [{:keys [status body]}
                @(handler (assoc-in request [:params "_count"] "1"))]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is 2"
              (is (= #fhir/unsignedInt 2 (:total body))))

            (testing "has a self link"
              (is (= (str base-url context-path "/Patient/0/Observation?_count=1")
                     (link-url body "self"))))

            (testing "has a next link"
              (is (= (page-url page-id-cipher "Observation" {"_count" "1" "__t" "1" "__page-offset" "1"})
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the entry"
              (given (-> body :entry first)
                [:fullUrl :value] := (str base-url context-path "/Observation/0")
                [:resource :fhir/type] := :fhir/Observation
                [:resource :id] := "0")))

          (testing "following the next link"
            (let [{:keys [status body]}
                  @(handler
                    {::reitit/match page-match
                     :path-params (page-path-params page-id-cipher {"_count" "1" "__t" "1" "__page-offset" "1"})})]

              (is (= 200 status))

              (testing "the total count is 2"
                (is (= #fhir/unsignedInt 2 (:total body))))

              (testing "has no next link"
                (is (nil? (link-url body "next"))))

              (testing "the bundle contains one entry"
                (is (= 1 (count (:entry body)))))

              (testing "the entry"
                (given (-> body :entry first)
                  [:fullUrl :value] := (str base-url context-path "/Observation/1")
                  [:resource :fhir/type] := :fhir/Observation
                  [:resource :id] := "1")))))

        (testing "missing resource contents"
          (with-redefs [rs/multi-get (fn [_ _] (ac/completed-future {}))]
            (let [{:keys [status body]} @(handler request)]

              (is (= 500 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "incomplete"
                [:issue 0 :diagnostics] := #fhir/string "The resource content of `Observation/0` with hash `07F3F62AAE35B3BEF8F1AAA7B4BA3DE6055541BF073A65DFF32B512A460D6D1E` was not found."))))))))
