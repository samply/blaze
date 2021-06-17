(ns blaze.rest-api
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.search-param-registry.spec]
    [blaze.executors :as ex]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :as metrics]
    [blaze.module :refer [reg-collector]]
    [blaze.rest-api.middleware.auth-guard :as auth-guard]
    [blaze.rest-api.middleware.batch-handler :as batch-handler]
    [blaze.rest-api.middleware.cors :as cors]
    [blaze.rest-api.middleware.forwarded :as forwarded]
    [blaze.rest-api.middleware.log :refer [wrap-log]]
    [blaze.rest-api.middleware.output :as output :refer [wrap-output]]
    [blaze.rest-api.middleware.resource :as resource]
    [blaze.rest-api.spec]
    [blaze.spec]
    [buddy.auth.middleware :refer [wrap-authentication]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [reitit.ring]
    [reitit.ring.spec]
    [ring.middleware.params :as ring-params]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


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


(def ^:private wrap-cors
  {:name :cors
   :wrap cors/wrap-cors})


(def ^:private wrap-resource
  {:name :resource
   :wrap resource/wrap-resource})


(defn resolve-pattern
  "Tries to find a resource pattern in `resource-patterns` according to the
  name of the `structure-definition`.

  Falls back to the :default resource pattern if there is any."
  {:arglists '([resource-patterns structure-definition])}
  [resource-patterns {:keys [name]}]
  (or
    (some
      #(when (= name (:blaze.rest-api.resource-pattern/type %)) %)
      resource-patterns)
    (some
      #(when (= :default (:blaze.rest-api.resource-pattern/type %)) %)
      resource-patterns)))


(defn resource-route
  "Builds routes for one resource according to `structure-definition`.

  Returns nil if the resource has no match in `resource-patterns`.

  Route data contains the resource type under :fhir.resource/type."
  {:arglists '([auth-backends parse-executor resource-patterns structure-definition])}
  [auth-backends parse-executor resource-patterns {:keys [name] :as structure-definition}]
  (when-let
    [{:blaze.rest-api.resource-pattern/keys [interactions]}
     (resolve-pattern resource-patterns structure-definition)]
    [(str "/" name)
     {:middleware
      (cond-> []
        (seq auth-backends)
        (conj wrap-auth-guard))
      :fhir.resource/type name}
     [""
      (cond-> {:name (keyword name "type")}
        (contains? interactions :search-type)
        (assoc :get (-> interactions :search-type
                        :blaze.rest-api.interaction/handler))
        (contains? interactions :create)
        (assoc :post {:middleware [[wrap-resource parse-executor]]
                      :handler (-> interactions :create
                                   :blaze.rest-api.interaction/handler)}))]
     ["/_history"
      (cond-> {:conflicting true}
        (contains? interactions :history-type)
        (assoc :get (-> interactions :history-type
                        :blaze.rest-api.interaction/handler)))]
     ["/_search"
      (cond-> {:conflicting true}
        (contains? interactions :search-type)
        (assoc :post (-> interactions :search-type
                         :blaze.rest-api.interaction/handler)))]
     ["/{id}"
      [""
       (cond->
         {:name (keyword name "instance")
          :conflicting true}
         (contains? interactions :read)
         (assoc :get (-> interactions :read
                         :blaze.rest-api.interaction/handler))
         (contains? interactions :update)
         (assoc :put {:middleware [[wrap-resource parse-executor]]
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
          (assoc :get (-> interactions :history-instance
                          :blaze.rest-api.interaction/handler)))]
       ["/{vid}"
        (cond-> {:name (keyword name "versioned-instance")}
          (contains? interactions :vread)
          (assoc :get (-> interactions :vread
                          :blaze.rest-api.interaction/handler)))]]]]))


(defn- compartment-route
  {:arglists '([auth-backends compartment])}
  [auth-backends {:blaze.rest-api.compartment/keys [code search-handler]}]
  [(format "/%s/{id}/{type}" code)
   {:name (keyword code "compartment")
    :fhir.compartment/code code
    :conflicting true
    :middleware
    (cond-> []
      (seq auth-backends)
      (conj wrap-auth-guard))
    :get search-handler}])


(def structure-definition-filter
  (comp
    (filter (comp #{"resource"} :kind))
    (remove :experimental)
    (remove :abstract)))


(defn routes
  {:arglists '([config capabilities-handler batch-handler-promise])}
  [{:keys
    [base-url
     context-path
     structure-definitions
     auth-backends
     search-system-handler
     transaction-handler
     history-system-handler
     resource-patterns
     compartments
     operations]
    :blaze.rest-api.json-parse/keys [executor]
    :or {context-path ""}}
   capabilities-handler
   batch-handler-promise]
  (-> [""
       {:middleware [wrap-params [wrap-forwarded base-url]]
        :blaze/context-path context-path}
       [""
        (cond->
          {:middleware
           (cond-> []
             (seq auth-backends)
             (conj wrap-auth-guard))}
          (some? search-system-handler)
          (assoc :get search-system-handler)
          (some? transaction-handler)
          (assoc :post {:middleware
                        [[wrap-resource executor]
                         [wrap-batch-handler batch-handler-promise]]
                        :handler transaction-handler}))]
       ["/metadata"
        {:get capabilities-handler}]
       ["/_history"
        (cond->
          {:middleware
           (cond-> []
             (seq auth-backends)
             (conj wrap-auth-guard))}
          (some? history-system-handler)
          (assoc :get history-system-handler))]]
      (into
        (mapcat
          (fn [{:blaze.rest-api.operation/keys [code system-handler]}]
            (when system-handler
              [[(str "/$" code)
                {:middleware
                 (cond-> []
                   (seq auth-backends)
                   (conj wrap-auth-guard))
                 :get system-handler
                 :post system-handler}]])))
        operations)
      (into
        (mapcat
          (fn [{:blaze.rest-api.operation/keys
                [code resource-types type-handler]}]
            (when type-handler
              (map
                (fn [resource-type]
                  [(str "/" resource-type "/$" code)
                   {:conflicting true
                    :middleware
                    (cond-> []
                      (seq auth-backends)
                      (conj wrap-auth-guard))
                    :get type-handler
                    :post type-handler}])
                resource-types))))
        operations)
      (into
        (mapcat
          (fn [{:blaze.rest-api.operation/keys
                [code resource-types instance-handler]}]
            (when instance-handler
              (map
                (fn [resource-type]
                  [(str "/" resource-type "/{id}/$" code)
                   {:middleware
                    (cond-> []
                      (seq auth-backends)
                      (conj wrap-auth-guard))
                    :get instance-handler
                    :post instance-handler}])
                resource-types))))
        operations)
      (into
        (comp
          structure-definition-filter
          (keep #(resource-route auth-backends executor resource-patterns %)))
        structure-definitions)
      (into
        (map #(compartment-route auth-backends %))
        compartments)))


(defn batch-handler
  "Handler for individual requests of a batch request."
  [routes context-path]
  (reitit.ring/ring-handler
    (reitit.ring/router
      routes
      {:path context-path
       :syntax :bracket
       :reitit.middleware/transform
       (fn [middleware]
         (filterv (comp not #{:resource} :name) middleware))})
    handler-util/default-handler))


(defn router
  {:arglists '([config capabilities-handler])}
  [{:keys [context-path] :or {context-path ""} :as config} capabilities-handler]
  (let [batch-handler-promise (promise)
        routes (routes config capabilities-handler batch-handler-promise)]
    (deliver batch-handler-promise (batch-handler routes context-path))
    (reitit.ring/router
      routes
      {:path context-path
       :syntax :bracket
       :reitit.ring/default-options-endpoint
       {:no-doc true
        :handler
        (fn [{::reitit/keys [match]}]
          (let [methods (->> match :result (keep (fn [[k v]] (when v k))))
                allowed-methods
                (->> methods (map (comp str/upper-case name)) (str/join ","))]
            (-> (ring/response {})
                (ring/header "Access-Control-Allow-Methods" allowed-methods)
                (ring/header "Access-Control-Allow-Headers" "content-type"))))}})))


(def ^:private quantity-documentation
  #fhir/markdown"Decimal values are truncated at two digits after the decimal point.")


(defn- capability-resource
  {:arglists '([resource-patterns operations search-param-registry structure-definition])}
  [resource-patterns operations search-param-registry
   {:keys [name] :as structure-definition}]
  (when-let
    [{:blaze.rest-api.resource-pattern/keys [interactions]}
     (resolve-pattern resource-patterns structure-definition)]
    (let [operations
          (filter
            #(some #{name} (:blaze.rest-api.operation/resource-types %))
            operations)]
      (cond->
        {:type (type/->Code name)
         :interaction
         (reduce
           (fn [res code]
             (if-let
               [{:blaze.rest-api.interaction/keys [doc]} (get interactions code)]
               (conj
                 res
                 (cond->
                   {:code (type/->Code (clojure.core/name code))}
                   doc
                   (assoc :documentation (type/->Markdown doc))))
               res))
           []
           [:read
            :vread
            :update
            :delete
            :history-instance
            :history-type
            :create
            :search-type])
         :versioning #fhir/code"versioned"
         :readHistory true
         :updateCreate true
         :conditionalCreate true
         :conditionalRead #fhir/code"not-supported"
         :conditionalUpdate false
         :conditionalDelete #fhir/code"not-supported"
         :referencePolicy
         [#fhir/code"literal"
          #fhir/code"enforced"
          #fhir/code"local"]
         :searchParam
         (transduce
           (map
             (fn [{:keys [name url type]}]
               (cond-> {:name name :type (type/->Code type)}
                 url
                 (assoc :definition (type/->Canonical url))
                 (= "quantity" type)
                 (assoc :documentation quantity-documentation))))
           conj
           []
           (sr/list-by-type search-param-registry name))}

        (seq operations)
        (assoc
          :operation
          (into
            []
            (mapcat
              (fn [{:blaze.rest-api.operation/keys
                    [code def-uri type-handler instance-handler]}]
                (when (or type-handler instance-handler)
                  [{:name code
                    :definition (type/->Canonical def-uri)}])))
            operations))))))


(defn capabilities-handler
  [{:keys
    [version
     context-path
     structure-definitions
     search-param-registry
     search-system-handler
     transaction-handler
     history-system-handler
     resource-patterns
     operations]
    :or {context-path ""}}]
  (let [capability-statement
        {:fhir/type :fhir/CapabilityStatement
         :status #fhir/code"active"
         :experimental false
         :publisher "The Samply Community"
         :copyright
         #fhir/markdown"Copyright 2019 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
         :kind #fhir/code"instance"
         :date #fhir/dateTime"2021-04-27"
         :software
         {:name "Blaze"
          :version version}
         :implementation
         {:description "Blaze"}
         :fhirVersion #fhir/code"4.0.1"
         :format
         [#fhir/code"application/fhir+json"
          #fhir/code"application/xml+json"]
         :rest
         [{:mode #fhir/code"server"
           :resource
           (into
             []
             (comp
               structure-definition-filter
               (keep #(capability-resource resource-patterns operations search-param-registry %)))
             structure-definitions)
           :interaction
           (cond-> []
             (some? search-system-handler)
             (conj {:code #fhir/code"search-system"})
             (some? transaction-handler)
             (conj {:code #fhir/code"transaction"} {:code #fhir/code"batch"})
             (some? history-system-handler)
             (conj {:code #fhir/code"history-system"}))}]}]
    (fn [{:blaze/keys [base-url]}]
      (ac/completed-future
        (ring/response
          (assoc-in
            capability-statement
            [:implementation :url]
            (type/->Url (str base-url context-path))))))))


(defn handler
  "Whole app Ring handler."
  [{:keys [auth-backends] :as config}]
  (-> (reitit.ring/ring-handler
        (router config (capabilities-handler config))
        handler-util/default-handler
        {:middleware
         (cond-> [wrap-cors]
           (seq auth-backends)
           (conj #(apply wrap-authentication % auth-backends)))})
      (wrap-output)
      (ring-params/wrap-params)
      (wrap-log)))


(defn- json-parse-executor-init-msg []
  (format "Init JSON parse executor with %d threads"
          (.availableProcessors (Runtime/getRuntime))))


(defmethod ig/init-key :blaze.rest-api.json-parse/executor
  [_ _]
  (log/info (json-parse-executor-init-msg))
  (ex/cpu-bound-pool "blaze-json-parse-%d"))


(derive :blaze.rest-api.json-parse/executor :blaze.metrics/thread-pool-executor)


(defmethod ig/pre-init-spec :blaze/rest-api [_]
  (s/keys
    :req
    [:blaze.rest-api.json-parse/executor]
    :req-un
    [:blaze/base-url
     ::version
     :blaze.rest-api/structure-definitions
     :blaze.db/search-param-registry]
    :opt-un
    [::context-path
     ::auth-backends
     ::search-system-handler
     ::transaction-handler
     ::history-system-handler
     ::resource-patterns
     ::operations]))


(defmethod ig/init-key :blaze/rest-api
  [_ {:keys [base-url context-path] :as config}]
  (log/info "Init FHIR RESTful API with base URL:" (str base-url context-path))
  (handler config))


(reg-collector ::requests-total
  metrics/requests-total)


(reg-collector ::request-duration-seconds
  metrics/request-duration-seconds)


(reg-collector ::parse-duration-seconds
  resource/parse-duration-seconds)


(reg-collector ::generate-duration-seconds
  output/generate-duration-seconds)
