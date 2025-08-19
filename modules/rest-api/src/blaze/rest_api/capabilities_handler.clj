(ns blaze.rest-api.capabilities-handler
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
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
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.spec]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.ring]
   [reitit.ring.spec]
   [ring.util.response :as ring]))

(set! *warn-on-reflection* true)

(def ^:private copyright
  #fhir/markdown"Copyright 2019 - 2025 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.")

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
      (cond->
       {:fhir/type :fhir.CapabilityStatement.rest/resource
        :type (type/code name)
        :profile (type/canonical url)
        :interaction
        (reduce
         (fn [res code]
           (if-let
            [{:blaze.rest-api.interaction/keys [doc]} (get interactions code)]
             (conj
              res
              (cond-> {:fhir/type :fhir.CapabilityStatement.rest.resource/interaction
                       :code (type/code (clojure.core/name code))}
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
             (cond->
              {:fhir/type :fhir.CapabilityStatement.rest.resource/searchParam
               :name name :type (type/code type)}
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
               (cond->
                {:fhir/type :fhir.CapabilityStatement.rest.resource/operation
                 :name code
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
     history-system-handler
     operations]
    fhir-version :fhir/version
    :as config}]
  (let [operations (into
                    []
                    (keep
                     (fn [{:blaze.rest-api.operation/keys
                           [code def-uri system-handler documentation]}]
                       (when system-handler
                         (cond->
                          {:fhir/type :fhir.CapabilityStatement.rest/operation
                           :name code
                           :definition (type/canonical def-uri)}
                           documentation
                           (assoc :documentation (type/->Markdown documentation))))))
                    operations)]
    {:fhir/type :fhir/CapabilityStatement
     :status #fhir/code"active"
     :experimental false
     :publisher "The Samply Community"
     :copyright copyright
     :kind #fhir/code"instance"
     :date (type/dateTime release-date)
     :software
     {:fhir/type :fhir.CapabilityStatement/software
      :name "Blaze"
      :version version
      :releaseDate (type/dateTime release-date)}
     :implementation
     {:fhir/type :fhir.CapabilityStatement/implementation
      :description "Blaze"}
     :fhirVersion (type/code fhir-version)
     :format
     [#fhir/code"application/fhir+json"
      #fhir/code"application/fhir+xml"]
     :rest
     [(cond->
       {:fhir/type :fhir.CapabilityStatement/rest
        :mode #fhir/code"server"
        :resource
        (into
         []
         (keep (partial capability-resource config))
         (sdr/resources structure-definition-repo))
        :interaction
        (cond-> []
          (some? search-system-handler)
          (conj {:fhir/type :fhir.CapabilityStatement.rest/interaction
                 :code #fhir/code"search-system"})
          (some? transaction-handler-active?)
          (conj {:fhir/type :fhir.CapabilityStatement.rest/interaction
                 :code #fhir/code"transaction"}
                {:fhir/type :fhir.CapabilityStatement.rest/interaction
                 :code #fhir/code"batch"})
          (some? history-system-handler)
          (conj {:fhir/type :fhir.CapabilityStatement.rest/interaction
                 :code #fhir/code"history-system"}))
        :searchParam
        [{:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_id"
          :type "token"
          :definition "http://hl7.org/fhir/SearchParameter/Resource-id"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_lastUpdated"
          :type "date"
          :definition "http://hl7.org/fhir/SearchParameter/Resource-lastUpdated"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_profile"
          :type "uri"
          :definition "http://hl7.org/fhir/SearchParameter/Resource-profile"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_security"
          :type "token"
          :definition "http://hl7.org/fhir/SearchParameter/Resource-security"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_source"
          :type "uri"
          :definition "http://hl7.org/fhir/SearchParameter/Resource-source"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_tag"
          :type "token"
          :definition "http://hl7.org/fhir/SearchParameter/Resource-tag"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_list"
          :type "special"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_has"
          :type "special"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_include"
          :type "special"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_revinclude"
          :type "special"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_count"
          :type "number"
          :documentation "The number of resources returned per page"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_elements"
          :type "special"}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_sort"
          :type "special"
          :documentation "Only `_id`, `_lastUpdated` and `-_lastUpdated` are supported at the moment."}
         {:fhir/type :fhir.CapabilityStatement.rest/searchParam
          :name "_summary"
          :type "token"
          :documentation "Only `count` is supported at the moment."}]
        :compartment ["http://hl7.org/fhir/CompartmentDefinition/patient"]}
        (seq operations) (assoc :operation operations))]}))

(defn- build-terminology-capabilities-base
  [{:keys [version release-date]}]
  {:fhir/type :fhir/TerminologyCapabilities
   :meta #fhir/Meta{:profile [#fhir/canonical"http://hl7.org/fhir/StructureDefinition/TerminologyCapabilities"]}
   :status #fhir/code"active"
   :experimental false
   :publisher "The Samply Community"
   :copyright copyright
   :kind #fhir/code"instance"
   :date (type/dateTime release-date)
   :software
   {:fhir/type :fhir.TerminologyCapabilities/software
    :name "Blaze"
    :version version
    :releaseDate (type/dateTime release-date)}
   :implementation
   {:fhir/type :fhir.TerminologyCapabilities/implementation
    :description "Blaze"}
   :validateCode
   {:fhir/type :fhir.TerminologyCapabilities/validateCode
    :translations #fhir/boolean false}})

(defn- context
  [{:keys [context-path terminology-service] :or {context-path ""} :as config}]
  {:context-path context-path
   :capability-statement-base (build-capability-statement-base config)
   :terminology-capabilities-base (build-terminology-capabilities-base config)
   :terminology-service terminology-service})

(defn- assoc-implementation-url [capability-statement-base context-path base-url]
  (assoc-in capability-statement-base [:implementation :url]
            (type/->Url (str base-url context-path))))

(defn- profile-canonical [{:keys [url version]}]
  (let [version (type/value version)]
    (type/canonical (cond-> (type/value url) version (str "|" version)))))

(defn- supported-profiles-query [db type]
  (d/type-query db "StructureDefinition" [["type" type] ["derivation" "constraint"]]))

(defn- supported-profiles [db type]
  (do-sync [profiles (d/pull-many db (supported-profiles-query db type))]
    (mapv profile-canonical profiles)))

(defn- assoc-supported-profiles** [db {:keys [type] :as resource}]
  (do-sync [supported-profiles (supported-profiles db (type/value type))]
    (cond-> resource
      (seq supported-profiles) (assoc :supportedProfile supported-profiles))))

(defn- assoc-supported-profiles* [db resources]
  (let [futures (mapv (partial assoc-supported-profiles** db) resources)]
    (do-sync [_ (ac/all-of futures)]
      (mapv ac/join futures))))

(defn- assoc-supported-profiles [{[{:keys [resource]}] :rest :as capability-statement-base} db]
  (do-sync [resource (assoc-supported-profiles* db resource)]
    (assoc-in capability-statement-base [:rest 0 :resource] resource)))

(defn- final-capability-statement
  [{:keys [capability-statement-base context-path]} db base-url elements]
  (let [cs (assoc-implementation-url capability-statement-base context-path base-url)]
    (if (seq elements)
      (if (some #{:rest} elements)
        (do-sync [cs (assoc-supported-profiles cs db)]
          (select-keys cs (conj elements :fhir/type)))
        (ac/completed-future (select-keys cs (conj elements :fhir/type))))
      (assoc-supported-profiles cs db))))

(defn- final-terminology-capabilities
  [{:keys [context-path terminology-service terminology-capabilities-base]}
   base-url]
  (do-sync [code-systems (ts/code-systems terminology-service)]
    (cond->
     (assoc-in
      terminology-capabilities-base
      [:implementation :url]
      (type/->Url (str base-url context-path)))
      code-systems (assoc :codeSystem code-systems))))

(defn- final-capabilities [context db base-url mode elements]
  (if (= "terminology" mode)
    (final-terminology-capabilities context base-url)
    (final-capability-statement context db base-url elements)))

(defn- tag [capabilities]
  (Integer/toHexString (hash capabilities)))

(defn response [{:strs [if-none-match]} capabilities]
  (let [tag (tag capabilities)]
    (-> (if ((header/if-none-match->tags if-none-match) tag)
          (ring/status 304)
          (ring/response capabilities))
        (ring/header "ETag" (format "W/\"%s\"" tag)))))

(defmethod m/pre-init-spec ::rest-api/capabilities-handler [_]
  (s/keys
   :req [:fhir/version]
   :req-un
   [:blaze/version
    :blaze/release-date
    :blaze.fhir/structure-definition-repo
    :blaze.db/search-param-registry]
   :opt-un
   [:blaze/context-path
    ::rest-api/search-system-handler
    ::rest-api/transaction-handler-active?
    ::rest-api/history-system-handler
    ::rest-api/resource-patterns
    ::rest-api/operations
    :blaze.db/enforce-referential-integrity
    :blaze.db/allow-multiple-delete
    :blaze/terminology-service]))

(defmethod ig/init-key ::rest-api/capabilities-handler
  [_ config]
  (let [context (context config)
        mode (if (:terminology-service context)
               #(get % "mode")
               (constantly nil))]
    (fn [{:keys [query-params headers] :blaze/keys [db base-url]}]
      (let [elements (fhir-util/elements query-params)
            mode (mode query-params)]
        (do-sync [capabilities (final-capabilities context db base-url mode elements)]
          (response headers capabilities))))))
