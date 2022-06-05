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
  {:arglists '([context structure-definition])}
  [{:keys [resource-patterns operations search-param-registry
           enforce-referential-integrity]
    :or {enforce-referential-integrity true}}
   {:keys [name] :as structure-definition}]
  (when-let
    [{:blaze.rest-api.resource-pattern/keys [interactions]}
     (u/resolve-pattern resource-patterns structure-definition)]
    (let [operations
          (filter
            #(some #{name} (:blaze.rest-api.operation/resource-types %))
            operations)]
      (cond->
        {:type (type/code name)
         :interaction
         (reduce
           (fn [res code]
             (if-let
               [{:blaze.rest-api.interaction/keys [doc]} (get interactions code)]
               (conj
                 res
                 (cond->
                   {:code (type/code (clojure.core/name code))}
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
         (cond->
           [#fhir/code"literal"
            #fhir/code"local"]
           enforce-referential-integrity
           (conj #fhir/code"enforced"))
         :searchRevInclude
         (into
           []
           (mapcat
             (fn [{:keys [base code]}]
               (map #(str % ":" code) base)))
           (sr/list-by-target search-param-registry name))
         :searchParam
         (into
           []
           (map
             (fn [{:keys [name url type]}]
               (cond-> {:name name :type (type/code type)}
                 url
                 (assoc :definition (type/canonical url))
                 (= "quantity" type)
                 (assoc :documentation quantity-documentation))))
           (sr/list-by-type search-param-registry name))}

        (seq operations)
        (assoc
          :operation
          (into
            []
            (keep
              (fn [{:blaze.rest-api.operation/keys
                    [code def-uri type-handler instance-handler]}]
                (when (or type-handler instance-handler)
                  {:name code
                   :definition (type/canonical def-uri)})))
            operations))))))


(defn capabilities-handler
  {:arglists '([context])}
  [{:keys
    [version
     context-path
     structure-definitions
     search-system-handler
     transaction-handler
     history-system-handler]
    :or {context-path ""}
    :as context}]
  (let [capability-statement
        {:fhir/type :fhir/CapabilityStatement
         :status #fhir/code"active"
         :experimental false
         :publisher "The Samply Community"
         :copyright
         #fhir/markdown"Copyright 2019 - 2022 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
         :kind #fhir/code"instance"
         :date #fhir/dateTime"2022-06-05"
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
             (keep (partial capability-resource context))
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
