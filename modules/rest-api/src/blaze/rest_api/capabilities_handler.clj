(ns blaze.rest-api.capabilities-handler
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.fhir.structure-definition-repo.spec]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.module :as m]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.header :as header]
   [blaze.rest-api.spec]
   [blaze.rest-api.util :as u]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.ring]
   [reitit.ring.spec]
   [ring.util.response :as ring]))

(set! *warn-on-reflection* true)

(def ^:private quantity-documentation
  #fhir/markdown"Decimal values are truncated at two digits after the decimal point.")

(defn- capability-resource
  {:arglists '([context structure-definition])}
  [{:keys [resource-patterns operations search-param-registry
           enforce-referential-integrity allow-multiple-delete]
    :or {enforce-referential-integrity true}}
   {:keys [name url] :as structure-definition}]
  (when-let
   [{:blaze.rest-api.resource-pattern/keys [interactions]}
    (u/resolve-pattern resource-patterns structure-definition)]
    (let [operations
          (filter
           #(some #{name} (:blaze.rest-api.operation/resource-types %))
           operations)]
      (cond-> {:type (type/code name)
               :profile (type/canonical url)
               :interaction
               (reduce
                (fn [res code]
                  (if-let
                   [{:blaze.rest-api.interaction/keys [doc]} (get interactions code)]
                    (conj
                     res
                     (cond-> {:code (type/code (clojure.core/name code))}
                       doc (assoc :documentation (type/->Markdown doc))))
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
               :versioning #fhir/code"versioned-update"
               :readHistory true
               :updateCreate true
               :conditionalCreate true
               :conditionalRead #fhir/code"not-supported"
               :conditionalUpdate false
               :conditionalDelete
               (if allow-multiple-delete
                 #fhir/code"multiple"
                 #fhir/code"single")
               :referencePolicy
               (cond-> [#fhir/code"literal"
                        #fhir/code"local"]
                 enforce-referential-integrity (conj #fhir/code"enforced"))
               :searchInclude
               (into
                []
                (comp
                 (filter (comp #{"reference"} :type))
                 (mapcat
                  (fn [{:keys [code target]}]
                    (cons
                     (str name ":" code)
                     (for [target target]
                       (str name ":" code ":" target))))))
                (sr/list-by-type search-param-registry name))
               :searchRevInclude
               (into
                []
                (mapcat
                 (fn [{:keys [base code]}]
                   (map #(str % ":" code) base)))
                (sr/list-by-target-type search-param-registry name))
               :searchParam
               (into
                []
                (comp
                 (remove (comp #{"_id" "_lastUpdated" "_profile" "_security" "_source" "_tag" "_list" "_has"} :name))
                 (map
                  (fn [{:keys [name url type]}]
                    (cond-> {:name name :type (type/code type)}
                      url
                      (assoc :definition (type/canonical url))
                      (= "quantity" type)
                      (assoc :documentation quantity-documentation)))))
                (sr/list-by-type search-param-registry name))}

        (seq operations)
        (assoc
         :operation
         (into
          []
          (keep
           (fn [{:blaze.rest-api.operation/keys
                 [code def-uri type-handler instance-handler documentation]}]
             (when (or type-handler instance-handler)
               (cond-> {:name code
                        :definition (type/canonical def-uri)}
                 documentation
                 (assoc :documentation (type/->Markdown documentation))))))
          operations))))))

(defn- build-capability-statement-base
  "Builds the base for the capability statement that is independent of the
  request.

  Building the capability statement in this two stage process is a performance
  improvement."
  [{:keys
    [version
     release-date
     structure-definition-repo
     search-system-handler
     transaction-handler-active?
     history-system-handler]
    :as context}]
  {:fhir/type :fhir/CapabilityStatement
   :status #fhir/code"active"
   :experimental false
   :publisher "The Samply Community"
   :copyright
   #fhir/markdown"Copyright 2019 - 2024 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
   :kind #fhir/code"instance"
   :date (type/dateTime release-date)
   :software
   {:name "Blaze"
    :version version
    :releaseDate (type/dateTime release-date)}
   :implementation
   {:description "Blaze"}
   :fhirVersion #fhir/code"4.0.1"
   :format
   [#fhir/code"application/fhir+json"
    #fhir/code"application/fhir+xml"]
   :rest
   [{:mode #fhir/code"server"
     :resource
     (into
      []
      (keep (partial capability-resource context))
      (sdr/resources structure-definition-repo))
     :interaction
     (cond-> []
       (some? search-system-handler)
       (conj {:code #fhir/code"search-system"})
       (some? transaction-handler-active?)
       (conj {:code #fhir/code"transaction"} {:code #fhir/code"batch"})
       (some? history-system-handler)
       (conj {:code #fhir/code"history-system"}))
     :searchParam
     [{:name "_id"
       :type "token"
       :definition "http://hl7.org/fhir/SearchParameter/Resource-id"}
      {:name "_lastUpdated"
       :type "date"
       :definition "http://hl7.org/fhir/SearchParameter/Resource-lastUpdated"}
      {:name "_profile"
       :type "uri"
       :definition "http://hl7.org/fhir/SearchParameter/Resource-profile"}
      {:name "_security"
       :type "token"
       :definition "http://hl7.org/fhir/SearchParameter/Resource-security"}
      {:name "_source"
       :type "uri"
       :definition "http://hl7.org/fhir/SearchParameter/Resource-source"}
      {:name "_tag"
       :type "token"
       :definition "http://hl7.org/fhir/SearchParameter/Resource-tag"}
      {:name "_list"
       :type "special"}
      {:name "_has"
       :type "special"}
      {:name "_include"
       :type "special"}
      {:name "_revinclude"
       :type "special"}
      {:name "_count"
       :type "number"
       :documentation "The number of resources returned per page"}
      {:name "_elements"
       :type "special"}
      {:name "_sort"
       :type "special"
       :documentation "Only `_id`, `_lastUpdated` and `-_lastUpdated` are supported at the moment."}
      {:name "_summary"
       :type "token"
       :documentation "Only `count` is supported at the moment."}]
     :compartment ["http://hl7.org/fhir/CompartmentDefinition/patient"]}]})

(defn- final-capability-statement
  [capability-statement base-url context-path elements]
  (cond-> (assoc-in
           capability-statement
           [:implementation :url]
           (type/->Url (str base-url context-path)))
    (seq elements) (select-keys (conj elements :fhir/type))))

(defn- tag [capability-statement]
  (Integer/toHexString (hash capability-statement)))

(defn response [{:strs [if-none-match]} capability-statement]
  (let [tag (tag capability-statement)]
    (-> (if ((header/if-none-match->tags if-none-match) tag)
          (ring/status 304)
          (ring/response capability-statement))
        (ring/header "ETag" (format "W/\"%s\"" tag)))))

(defmethod m/pre-init-spec ::rest-api/capabilities-handler [_]
  (s/keys
   :req-un
   [:blaze/version
    :blaze/release-date
    :blaze.fhir/structure-definition-repo
    :blaze.db/search-param-registry]
   :opt-un
   [:blaze/context-path
    ::search-system-handler
    ::transaction-handler-active?
    ::history-system-handler
    ::resource-patterns
    ::operations
    :blaze.db/enforce-referential-integrity
    :blaze.db/allow-multiple-delete]))

(defmethod ig/init-key ::rest-api/capabilities-handler
  [_ {:keys [context-path] :or {context-path ""} :as config}]
  (let [capability-statement-base (build-capability-statement-base config)]
    (fn [{:keys [query-params headers] :blaze/keys [base-url]}]
      (let [elements (fhir-util/elements query-params)
            capability-statement (final-capability-statement
                                  capability-statement-base base-url
                                  context-path elements)]
        (-> (response headers capability-statement)
            (ac/completed-future))))))
