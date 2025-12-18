(ns blaze.rest-api.routes
  (:refer-clojure :exclude [str])
  (:require
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.middleware.fhir.error :as error]
   [blaze.middleware.fhir.output :as fhir-output]
   [blaze.middleware.fhir.resource :as resource]
   [blaze.middleware.fhir.validate :as validate]
   [blaze.middleware.link-headers :as link-headers]
   [blaze.middleware.output :as output]
   [blaze.rest-api.middleware.auth-guard :as auth-guard]
   [blaze.rest-api.middleware.ensure-form-body :as ensure-form-body]
   [blaze.rest-api.middleware.forwarded :as forwarded]
   [blaze.rest-api.middleware.metrics :as metrics]
   [blaze.rest-api.middleware.sync :as sync]
   [blaze.rest-api.operation :as-alias operation]
   [blaze.rest-api.spec]
   [blaze.rest-api.util :as u]
   [blaze.spec]
   [blaze.util :refer [str]]
   [reitit.ring]
   [reitit.ring.spec]
   [ring.middleware.params :as ring-params]))

(set! *warn-on-reflection* true)

(def ^:private wrap-params
  {:name :params
   :wrap ring-params/wrap-params})

(def ^:private wrap-forwarded
  {:name :forwarded
   :wrap forwarded/wrap-forwarded})

(def ^:private wrap-auth-guard
  {:name :auth-guard
   :wrap auth-guard/wrap-auth-guard})

(def ^:private wrap-resource
  {:name :resource
   :wrap resource/wrap-resource})

(def ^:private wrap-validate
  {:name :validate
   :wrap validate/wrap-validate})

(def ^:private wrap-binary-data
  {:name :binary-data
   :wrap resource/wrap-binary-data})

(def ^:private wrap-db
  {:name :db
   :wrap db/wrap-db})

(def ^:private wrap-snapshot-db
  {:name :snapshot-db
   :wrap db/wrap-snapshot-db})

(def ^:private wrap-ensure-form-body
  {:name :ensure-form-body
   :wrap ensure-form-body/wrap-ensure-form-body})

(def ^:private wrap-link-headers
  {:name :link-headers
   :wrap link-headers/wrap-link-headers})

(def ^:private wrap-sync
  {:name :sync
   :wrap sync/wrap-sync})

(def ^:private wrap-output
  {:name :output
   :compile (fn [{:keys [response-type] :response-type.json/keys [opts]} _]
              (condp = response-type
                :json (fn [handler _writing-context]
                        ((output/wrap-output opts) handler))
                :binary fhir-output/wrap-binary-output
                :none (fn [handler _writing-context] handler)
                fhir-output/wrap-output))})

(def ^:private wrap-error
  {:name :error
   :compile (fn [{:keys [response-type]} _]
              (if (= :json response-type)
                error/wrap-json-error
                error/wrap-error))})

(def ^:private wrap-observe-request-duration
  {:name :observe-request-duration
   :compile (fn [{:keys [interaction]} _]
              (if interaction
                (metrics/wrap-observe-request-duration-fn interaction)
                identity))})

(def ^:private wrap-decrypt-page-id
  {:name :decrypt-page-id
   :wrap decrypt-page-id/wrap-decrypt-page-id})

(defn resource-route
  "Builds routes for one resource according to `structure-definition`.

  Returns nil if the resource has no match in `resource-patterns`.

  Route data contains the resource type under :fhir.resource/type."
  {:arglists '([config resource-patterns structure-definition])}
  [{:keys [node db-sync-timeout batch? validator page-id-cipher parsing-context]}
   resource-patterns {:keys [name] :as structure-definition}]
  (when-let
   [{:blaze.rest-api.resource-pattern/keys [interactions]}
    (u/resolve-pattern resource-patterns structure-definition)]
    (cond->
     [(str "/" name)
      {:fhir.resource/type name}
      [""
       (cond-> {:name (keyword name "type")}
         (contains? interactions :search-type)
         (assoc :get {:interaction "search-type"
                      :middleware [[wrap-db node db-sync-timeout]
                                   wrap-link-headers]
                      :handler (-> interactions :search-type
                                   :blaze.rest-api.interaction/handler)})
         (contains? interactions :create)
         (assoc :post {:interaction "create"
                       :middleware (cond->
                                    [(if (= name "Binary")
                                       [wrap-binary-data parsing-context]
                                       [wrap-resource parsing-context name])]
                                     (some? validator) (conj [wrap-validate validator]))
                       :handler (-> interactions :create
                                    :blaze.rest-api.interaction/handler)})
         (contains? interactions :conditional-delete-type)
         (assoc :delete {:interaction "conditional-delete-type"
                         :handler (-> interactions :conditional-delete-type
                                      :blaze.rest-api.interaction/handler)}))]
      ["/_history"
       (cond-> {:name (keyword name "history") :conflicting true}
         (contains? interactions :history-type)
         (assoc :get {:interaction "history-type"
                      :middleware [[wrap-db node db-sync-timeout]
                                   wrap-link-headers]
                      :handler (-> interactions :history-type
                                   :blaze.rest-api.interaction/handler)}))]
      ["/_search"
       (cond-> {:name (keyword name "search") :conflicting true}
         (contains? interactions :search-type)
         (assoc :post {:interaction "search-type"
                       :middleware [wrap-ensure-form-body
                                    [wrap-db node db-sync-timeout]
                                    wrap-link-headers]
                       :handler (-> interactions :search-type
                                    :blaze.rest-api.interaction/handler)}))]]
      (not batch?)
      (conj
       ["/__page/{page-id}"
        (cond-> {:name (keyword name "page") :conflicting true}
          (contains? interactions :search-type)
          (assoc
           :get {:interaction "search-type"
                 :middleware [[wrap-decrypt-page-id page-id-cipher]
                              [wrap-snapshot-db node db-sync-timeout]
                              wrap-link-headers]
                 :handler (-> interactions :search-type
                              :blaze.rest-api.interaction/handler)}
           :post {:interaction "search-type"
                  :middleware [[wrap-decrypt-page-id page-id-cipher]
                               [wrap-snapshot-db node db-sync-timeout]
                               wrap-link-headers]
                  :handler (-> interactions :search-type
                               :blaze.rest-api.interaction/handler)}))]
       ["/__history-page/{page-id}"
        (cond-> {:name (keyword name "history-page") :conflicting true}
          (contains? interactions :history-type)
          (assoc :get {:interaction "history-type"
                       :middleware [[wrap-decrypt-page-id page-id-cipher]
                                    [wrap-snapshot-db node db-sync-timeout]
                                    wrap-link-headers]
                       :handler (-> interactions :history-type
                                    :blaze.rest-api.interaction/handler)}))])
      true
      (conj
       (cond->
        ["/{id}"
         [""
          (cond->
           {:name (keyword name "instance")
            :conflicting true}
            (= name "Binary")
            (assoc :response-type :binary)
            (contains? interactions :read)
            (assoc :get {:interaction "read"
                         :middleware [[wrap-db node db-sync-timeout]]
                         :handler (-> interactions :read
                                      :blaze.rest-api.interaction/handler)})
            (contains? interactions :update)
            (assoc :put {:interaction "update"
                         :middleware (cond->
                                      [(if (= name "Binary")
                                         [wrap-binary-data parsing-context]
                                         [wrap-resource parsing-context name])]
                                       (some? validator) (conj [wrap-validate validator]))
                         :handler (-> interactions :update
                                      :blaze.rest-api.interaction/handler)})
            (contains? interactions :delete)
            (assoc :delete {:interaction "delete"
                            :handler (-> interactions :delete
                                         :blaze.rest-api.interaction/handler)}))]
         ["/_history"
          [""
           (cond->
            {:name (keyword name "history-instance")
             :conflicting true}
             (contains? interactions :history-instance)
             (assoc :get {:interaction "history-instance"
                          :middleware [[wrap-db node db-sync-timeout]
                                       wrap-link-headers]
                          :handler (-> interactions :history-instance
                                       :blaze.rest-api.interaction/handler)})
             (contains? interactions :delete-history)
             (assoc :delete {:interaction "delete-history"
                             :handler (-> interactions :delete-history
                                          :blaze.rest-api.interaction/handler)}))]
          ["/{vid}"
           (cond-> {:name (keyword name "versioned-instance")}
             (contains? interactions :vread)
             (assoc :get {:interaction "vread"
                          :middleware [[wrap-db node db-sync-timeout]]
                          :handler (-> interactions :vread
                                       :blaze.rest-api.interaction/handler)}))]]]
         (not batch?)
         (conj
          ["/__history-page/{page-id}"
           (cond->
            {:name (keyword name "history-instance-page")
             :conflicting true}
             (contains? interactions :history-instance)
             (assoc :get {:interaction "history-instance"
                          :middleware [[wrap-decrypt-page-id page-id-cipher]
                                       [wrap-snapshot-db node db-sync-timeout]
                                       wrap-link-headers]
                          :handler (-> interactions :history-instance
                                       :blaze.rest-api.interaction/handler)}))]))))))

(defn compartment-route
  {:arglists '([context compartment])}
  [{:keys [node db-sync-timeout batch? page-id-cipher]}
   {:blaze.rest-api.compartment/keys [code search-handler]}]
  (cond->
   [(format "/%s/{id}/{type}" code)
    {:fhir.compartment/code code}
    [""
     {:name (keyword code "compartment")
      :fhir.compartment/code code
      :conflicting true
      :get {:interaction "search-compartment"
            :middleware [[wrap-db node db-sync-timeout]
                         wrap-link-headers]
            :handler search-handler}}]]
    (not batch?)
    (conj
     ["/__page/{page-id}"
      {:name (keyword code "compartment-page")
       :conflicting true
       :get {:interaction "search-compartment"
             :middleware [[wrap-decrypt-page-id page-id-cipher]
                          [wrap-snapshot-db node db-sync-timeout]
                          wrap-link-headers]
             :handler search-handler}}])))

(defn- operation-system-handler-route
  [{:keys [node db-sync-timeout parsing-context]}
   {::operation/keys [code affects-state response-type post-middleware
                      system-handler]}]
  (when system-handler
    [[(str "/$" code)
      (cond->
       {:interaction (str "operation-system-" code)
        :middleware [[wrap-db node db-sync-timeout]]

        :post {:middleware [(if post-middleware
                              post-middleware
                              [wrap-resource parsing-context "Parameters"])]
               :handler system-handler}}
        (not (true? affects-state))
        (assoc :get system-handler)
        response-type
        (assoc :response-type response-type))]]))

(defn- operation-type-handler-route
  [{:keys [node db-sync-timeout parsing-context]}
   {::operation/keys [code affects-state resource-types type-handler]}]
  (when type-handler
    (map
     (fn [resource-type]
       [(str "/" resource-type "/$" code)
        (cond->
         {:interaction (str "operation-type-" code)
          :fhir.resource/type resource-type
          :conflicting true
          :middleware [[wrap-db node db-sync-timeout]]
          :post {:middleware [[wrap-resource parsing-context "Parameters"]]
                 :handler type-handler}}
          (not (true? affects-state))
          (assoc :get type-handler))])
     resource-types)))

(defn- operation-instance-handler-route
  [{:keys [node db-sync-timeout page-id-cipher parsing-context]}
   {::operation/keys [code affects-state resource-types instance-handler
                      instance-page-handler]}]
  (when instance-handler
    (map
     (fn [resource-type]
       (cond->
        [(str "/" resource-type "/{id}")
         {:interaction (str "operation-instance-" code)}
         [(str "/$" code)
          (cond->
           {:name (keyword (str resource-type ".operation") code)
            :fhir.resource/type resource-type
            :conflicting true
            :middleware [[wrap-db node db-sync-timeout]]

            :post {:middleware [[wrap-resource parsing-context "Parameters"]]
                   :handler instance-handler}}
            (not (true? affects-state))
            (assoc :get instance-handler))]]

         instance-page-handler
         (conj
          [(str "/__" code "-page/{page-id}")
           {:name (keyword (str resource-type ".operation") (str code "-page"))
            :conflicting true
            :middleware [[wrap-decrypt-page-id page-id-cipher]
                         [wrap-snapshot-db node db-sync-timeout]]
            :get instance-page-handler}])))
     resource-types)))

(defn routes
  {:arglists '([config])}
  [{:keys
    [base-url
     context-path
     structure-definition-repo
     node
     admin-node
     auth-backends
     batch?
     db-sync-timeout
     search-system-handler
     transaction-handler
     history-system-handler
     resource-patterns
     compartments
     operations
     async-status-handler
     async-status-cancel-handler
     capabilities-handler
     admin-handler
     validator
     page-id-cipher
     parsing-context
     writing-context]
    :or {context-path ""}
    :as config}]
  (cond->
   [""
    {:middleware
     (cond-> [wrap-observe-request-duration wrap-params
              [wrap-output writing-context] wrap-error
              [wrap-forwarded base-url] wrap-sync]
       (seq auth-backends)
       (conj wrap-auth-guard))
     :blaze/context-path context-path}
    [""
     (cond-> {}
       (some? search-system-handler)
       (assoc :get {:interaction "search-system"
                    :middleware [[wrap-db node db-sync-timeout]
                                 wrap-link-headers]
                    :handler search-system-handler})
       (some? transaction-handler)
       (assoc :post {:interaction "transaction"
                     :middleware (cond->
                                  [[wrap-resource parsing-context "Bundle"]]
                                   (some? validator) (conj [wrap-validate validator]))
                     :handler transaction-handler}))]
    ["/metadata"
     {:interaction "capabilities"
      :get {:middleware [[wrap-db node db-sync-timeout]]
            :handler capabilities-handler}}]
    ["/_history"
     (cond-> {:name :history}
       (some? history-system-handler)
       (assoc :get {:interaction "history-system"
                    :middleware [[wrap-db node db-sync-timeout]
                                 wrap-link-headers]
                    :handler history-system-handler}))]]
    (not batch?)
    (conj
     ["/__page/{page-id}"
      (cond-> {:name :page}
        (some? search-system-handler)
        (assoc
         :get {:interaction "search-system"
               :middleware [[wrap-decrypt-page-id page-id-cipher]
                            [wrap-snapshot-db node db-sync-timeout]
                            wrap-link-headers]
               :handler search-system-handler}
         :post {:interaction "search-system"
                :middleware [[wrap-decrypt-page-id page-id-cipher]
                             [wrap-snapshot-db node db-sync-timeout]
                             wrap-link-headers]
                :handler search-system-handler}))]
     ["/__history-page/{page-id}"
      (cond-> {:name :history-page}
        (some? history-system-handler)
        (assoc :get {:interaction "history-system"
                     :middleware [[wrap-decrypt-page-id page-id-cipher]
                                  [wrap-snapshot-db node db-sync-timeout]
                                  wrap-link-headers]
                     :handler history-system-handler}))])
    true
    (into
     (mapcat (partial operation-system-handler-route config))
     operations)
    true
    (into
     (mapcat (partial operation-type-handler-route config))
     operations)
    true
    (into
     (mapcat (partial operation-instance-handler-route config))
     operations)
    true
    (into
     (keep (partial resource-route config resource-patterns))
     (sdr/resources structure-definition-repo))
    true
    (into
     (map (partial compartment-route config))
     compartments)
    (and async-status-handler async-status-cancel-handler admin-node)
    (conj
     ["/__async-status/{id}"
      {:name :async-status
       :get
       {:middleware [[wrap-db admin-node db-sync-timeout]]
        :handler async-status-handler}
       :delete
       {:handler async-status-cancel-handler}}])
    admin-handler
    (conj
     ["/__admin{*more}"
      {:get {:handler admin-handler}
       :post {:handler admin-handler}
       :response-type :none}])))
