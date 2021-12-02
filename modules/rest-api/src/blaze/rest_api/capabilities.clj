(ns blaze.rest-api.capabilities
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.search-param-registry.spec]
    [blaze.fhir.spec.type :as type]
    [blaze.rest-api.spec]
    [blaze.rest-api.util :as u]
    [blaze.spec]
    [reitit.ring]
    [reitit.ring.spec]
    [ring.util.response :as ring]))


(def ^:private quantity-documentation
  #fhir/markdown"Decimal values are truncated at two digits after the decimal point.")


(defn- capability-resource
  {:arglists '([resource-patterns operations search-param-registry structure-definition])}
  [resource-patterns operations search-param-registry
   {:keys [name] :as structure-definition}]
  (when-let
    [{:blaze.rest-api.resource-pattern/keys [interactions]}
     (u/resolve-pattern resource-patterns structure-definition)]
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
         #fhir/markdown"Copyright 2019 - 2021 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
         :kind #fhir/code"instance"
         :date #fhir/dateTime"2021-12-02"
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
               u/structure-definition-filter
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
