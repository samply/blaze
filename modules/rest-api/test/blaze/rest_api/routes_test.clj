(ns blaze.rest-api.routes-test
  (:require
   [blaze.db.api-stub :refer [mem-node-config]]
   [blaze.db.impl.search-param]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.job-scheduler]
   [blaze.module.test-util :refer [with-system]]
   [blaze.rest-api.middleware.metrics :as metrics]
   [blaze.rest-api.routes :as routes]
   [blaze.rest-api.routes-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [reitit.ring]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest resource-route-test
  (testing "read interaction"
    (given (routes/resource-route
            {:node ::node
             :db-sync-timeout 1000}
            [#:blaze.rest-api.resource-pattern
              {:type :default
               :interactions
               {:read
                #:blaze.rest-api.interaction
                 {:handler (fn [_] ::read)}}}]
            {:kind "resource" :name "Patient"})
      [0] := "/Patient"
      [1 :fhir.resource/type] := "Patient"
      [2] := ["" {:name :Patient/type}]
      [3] := ["/_history" {:name :Patient/history :conflicting true}]
      [4] := ["/_search" {:name :Patient/search :conflicting true}]
      [5] := ["/__page/{page-id}" {:name :Patient/page :conflicting true}]
      [6] := ["/__history-page/{page-id}" {:name :Patient/history-page :conflicting true}]
      [7 1 1 :name] := :Patient/instance
      [7 1 1 :conflicting] := true
      [7 1 1 :get :middleware count] := 1
      [7 1 1 :get :middleware 0 0 :name] := :db
      [7 1 1 :get :middleware 0 1] := ::node
      [7 1 1 :get :middleware 0 2] := 1000
      [7 1 1 :get :handler #(% {})] := ::read
      [7 2 0] := "/_history"
      [7 2 1] := ["" {:name :Patient/history-instance, :conflicting true}])

    (testing "as batch"
      (given (routes/resource-route
              {:batch? true}
              [#:blaze.rest-api.resource-pattern
                {:type :default
                 :interactions
                 {:read
                  #:blaze.rest-api.interaction
                   {:handler (fn [_] ::read)}}}]
              {:kind "resource" :name "Patient"})
        [0] := "/Patient"
        [1 :fhir.resource/type] := "Patient"
        [2] := ["" {:name :Patient/type}]
        [3] := ["/_history" {:name :Patient/history :conflicting true}]
        [4] := ["/_search" {:name :Patient/search :conflicting true}]
        [5 1 1 :name] := :Patient/instance
        [5 1 1 :conflicting] := true
        [5 1 1 :get :middleware count] := 1
        [5 1 1 :get :middleware 0 0 :name] := :db
        [5 1 1 :get :middleware 0 1] := nil
        [5 1 1 :get :middleware 0 2] := nil
        [5 1 1 :get :handler #(% {})] := ::read
        [5 2 0] := "/_history"
        [5 2 1] := ["" {:name :Patient/history-instance, :conflicting true}]))))

(defn- handler [key]
  (fn [_] key))

(def ^:private system-config
  (assoc
   mem-node-config
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   :blaze.test/fixed-rng-fn {}))

(def ^:private config
  {:structure-definition-repo structure-definition-repo
   :search-system-handler (handler ::search-system)
   :transaction-handler (handler ::transaction)
   :history-system-handler (handler ::history-system)
   :async-status-handler (handler ::async-status)
   :resource-patterns
   [#:blaze.rest-api.resource-pattern
     {:type :default
      :interactions
      {:read
       #:blaze.rest-api.interaction
        {:handler (handler ::read)}
       :vread
       #:blaze.rest-api.interaction
        {:handler (handler ::vread)}
       :update
       #:blaze.rest-api.interaction
        {:handler (handler ::update)}
       :delete
       #:blaze.rest-api.interaction
        {:handler (handler ::delete)}
       :conditional-delete-type
       #:blaze.rest-api.interaction
        {:handler (handler ::conditional-delete-type)}
       :history-instance
       #:blaze.rest-api.interaction
        {:handler (handler ::history-instance)}
       :history-type
       #:blaze.rest-api.interaction
        {:handler (handler ::history-type)}
       :create
       #:blaze.rest-api.interaction
        {:handler (handler ::create)}
       :search-type
       #:blaze.rest-api.interaction
        {:handler (handler ::search-type)}}}]
   :compartments
   [#:blaze.rest-api.compartment
     {:code "Patient"
      :search-handler (handler ::search-patient-compartment)}]
   :operations
   [#:blaze.rest-api.operation
     {:code "compact-db"
      :system-handler (handler ::compact-db)}
    #:blaze.rest-api.operation
     {:code "evaluate-measure"
      :resource-types ["Measure"]
      :type-handler (handler ::evaluate-measure-type)
      :instance-handler (handler ::evaluate-measure-instance)}
    #:blaze.rest-api.operation
     {:code "everything"
      :resource-types ["Patient"]
      :instance-handler (handler ::everything)
      :instance-page-handler (handler ::everything-page)}]
   :capabilities-handler (handler ::capabilities)
   :metadata-handler (handler ::metadata)
   :admin-handler (handler ::admin)
   :node ::node
   :db-sync-timeout 1000})

(defn- router
  [config
   {:blaze/keys [job-scheduler] :blaze.test/keys [fixed-clock fixed-rng-fn]}]
  (reitit.ring/router
   (routes/routes
    (assoc config :job-scheduler job-scheduler :context-path "/foo"
           :clock fixed-clock :rng-fn fixed-rng-fn))
   {:path "" :syntax :bracket}))

(deftest compile-observe-request-duration-middleware-test
  (with-system [system system-config]
    (with-redefs [metrics/wrap-observe-request-duration-fn
                  (fn [interaction]
                    (fn [_handler]
                      interaction))]
      (let [router (router config system)]
        (are [path request-method interaction]
             (let [middlewares (get-in (reitit/match-by-path router path) [:result request-method :middleware])
                   observe-request-duration (:wrap (some #(when (= :observe-request-duration (:name %)) %) middlewares))]
               (= interaction (observe-request-duration ::handler)))

          "" :get "search-system"
          "" :post "transaction"
          "/_history" :get "history-system"
          "/__page/0" :get "search-system"
          "/__page/0" :post "search-system"
          "/__history-page/0" :get "history-system"
          "/Patient" :get "search-type"
          "/Patient" :post "create"
          "/Patient/_search" :post "search-type"
          "/Patient/_history" :get "history-type"
          "/Patient/__page/0" :get "search-type"
          "/Patient/__page/0" :post "search-type"
          "/Patient/__history-page/0" :get "history-type"
          "/Patient/0" :get "read"
          "/Patient/0" :put "update"
          "/Patient/0" :delete "delete"
          "/Patient/0/_history" :get "history-instance"
          "/Patient/0/__history-page/0" :get "history-instance"
          "/Patient/0/_history/42" :get "vread"
          "/Patient/0/$everything" :get "operation-instance-everything"
          "/Patient/0/__everything-page/0" :get "operation-instance-everything"
          "/Patient/0/Condition" :get "search-compartment"
          "/Patient/0/Observation" :get "search-compartment"
          "/$compact-db" :get "operation-system-compact-db"
          "/$compact-db" :post "operation-system-compact-db"
          "/Measure/$evaluate-measure" :get "operation-type-evaluate-measure"
          "/Measure/$evaluate-measure" :post "operation-type-evaluate-measure"
          "/Measure/0/$evaluate-measure" :get "operation-instance-evaluate-measure"
          "/Measure/0/$evaluate-measure" :post "operation-instance-evaluate-measure"
          "/Measure/0" :get "read"
          "/metadata" :get "capabilities")))))

(deftest handlers-test
  (with-system [system system-config]
    (let [router (router config system)]
      (are [path request-method handler]
           (= handler
              ((get-in
                (reitit/match-by-path router path)
                [:result request-method :data :handler])
               {}))
        "" :get ::search-system
        "" :post ::transaction
        "/_history" :get ::history-system
        "/__page/0" :get ::search-system
        "/__page/0" :post ::search-system
        "/__history-page/0" :get ::history-system
        "/Patient" :get ::search-type
        "/Patient" :post ::create
        "/Patient" :delete ::conditional-delete-type
        "/Patient/_search" :post ::search-type
        "/Patient/_history" :get ::history-type
        "/Patient/__page/0" :get ::search-type
        "/Patient/__page/0" :post ::search-type
        "/Patient/__history-page/0" :get ::history-type
        "/Patient/0" :get ::read
        "/Patient/0" :put ::update
        "/Patient/0" :delete ::delete
        "/Patient/0/_history" :get ::history-instance
        "/Patient/0/__history-page/0" :get ::history-instance
        "/Patient/0/_history/42" :get ::vread
        "/Patient/0/$everything" :get ::everything
        "/Patient/0/$everything" :post ::everything
        "/Patient/0/__everything-page/0" :get ::everything-page
        "/Patient/0/Condition" :get ::search-patient-compartment
        "/Patient/0/Observation" :get ::search-patient-compartment
        "/Patient/0/Condition/__page/0" :get ::search-patient-compartment
        "/Patient/0/Observation/__page/0" :get ::search-patient-compartment
        "/$compact-db" :get ::compact-db
        "/$compact-db" :post ::compact-db
        "/Measure/$evaluate-measure" :get ::evaluate-measure-type
        "/Measure/$evaluate-measure" :post ::evaluate-measure-type
        "/Measure/0/$evaluate-measure" :get ::evaluate-measure-instance
        "/Measure/0/$evaluate-measure" :post ::evaluate-measure-instance
        "/Measure/0" :get ::read
        "/__metadata/StructureDefinition" :get ::metadata
        "/__admin" :get ::admin
        "/__admin/more" :get ::admin))))

(deftest middleware-test
  (with-system [system system-config]
    (let [router (router config system)]
      (are [path request-method middleware]
           (= middleware
              (->> (get-in
                    (reitit/match-by-path router path)
                    [:result request-method :data :middleware])
                   (mapv (comp :name #(if (sequential? %) (first %) %)))))
        "" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
        "" :post [:observe-request-duration :params :output :error :forwarded :sync :resource]
        "/_history" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
        "/__page/0" :get [:observe-request-duration :params :output :error :forwarded :sync :decrypt-page-id :snapshot-db :link-headers]
        "/__page/0" :post [:observe-request-duration :params :output :error :forwarded :sync :decrypt-page-id :snapshot-db :link-headers]
        "/__history-page/0" :get [:observe-request-duration :params :output :error :forwarded :sync :decrypt-page-id :snapshot-db :link-headers]
        "/Patient" :get [:observe-request-duration :params :output :error :forwarded :sync :db  :link-headers]
        "/Patient" :post [:observe-request-duration :params :output :error :forwarded :sync :resource]
        "/Patient" :delete [:observe-request-duration :params :output :error :forwarded :sync]
        "/Patient/_history" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
        "/Patient/__history-page/0" :get [:observe-request-duration :params :output :error :forwarded :sync :decrypt-page-id :snapshot-db :link-headers]
        "/Patient/_search" :post [:observe-request-duration :params :output :error :forwarded :sync :ensure-form-body :db :link-headers]
        "/Patient/__page/0" :get [:observe-request-duration :params :output :error :forwarded :sync :decrypt-page-id :snapshot-db :link-headers]
        "/Patient/__page/0" :post [:observe-request-duration :params :output :error :forwarded :sync :decrypt-page-id :snapshot-db :link-headers]
        "/Patient/0" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
        "/Patient/0" :put [:observe-request-duration :params :output :error :forwarded :sync :resource]
        "/Patient/0" :delete [:observe-request-duration :params :output :error :forwarded :sync]
        "/Patient/0/_history" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
        "/Patient/0/__history-page/0" :get [:observe-request-duration :params :output :error :forwarded :sync :decrypt-page-id :snapshot-db :link-headers]
        "/Patient/0/_history/42" :get [:observe-request-duration :params :output :error :forwarded :sync :versioned-instance-db]
        "/Patient/0/$everything" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
        "/Patient/0/__everything-page/0" :get [:observe-request-duration :params :output :error :forwarded :sync :decrypt-page-id :snapshot-db]
        "/Patient/0/Condition" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
        "/Patient/0/Observation" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
        "/$compact-db" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
        "/$compact-db" :post [:observe-request-duration :params :output :error :forwarded :sync :db :resource]
        "/Measure/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
        "/Measure/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :db  :resource]
        "/Measure/0/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
        "/Measure/0/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :db :resource]
        "/Measure/0" :get [:observe-request-duration :params :output :error :forwarded :sync :db]))

    (testing "as batch"
      (let [router (router (assoc config :batch? true) system)]
        (are [path request-method middleware]
             (= middleware
                (->> (get-in
                      (reitit/match-by-path router path)
                      [:result request-method :data :middleware])
                     (mapv (comp :name #(if (sequential? %) (first %) %)))))
          "" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
          "" :post [:observe-request-duration :params :output :error :forwarded :sync :resource]
          "/_history" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
          "/Patient" :get [:observe-request-duration :params :output :error :forwarded :sync :db  :link-headers]
          "/Patient" :post [:observe-request-duration :params :output :error :forwarded :sync :resource]
          "/Patient" :delete [:observe-request-duration :params :output :error :forwarded :sync]
          "/Patient/_history" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
          "/Patient/_search" :post [:observe-request-duration :params :output :error :forwarded :sync :ensure-form-body :db :link-headers]
          "/Patient/0" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
          "/Patient/0" :put [:observe-request-duration :params :output :error :forwarded :sync :resource]
          "/Patient/0" :delete [:observe-request-duration :params :output :error :forwarded :sync]
          "/Patient/0/_history" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
          "/Patient/0/_history/42" :get [:observe-request-duration :params :output :error :forwarded :sync :versioned-instance-db]
          "/Patient/0/Condition" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
          "/Patient/0/Observation" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
          "/$compact-db" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
          "/$compact-db" :post [:observe-request-duration :params :output :error :forwarded :sync :db :resource]
          "/Measure/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
          "/Measure/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :db  :resource]
          "/Measure/0/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
          "/Measure/0/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :db :resource]
          "/Measure/0" :get [:observe-request-duration :params :output :error :forwarded :sync :db])))))

(deftest middleware-with-auth-backends-test
  (with-system [system system-config]
    (let [router (router (assoc config :auth-backends [:auth-backend]) system)]
      (are [path request-method middleware]
           (= middleware
              (->> (get-in
                    (reitit/match-by-path router path)
                    [:result request-method :data :middleware])
                   (mapv (comp :name #(if (sequential? %) (first %) %)))))
        "" :get [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db :link-headers]
        "" :post [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :resource]
        "/$compact-db" :get [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db]
        "/$compact-db" :post [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db :resource]
        "/Measure/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db]
        "/Measure/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db  :resource]
        "/Measure/0/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db]
        "/Measure/0/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db :resource]))))
