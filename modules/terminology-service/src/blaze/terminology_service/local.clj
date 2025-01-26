(ns blaze.terminology-service.local
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.module :as m]
   [blaze.spec]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.local.capabilities :as c]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.code-system.bcp-13 :as bcp-13]
   [blaze.terminology-service.local.code-system.loinc :as loinc]
   [blaze.terminology-service.local.code-system.sct :as sct]
   [blaze.terminology-service.local.code-system.ucum :as ucum]
   [blaze.terminology-service.local.spec]
   [blaze.terminology-service.local.value-set :as vs]
   [blaze.terminology-service.local.value-set.expand :as vs-expand]
   [blaze.terminology-service.local.value-set.validate-code :as vs-validate-code]
   [blaze.terminology-service.protocols :as p]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [muuntaja.parse :as parse]
   [taoensso.timbre :as log])
  (:import
   [com.google.common.base CaseFormat]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- plural [s]
  (if (str/ends-with? s "y")
    (str (subs s 0 (dec (count s))) "ies")
    (str s "s")))

(defn- assoc-via [params {:keys [cardinality]} name value]
  (if (identical? :many cardinality)
    (update params (keyword (plural (camel->kebab name))) (fnil into []) (if (sequential? value) value [value]))
    (assoc params (keyword (camel->kebab name)) value)))

(defn- validate-params [specs {params :parameter}]
  (reduce
   (fn [new-params {:keys [name] :as param}]
     (let [name (type/value name)]
       (if-let [{:keys [action] :as spec} (specs name)]
         (case action
           :copy
           (assoc-via new-params spec name (type/value (:value param)))

           :parse-nat-long
           (let [value (type/value (:value param))]
             (if-not (neg? value)
               (assoc-via new-params spec name value)
               (reduced (ba/incorrect (format "Invalid value for parameter `%s`. Has to be a non-negative integer." name)))))

           :parse
           (assoc-via new-params spec name ((:parse spec) (type/value (:value param))))

           :parse-canonical
           (assoc-via new-params spec name (:value param))

           :copy-complex-type
           (assoc-via new-params spec name (:value param))

           :copy-resource
           (assoc-via new-params spec name (:resource param))

           (reduced (ba/unsupported (format "Unsupported parameter `%s`." name)
                                    :http/status 400)))
         new-params)))
   {}
   params))

(defn- check-url-system [url system url-param-name system-param-name]
  (when-not (= url (or system url))
    (ba/incorrect (format "Parameter `%s` differs from parameter `%s`."
                          url-param-name system-param-name))))

(defn- check-version-version [version-1 version-2 param-name-1 param-name-2]
  (cond
    (nil? version-1) version-2
    (nil? version-2) version-1
    (= version-1 version-2)
    version-1
    :else
    (ba/incorrect (format "Parameter `%s` differs from parameter `%s`."
                          param-name-1 param-name-2))))

(defn- cs-coding-clause [{:keys [code display]} origin]
  (if-let [code (type/value code)]
    (cond-> {:code code :origin origin}
      (type/value display) (assoc :display (type/value display)))
    (ba/incorrect "Missing required parameter `coding.code`.")))

(defn- cs-validate-code-more
  "Tries to extract :url, :version and :clause from `params`."
  [{:keys [url code-system code coding codeable-concept display] :as params}]
  (if-not (and url code-system)
    (cond
      code
      (if (or url code-system)
        (assoc params :clause (cond-> {:code code} display (assoc :display display)))
        (ba/incorrect "Missing both parameters `url` and `codeSystem`."))

      coding
      (let [system (-> coding :system type/value)
            version (-> coding :version type/value)]
        (if code-system
          (when-ok [clause (cs-coding-clause coding "coding")]
            (assoc params :clause clause))
          (if-let [url (or url system)]
            (when-ok [_ (check-url-system url system "url" "coding.system")
                      version (check-version-version (:version params) version "version" "coding.version")
                      clause (cs-coding-clause coding "coding")]
              (cond-> (assoc params :url url :clause clause)
                version (assoc :version version)))
            (ba/incorrect "Missing all of the parameters `url`, `coding.system` and `codeSystem`."))))

      codeable-concept
      (let [{[fist-coding :as codings] :coding} codeable-concept]
        (condp = (count codings)
          1 (let [system (-> fist-coding :system type/value)
                  version (-> fist-coding :version type/value)]
              (if code-system
                (when-ok [clause (cs-coding-clause fist-coding "codeableConcept")]
                  (assoc params :clause clause))
                (if-let [url (or url system)]
                  (when-ok [_ (check-url-system url system "url" "codeableConcept.coding[0].system")
                            version (check-version-version (:version params) version "version" "codeableConcept.coding[0].version")
                            clause (cs-coding-clause fist-coding "codeableConcept")]
                    (cond-> (assoc params :url url :clause clause)
                      version (assoc :version version)))
                  (ba/incorrect "Missing all of the parameters `url`, `codeableConcept.coding[0].system` and `codeSystem`."))))
          0 (ba/incorrect "Incorrect parameter `codeableConcept` with no coding.")

          (ba/unsupported "Unsupported parameter `codeableConcept` with more than one coding.")))

      :else
      (ba/incorrect "Missing one of the parameters `code`, `coding` or `codeableConcept`."))
    (ba/incorrect "Both parameters `url` and `codeSystem` are given.")))

(defn- expand-vs-more
  [{:keys [url value-set offset] :as params}]
  (cond
    (when-let [offset (type/value offset)] (not= 0 offset))
    (ba/incorrect "Invalid non-zero value for parameter `offset`.")

    (and url value-set)
    (ba/incorrect "Both parameters `url` and `valueSet` are given.")

    (or url value-set)
    params

    :else
    (ba/incorrect "Missing both parameters `url` and `valueSet`.")))

(defn vs-validate-code-more [{:keys [url value-set] :as params}]
  (cond
    (and url value-set)
    (ba/incorrect "Both parameters `url` and `valueSet` are given.")

    (or url value-set)
    params

    :else
    (ba/incorrect "Missing both parameters `url` and `valueSet`.")))

(defn- find-code-system [context {:keys [url version code-system]}]
  (if code-system
    (ac/completed-future (cs/enhance context code-system))
    (if version
      (cs/find context url version)
      (cs/find context url))))

(defn- find-value-set
  [context {:keys [url value-set] version :value-set-version}]
  (if value-set
    (ac/completed-future value-set)
    (if version
      (vs/find context url version)
      (vs/find context url))))

(defn- handle-close [stage db]
  (ac/handle
   stage
   (fn [output e]
     (let [res (if e (assoc e :t (d/t db)) output)]
       (.close ^AutoCloseable db)
       res))))

(defn- context [{:keys [clock loinc sct]}]
  (cond-> {:clock clock}
    loinc (assoc :loinc/context loinc)
    sct (assoc :sct/context sct)))

(defn- ensure-code-systems
  "Ensures that all code systems of internal terminologies like Snomed CT are
  present in the database node."
  [{:keys [enable-bcp-13 enable-ucum] :as config}
   {loinc-context :loinc/context
    sct-context :sct/context}]
  (when enable-bcp-13
    @(bcp-13/ensure-code-system config))
  (when enable-ucum
    @(ucum/ensure-code-system config))
  (when loinc-context
    @(loinc/ensure-code-systems config loinc-context))
  (when sct-context
    @(sct/ensure-code-systems config sct-context)))

(def ^:private cs-validate-code-param-specs
  {"url" {:action :copy}
   "codeSystem" {:action :copy-resource}
   "code" {:action :copy}
   "version" {:action :copy}
   "display" {:action :copy}
   "coding" {:action :copy-complex-type}
   "codeableConcept" {:action :copy-complex-type}
   "date" {}
   "abstract" {}
   "displayLanguage" {:action :copy}
   "tx-resource" {:action :copy-resource :cardinality :many}})

(def ^:private vs-expand-param-specs
  {"url" {:action :copy}
   "valueSet" {:action :copy-resource}
   "valueSetVersion" {:action :copy}
   "context" {}
   "contextDirection" {}
   "filter" {}
   "date" {}
   "offset" {:action :parse-nat-long}
   "count" {:action :parse-nat-long}
   "includeDesignations" {:action :copy}
   "designation" {}
   "includeDefinition" {:action :copy}
   "activeOnly" {:action :copy}
   "useSupplement" {}
   "excludeNested" {:action :copy}
   "excludeNotForUI" {}
   "excludePostCoordinated" {}
   "displayLanguage" {:action :copy}
   "property" {:action :copy :cardinality :many}
   "exclude-system" {}
   "system-version" {:action :parse-canonical :cardinality :many}
   "check-system-version" {}
   "force-system-version" {}
   "tx-resource" {:action :copy-resource :cardinality :many}})

(def ^:private vs-validate-code-param-specs
  {"url" {:action :copy}
   "context" {}
   "valueSet" {:action :copy-resource}
   "valueSetVersion" {:action :copy}
   "code" {:action :copy}
   "system" {:action :copy}
   "systemVersion" {:action :copy}
   "display" {:action :copy}
   "coding" {:action :copy-complex-type}
   "codeableConcept" {:action :copy-complex-type}
   "date" {}
   "abstract" {}
   "displayLanguage" {:action :parse :cardinality :many :parse parse/parse-accept-charset}
   "useSupplement" {}
   "inferSystem" {:action :copy}
   "tx-resource" {:action :copy-resource :cardinality :many}
   "lenient-display-validation" {:action :copy}
   "activeOnly" {:action :copy}})

(defn- context-with-db [context db {:keys [tx-resources]}]
  (cond-> (assoc context :db db)
    tx-resources (assoc :tx-resources tx-resources)))

(defn- terminology-service [node context]
  (reify p/TerminologyService
    (p/-code-systems [_]
      (let [db (d/new-batch-db (d/db node))]
        (-> (c/code-systems db)
            (handle-close db))))
    (-code-system-validate-code [_ params]
      (if-ok [params (validate-params cs-validate-code-param-specs params)
              params (cs-validate-code-more params)]
        (let [db (d/new-batch-db (d/db node))]
          (-> (find-code-system (context-with-db context db params) params)
              (ac/then-apply #(cs/validate-code % params))
              (handle-close db)))
        ac/completed-future))
    (-expand-value-set [_ params]
      (if-ok [params (validate-params vs-expand-param-specs params)
              params (expand-vs-more params)]
        (let [db (d/new-batch-db (d/db node))
              context (context-with-db context db params)
              context (assoc context :params params)]
          (-> (find-value-set context params)
              (ac/then-compose
               (partial vs-expand/expand-value-set context))
              (handle-close db)))
        ac/completed-future))
    (-value-set-validate-code [_ params]
      (let [validate-params (partial validate-params vs-validate-code-param-specs)]
        (if-ok [params (validate-params params)
                params (vs-validate-code-more params)]
          (let [db (d/new-batch-db (d/db node))
                context (context-with-db context db params)]
            (-> (find-value-set context params)
                (ac/then-compose
                 (fn [value-set]
                   (if-ok [extension-params (validate-params (vs/extension-params value-set))]
                     (vs-validate-code/validate-code
                      context
                      value-set
                      (merge
                       (vs/display-language-param value-set)
                       extension-params
                       params))
                     ac/completed-future)))
                (handle-close db)))
          ac/completed-future)))))

(defmethod m/pre-init-spec ::ts/local [_]
  (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn]
          :opt-un [::enable-bcp-13 ::enable-ucum ::loinc ::sct]))

(defmethod ig/init-key ::ts/local
  [_ {:keys [node] :as config}]
  (log/info "Init local terminology server")
  (let [context (context config)]
    (ensure-code-systems config context)
    (terminology-service node context)))

(derive ::ts/local :blaze/terminology-service)
