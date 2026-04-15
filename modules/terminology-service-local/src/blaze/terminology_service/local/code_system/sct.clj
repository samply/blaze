(ns blaze.terminology-service.local.code-system.sct
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.module :as m]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.sct.context :as context :refer [core-version-prefix]]
   [blaze.terminology-service.local.code-system.sct.filter.core :as filter]
   [blaze.terminology-service.local.code-system.sct.filter.descendent-of]
   [blaze.terminology-service.local.code-system.sct.filter.equals]
   [blaze.terminology-service.local.code-system.sct.filter.is-a]
   [blaze.terminology-service.local.code-system.sct.spec]
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]]
   [blaze.terminology-service.local.code-system.sct.util :as sct-u]
   [blaze.terminology-service.local.code-system.util :as cs-u]
   [blaze.terminology-service.local.search-index :as search-index]
   [blaze.util :as u]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn- find-code-system [{:keys [code-systems]} version]
  (->> (filter #(str/starts-with? (:value (:version %)) version) code-systems)
       (fu/sort-by-priority)
       (first)))

(defn- find-fully-specified-name
  [{{:keys [module-dependency-index fully-specified-name-index]} :sct/context
    :sct/keys [module-id version]}]
  (partial context/find-fully-specified-name module-dependency-index
           fully-specified-name-index module-id version))

(defn- find-synonyms
  [{{:keys [module-dependency-index synonym-index]} :sct/context
    :sct/keys [module-id version]}]
  (partial context/find-synonyms module-dependency-index synonym-index module-id
           version))

(defn- find-concept
  [{{:keys [module-dependency-index concept-index]} :sct/context
    :sct/keys [module-id version]}]
  (partial context/find-concept module-dependency-index concept-index module-id
           version))

(defn- find-acceptability
  [{{:keys [acceptability-index]} :sct/context :sct/keys [version]}]
  (partial context/find-acceptability acceptability-index version))

(defn- filter-preferred-xf [find-acceptability]
  (keep
   (fn [[id synonym]]
     (when (identical? :preferred (find-acceptability id))
       synonym))))

(defn- filter-preferred [find-acceptability synonyms]
  (into [] (filter-preferred-xf find-acceptability) synonyms))

(defn- display
  [find-synonyms find-acceptability find-fully-specified-name code
   {:keys [display-language] :or {display-language "en"}}]
  (or (second
       (first (sort-by (fn [[language-code]] (not= display-language language-code))
                       (filter-preferred find-acceptability (find-synonyms code)))))
      (find-fully-specified-name code)))

(defn- fully-specified-name-designation [term]
  {:language #fhir/code "en"
   :use #fhir/Coding{:system #fhir/uri-interned "http://snomed.info/sct"
                     :code #fhir/code "900000000000003001"
                     :display #fhir/string-interned "Fully specified name"}
   :value (type/string term)})

(defn- synonym-designation [[_id [language-code term]]]
  {:language (type/code language-code)
   :use #fhir/Coding{:system #fhir/uri-interned "http://snomed.info/sct"
                     :code #fhir/code "900000000000013009"
                     :display #fhir/string-interned "Synonym"}
   :value (type/string term)})

(defn- assoc-designations [concept find-fully-specified-name find-synonyms code]
  (let [fsn (find-fully-specified-name code)
        synonyms (find-synonyms code)]
    (cond-> concept
      (seq synonyms)
      (assoc
       :designation
       (into (or (some-> fsn fully-specified-name-designation vector) [])
             (map synonym-designation)
             synonyms)))))

(defn- build-concept
  [code-system find-synonyms find-acceptability find-fully-specified-name code
   {:keys [include-version include-designations] :as params}]
  (let [display (display find-synonyms find-acceptability find-fully-specified-name code params)]
    (cond->
     {:system #fhir/uri-interned "http://snomed.info/sct"
      :code (type/code (str code))}
      display (assoc :display (type/string display))
      include-version (assoc :version (:version code-system))
      include-designations (assoc-designations find-fully-specified-name find-synonyms code))))

(defn- assoc-context
  [{:keys [version] :as code-system}
   {:keys [module-dependency-index search-index] :as context}]
  (when-ok [[module-id version] (sct-u/module-version (:value version))]
    (let [module-ids (context/find-all-module-ids module-dependency-index
                                                  module-id version)]
      (assoc code-system :sct/context context
             :sct/module-id module-id :sct/version version
             :sct/search-index search-index
             :sct/module-ids module-ids))))

(defn- code-system-not-found-msg [version]
  (format "The code system `%s|%s` was not found." sct-u/url version))

(defmethod c/resolve-version :sct
  [{:sct/keys [context]} _ version]
  (if (or (nil? version) (= version core-version-prefix))
    (:value (:version (:current-int-system context)))
    (or (:value (:version (find-code-system context version)))
        version)))

(defmethod c/find :sct
  [{:sct/keys [context]} _ & [version]]
  (ac/completed-future
   (or (and (nil? version)
            (assoc-context (:current-int-system context) context))
       (some-> (find-code-system context version)
               (assoc-context context))
       (ba/not-found (code-system-not-found-msg version)))))

(defmethod c/enhance :sct
  [{:sct/keys [context]} code-system]
  (assoc-context code-system context))

(defn- concept-xf [code-system params]
  (let [find-concept (find-concept code-system)
        find-synonyms (find-synonyms code-system)
        find-acceptability (find-acceptability code-system)
        find-fully-specified-name (find-fully-specified-name code-system)]
    (keep
     (fn [code]
       (when-some [active (find-concept code)]
         (cond-> (build-concept code-system find-synonyms find-acceptability
                                find-fully-specified-name code params)
           (false? active)
           (assoc :inactive #fhir/boolean true)))))))

(defn- active-concept-xf [code-system params]
  (let [find-concept (find-concept code-system)
        find-synonyms (find-synonyms code-system)
        find-acceptability (find-acceptability code-system)
        find-fully-specified-name (find-fully-specified-name code-system)]
    (keep
     (fn [code]
       (when (find-concept code)
         (build-concept code-system find-synonyms find-acceptability
                        find-fully-specified-name code params))))))

(defn- complete-text-filter
  [{:sct/keys [search-index module-ids]} filter]
  (search-index/search search-index filter 10000 nil module-ids))

(defmethod c/expand-complete :sct
  [code-system {:keys [active-only filter] :as params}]
  (if filter
    (into
     []
     (comp (map parse-sctid)
           ((if active-only active-concept-xf concept-xf) code-system params))
     (complete-text-filter code-system filter))
    (ba/conflict
     "Expanding all SNOMED CT concepts is too costly."
     :fhir/issue "too-costly")))

(defn- filter-text-filter
  [{:sct/keys [search-index module-ids]} filter found-codes]
  (mapv parse-sctid (search-index/search search-index filter 10000 (mapv str found-codes) module-ids)))

(defmethod c/expand-concept :sct
  [code-system value-set-concepts {:keys [active-only] text-filter :filter :as params}]
  (let [codes (into [] (keep (comp parse-sctid :value :code)) value-set-concepts)]
    (into
     []
     ((if active-only active-concept-xf concept-xf) code-system params)
     (cond->> codes
       text-filter (filter-text-filter code-system text-filter)))))

(defmethod c/expand-filter :sct
  [code-system filter {:keys [active-only] text-filter :filter :as params}]
  (when-ok [found-codes (filter/expand-filter code-system filter)]
    (into
     #{}
     ((if active-only active-concept-xf concept-xf) code-system params)
     (cond->> found-codes
       text-filter (filter-text-filter code-system text-filter)))))

(defmethod c/find-complete :sct
  [{{:keys [module-dependency-index concept-index]} :sct/context
    :sct/keys [module-id version] :as code-system} {{:keys [code]} :clause :as params}]
  (when-let [code (parse-sctid code)]
    (when-some [active (context/find-concept module-dependency-index concept-index
                                             module-id version code)]
      (let [find-synonyms (find-synonyms code-system)
            find-acceptability (find-acceptability code-system)
            find-fully-specified-name (find-fully-specified-name code-system)]
        (cond-> (build-concept code-system find-synonyms find-acceptability
                               find-fully-specified-name code
                               (assoc params :include-version true
                                      :include-designations true))
          (not active) (assoc :inactive #fhir/boolean true))))))

(defmethod c/find-filter :sct
  [code-system filter {{:keys [code]} :clause}]
  (when-let [code (parse-sctid code)]
    (when-ok [found (filter/satisfies-filter code-system filter code)]
      (when found
        (let [find-synonyms (find-synonyms code-system)
              find-acceptability (find-acceptability code-system)
              find-fully-specified-name (find-fully-specified-name code-system)]
          (build-concept code-system find-synonyms find-acceptability
                         find-fully-specified-name code
                         {:include-version true :include-designations true}))))))

(defmethod m/pre-init-spec ::cs/sct [_]
  (s/keys :req-un [::release-path]))

(defmethod ig/init-key ::cs/sct
  [_ {:keys [release-path]}]
  (log/info "Start reading SNOMED CT release files...")
  (let [start (System/nanoTime)
        context (ba/throw-when (context/build release-path))]
    (log/info "Successfully read SNOMED CT release files in"
              (format "%.1f" (u/duration-s start)) "seconds")
    context))

(defn ensure-code-systems
  "Ensures that all SNOMED CT code systems are present in the database node."
  {:arglists '([context sct-context])}
  [{:keys [node] :as context} {:keys [code-systems]}]
  (-> (cs-u/code-system-versions (d/db node) sct-u/url)
      (ac/then-compose
       (fn [existing-versions]
         (let [tx-ops (cs-u/tx-ops context existing-versions code-systems)]
           (if (seq tx-ops)
             (do (log/debug "Create" (count tx-ops) "new SNOMED CT CodeSystem resources...")
                 (d/transact node tx-ops))
             (ac/completed-future nil)))))))
