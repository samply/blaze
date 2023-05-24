(ns blaze.rest-api.routes
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.search-param-registry.spec]
    [blaze.middleware.fhir.db :as db]
    [blaze.middleware.fhir.error :as error]
    [blaze.rest-api.middleware.auth-guard :as auth-guard]
    [blaze.rest-api.middleware.batch-handler :as batch-handler]
    [blaze.rest-api.middleware.ensure-form-body :as ensure-form-body]
    [blaze.rest-api.middleware.forwarded :as forwarded]
    [blaze.rest-api.middleware.link-headers :as link-headers]
    [blaze.rest-api.middleware.output :as output]
    [blaze.rest-api.middleware.resource :as resource]
    [blaze.rest-api.spec]
    [blaze.rest-api.util :as u]
    [blaze.spec]
    [reitit.ring]
    [reitit.ring.spec]
    [ring.middleware.params :as ring-params]))


(def ^:private wrap-params
  {:name :params
   :wrap ring-params/wrap-params})


(def ^:private wrap-forwarded
  {:name :forwarded
   :wrap forwarded/wrap-forwarded})


(def ^:private wrap-auth-guard
  {:name :auth-guard
   :wrap auth-guard/wrap-auth-guard})


(def ^:private wrap-batch-handler
  {:name :wrap-batch-handler
   :wrap batch-handler/wrap-batch-handler})


(def ^:private wrap-resource
  {:name :resource
   :wrap resource/wrap-resource})


(def ^:private wrap-db
  {:name :db
   :wrap db/wrap-db})


(def ^:private wrap-search-db
  {:name :search-db
   :wrap db/wrap-search-db})


(def ^:private wrap-snapshot-db
  {:name :snapshot-db
   :wrap db/wrap-snapshot-db})


(def ^:private wrap-versioned-instance-db
  {:name :versioned-instance-db
   :wrap db/wrap-versioned-instance-db})


(def ^:private wrap-ensure-form-body
  {:name :ensure-form-body
   :wrap ensure-form-body/wrap-ensure-form-body})


(def ^:private wrap-link-headers
  {:name :link-headers
   :wrap link-headers/wrap-link-headers})


(def ^:private wrap-sync
  {:name :sync
   :wrap
   (fn [handler]
     (fn [request respond raise]
       (-> (handler request)
           (ac/when-complete
             (fn [response e]
               (if e
                 (raise e)
                 (respond response)))))))})


(def ^:private wrap-output
  {:name :output
   :compile (fn [{:keys [response-type]} _]
              (if (= :json response-type)
                output/wrap-json-output
                output/wrap-output))})


(def ^:private wrap-error
  {:name :error
   :wrap error/wrap-error})


(defn resource-route
  "Builds routes for one resource according to `structure-definition`.

  Returns nil if the resource has no match in `resource-patterns`.

  Route data contains the resource type under :fhir.resource/type."
  {:arglists '([context resource-patterns structure-definition])}
  [{:keys [node db-sync-timeout]} resource-patterns
   {:keys [name] :as structure-definition}]
  (when-let
    [{:blaze.rest-api.resource-pattern/keys [interactions]}
     (u/resolve-pattern resource-patterns structure-definition)]
    [(str "/" name)
     {:fhir.resource/type name}
     [""
      (cond-> {:name (keyword name "type")}
        (contains? interactions :search-type)
        (assoc :get {:middleware [[wrap-search-db node db-sync-timeout]
                                  wrap-link-headers]
                     :handler (-> interactions :search-type
                                  :blaze.rest-api.interaction/handler)})
        (contains? interactions :create)
        (assoc :post {:middleware [wrap-resource]
                      :handler (-> interactions :create
                                   :blaze.rest-api.interaction/handler)}))]
     ["/_history"
      (cond-> {:conflicting true}
        (contains? interactions :history-type)
        (assoc :get {:middleware [[wrap-db node db-sync-timeout]
                                  wrap-link-headers]
                     :handler (-> interactions :history-type
                                  :blaze.rest-api.interaction/handler)}))]
     ["/_search"
      (cond-> {:name (keyword name "search") :conflicting true}
        (contains? interactions :search-type)
        (assoc :post {:middleware [wrap-ensure-form-body
                                   [wrap-db node db-sync-timeout]
                                   wrap-link-headers]
                      :handler (-> interactions :search-type
                                   :blaze.rest-api.interaction/handler)}))]
     ["/__page"
      (cond-> {:name (keyword name "page") :conflicting true}
        (contains? interactions :search-type)
        (assoc
          :get {:middleware [[wrap-snapshot-db node db-sync-timeout]
                             wrap-link-headers]
                :handler (-> interactions :search-type
                             :blaze.rest-api.interaction/handler)}
          :post {:middleware [[wrap-snapshot-db node db-sync-timeout]
                              wrap-link-headers]
                 :handler (-> interactions :search-type
                              :blaze.rest-api.interaction/handler)}))]
     ["/{id}"
      [""
       (cond->
         {:name (keyword name "instance")
          :conflicting true}
         (contains? interactions :read)
         (assoc :get {:middleware [[wrap-db node db-sync-timeout]]
                      :handler (-> interactions :read
                                   :blaze.rest-api.interaction/handler)})
         (contains? interactions :update)
         (assoc :put {:middleware [wrap-resource]
                      :handler (-> interactions :update
                                   :blaze.rest-api.interaction/handler)})
         (contains? interactions :delete)
         (assoc :delete (-> interactions :delete
                            :blaze.rest-api.interaction/handler)))]
      ["/_history"
       [""
        (cond->
          {:name (keyword name "history-instance")
           :conflicting true}
          (contains? interactions :history-instance)
          (assoc :get {:middleware [[wrap-db node db-sync-timeout]
                                    wrap-link-headers]
                       :handler (-> interactions :history-instance
                                    :blaze.rest-api.interaction/handler)}))]
       ["/{vid}"
        (cond-> {:name (keyword name "versioned-instance")}
          (contains? interactions :vread)
          (assoc :get {:middleware [[wrap-versioned-instance-db node db-sync-timeout]]
                       :handler (-> interactions :vread
                                    :blaze.rest-api.interaction/handler)}))]]]]))


(defn compartment-route
  {:arglists '([context compartment])}
  [{:keys [node db-sync-timeout]} {:blaze.rest-api.compartment/keys [code search-handler]}]
  [(format "/%s/{id}/{type}" code)
   {:name (keyword code "compartment")
    :fhir.compartment/code code
    :conflicting true
    :get {:middleware [[wrap-db node db-sync-timeout] wrap-link-headers]
          :handler search-handler}}])


(defn- operation-system-handler-route
  [{:keys [node db-sync-timeout]}
   {:blaze.rest-api.operation/keys
    [code response-type post-middleware system-handler]}]
  (when system-handler
    [[(str "/$" code)
      (cond-> {:middleware [[wrap-db node db-sync-timeout]]
               :get system-handler
               :post {:middleware [(if post-middleware
                                     post-middleware
                                     wrap-resource)]
                      :handler system-handler}}
        response-type
        (assoc :response-type response-type))]]))


(defn operation-type-handler-route
  [{:keys [node db-sync-timeout]}
   {:blaze.rest-api.operation/keys [code resource-types type-handler]}]
  (when type-handler
    (map
      (fn [resource-type]
        [(str "/" resource-type "/$" code)
         {:conflicting true
          :middleware [[wrap-db node db-sync-timeout]]
          :get type-handler
          :post {:middleware [wrap-resource]
                 :handler type-handler}}])
      resource-types)))


(defn operation-instance-handler-route
  [{:keys [node db-sync-timeout]}
   {:blaze.rest-api.operation/keys [code resource-types instance-handler]}]
  (when instance-handler
    (map
      (fn [resource-type]
        [(str "/" resource-type "/{id}/$" code)
         {:middleware [[wrap-db node db-sync-timeout]]
          :get instance-handler
          :post {:middleware [wrap-resource]
                 :handler instance-handler}}])
      resource-types)))


(defn routes
  {:arglists '([context capabilities-handler batch-handler-promise])}
  [{:keys
    [base-url
     context-path
     structure-definitions
     node
     auth-backends
     db-sync-timeout
     search-system-handler
     transaction-handler
     history-system-handler
     resource-patterns
     compartments
     operations]
    :or {context-path ""}
    :as context}
   capabilities-handler
   batch-handler-promise]
  (-> [""
       {:middleware
        (cond-> [wrap-params wrap-output wrap-error [wrap-forwarded base-url] wrap-sync]
          (seq auth-backends)
          (conj wrap-auth-guard))
        :blaze/context-path context-path}
       [""
        (cond-> {}
          (some? search-system-handler)
          (assoc :get {:middleware [[wrap-search-db node db-sync-timeout]
                                    wrap-link-headers]
                       :handler search-system-handler})
          (some? transaction-handler)
          (assoc :post {:middleware
                        [wrap-resource
                         [wrap-batch-handler batch-handler-promise]]
                        :handler transaction-handler}))]
       ["/metadata"
        {:get capabilities-handler}]
       ["/_history"
        (cond-> {}
          (some? history-system-handler)
          (assoc :get {:middleware [[wrap-db node db-sync-timeout]
                                    wrap-link-headers]
                       :handler history-system-handler}))]
       ["/__page"
        (cond-> {:name :page}
          (some? search-system-handler)
          (assoc
            :get {:middleware [[wrap-snapshot-db node db-sync-timeout]
                               wrap-link-headers]
                  :handler search-system-handler}
            :post {:middleware [[wrap-snapshot-db node db-sync-timeout]
                                wrap-link-headers]
                   :handler search-system-handler}))]]
      (into
        (mapcat (partial operation-system-handler-route context))
        operations)
      (into
        (mapcat (partial operation-type-handler-route context))
        operations)
      (into
        (mapcat (partial operation-instance-handler-route context))
        operations)
      (into
        (keep (partial resource-route context resource-patterns))
        structure-definitions)
      (into (map (partial compartment-route context)) compartments)))
