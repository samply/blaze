(ns blaze.terminology-service.local
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.util :as fu]
   [blaze.module :as m]
   [blaze.spec]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.local.capabilities :as c]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.code-system.bcp-13 :as bcp-13]
   [blaze.terminology-service.local.code-system.bcp-47 :as bcp-47]
   [blaze.terminology-service.local.code-system.loinc :as loinc]
   [blaze.terminology-service.local.code-system.sct :as sct]
   [blaze.terminology-service.local.code-system.ucum :as ucum]
   [blaze.terminology-service.local.spec]
   [blaze.terminology-service.local.value-set :as vs]
   [blaze.terminology-service.local.value-set.expand :as vs-expand]
   [blaze.terminology-service.local.value-set.validate-code :as vs-validate-code]
   [blaze.terminology-service.protocols :as p]
   [blaze.util :as u]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [muuntaja.parse :as parse]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache Caffeine]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

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

(defn- cs-coding-clause [{{code :value} :code {display :value} :display} origin]
  (if code
    (cond-> {:code code :origin origin} display (assoc :display display))
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
      (let [{{system :value} :system {version :value} :version} coding]
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
          1 (let [{{system :value} :system {version :value} :version} fist-coding]
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
    (and (some? offset) (not= 0 offset))
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

(defn- context [{:keys [clock graph-cache loinc sct]}]
  (cond-> {:clock clock ::cs/graph-cache graph-cache}
    loinc (assoc :loinc/context loinc)
    sct (assoc :sct/context sct)))

(defn- ensure-code-systems
  "Ensures that all code systems of internal terminologies like SNOMED CT are
  present in the database node."
  [{:keys [enable-bcp-13 enable-bcp-47 enable-ucum] :as config}
   {loinc-context :loinc/context
    sct-context :sct/context}]
  (when enable-bcp-13
    @(bcp-13/ensure-code-system config))
  (when enable-bcp-47
    @(bcp-47/ensure-code-system config))
  (when enable-ucum
    @(ucum/ensure-code-system config))
  (when loinc-context
    @(loinc/ensure-code-systems config loinc-context))
  (when sct-context
    @(sct/ensure-code-systems config sct-context)))

(defn- load-all-code-systems
  "Loads all code systems into the resource cache because we needs them to be
  able to generate the TerminologyCapabilities as fast as possible."
  [node]
  (log/info "Load all code systems into the resource cache...")
  (let [start (System/nanoTime)]
    @(cs/list (d/db node))
    (log/info "Successfully loaded all code systems in"
              (format "%.1f" (u/duration-s start)) "seconds")))

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

(defn- coerce-nat-long [value]
  (if-some [value (:value value)]
    (if-not (neg? value)
      value
      (ba/incorrect "Has to be a non-negative integer."))
    (ba/incorrect "Missing value.")))

(def ^:private vs-expand-param-specs
  {"url" {:action :copy}
   "valueSet" {:action :copy-resource}
   "valueSetVersion" {:action :copy}
   "context" {}
   "contextDirection" {}
   "filter" {}
   "date" {}
   "offset" {:action :copy :coerce coerce-nat-long}
   "count" {:action :copy :coerce coerce-nat-long}
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
   "system-version" {:action :copy :coerce identity :cardinality :many}
   "check-system-version" {}
   "force-system-version" {}
   "tx-resource" {:action :copy-resource :cardinality :many}})

(defn- coerce-display-language [value]
  (if (#{:fhir/code :fhir/string} (:fhir/type value))
    (if-some [value (:value value)]
      (parse/parse-accept-charset value)
      (ba/incorrect "Missing value."))
    (ba/incorrect "Expect FHIR code or string.")))

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
   "displayLanguage" {:action :copy :coerce coerce-display-language :cardinality :many}
   "useSupplement" {}
   "inferSystem" {:action :copy}
   "system-version" {:action :copy :coerce identity :cardinality :many}
   "tx-resource" {:action :copy-resource :cardinality :many}
   "lenient-display-validation" {:action :copy}
   "activeOnly" {:action :copy}})

(defn- db [vnode]
  (if-some [node @vnode]
    (d/db node)
    (ba/unavailable "Terminology service is unavailable.")))

(defn- context-with-db [context db {:keys [tx-resources]}]
  (cond-> (assoc context :db db)
    tx-resources (assoc :tx-resources tx-resources)))

(defn- terminology-service [config context vnode]
  (reify p/TerminologyService
    (-post-init [_ node]
      (ensure-code-systems (assoc config :node node) context)
      (load-all-code-systems node)
      (vreset! vnode node))

    (-code-systems [_]
      (when-ok [db (db vnode)]
        (c/code-systems db)))

    (-code-system-validate-code [_ params]
      (if-ok [params (fu/coerce-params cs-validate-code-param-specs params)
              params (cs-validate-code-more params)
              db (db vnode)]
        (let [db (d/new-batch-db db)]
          (-> (find-code-system (context-with-db context db params) params)
              (ac/then-apply #(cs/validate-code % params))
              (handle-close db)))
        ac/completed-future))

    (-expand-value-set [_ params]
      (if-ok [params (fu/coerce-params vs-expand-param-specs params)
              params (expand-vs-more params)
              db (db vnode)]
        (let [db (d/new-batch-db db)
              context (context-with-db context db params)
              context (assoc context :params params)]
          (-> (find-value-set context params)
              (ac/then-compose
               (partial vs-expand/expand-value-set context))
              (handle-close db)))
        ac/completed-future))

    (-value-set-validate-code [_ params]
      (let [validate-params (partial fu/coerce-params vs-validate-code-param-specs)]
        (if-ok [params (validate-params params)
                params (vs-validate-code-more params)
                db (db vnode)]
          (let [db (d/new-batch-db db)
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
  (s/keys :req-un [:blaze/clock :blaze/rng-fn ::graph-cache]
          :opt-un [::enable-bcp-13 ::enable-bcp-47 ::enable-ucum ::loinc ::sct]))

(defmethod ig/init-key ::ts/local
  [_ config]
  (log/info "Init local terminology service")
  (terminology-service config (context config) (volatile! nil)))

(defmethod m/pre-init-spec ::graph-cache [_]
  (s/keys :opt-un [::num-concepts]))

(defmethod ig/init-key ::graph-cache
  [_ {:keys [num-concepts] :or {num-concepts 100000}}]
  (log/info "Init local terminology service graph cache with a size of" num-concepts "concepts")
  (-> (Caffeine/newBuilder)
      (.maximumWeight num-concepts)
      (.weigher (fn [_ {:keys [concepts]}] (count concepts)))
      (.recordStats)
      (.build)))
