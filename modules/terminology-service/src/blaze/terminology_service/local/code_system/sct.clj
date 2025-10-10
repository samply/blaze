(ns blaze.terminology-service.local.code-system.sct
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.module :as m]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.sct.context :as context :refer [core-version-prefix url]]
   [blaze.terminology-service.local.code-system.sct.filter.core :as filter]
   [blaze.terminology-service.local.code-system.sct.filter.descendent-of]
   [blaze.terminology-service.local.code-system.sct.filter.equals]
   [blaze.terminology-service.local.code-system.sct.filter.is-a]
   [blaze.terminology-service.local.code-system.sct.spec]
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]]
   [blaze.terminology-service.local.code-system.sct.util :as sct-u]
   [blaze.terminology-service.local.code-system.util :as cs-u]
   [blaze.util :as u]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn- handles [db version]
  (cond
    (re-matches sct-u/module-only-version-pattern version)
    (do-sync [code-systems (cs-u/code-systems db url)]
      (filterv
       #(str/starts-with? (type/value (:version %)) version)
       code-systems))

    :else
    (d/pull-many db (d/type-query db "CodeSystem" [["url" url] ["version" version]]))))

(defn- assoc-context [{:keys [version] :as code-system} context]
  (when-ok [[module-id version] (sct-u/module-version (type/value version))]
    (assoc code-system :sct/context context :sct/module-id module-id
           :sct/version version)))

(defn- code-system-not-found-msg [version]
  (format "The code system `%s|%s` was not found." url version))

(defmethod c/find :sct
  [{:keys [db] :sct/keys [context]} _ & [version]]
  (if (or (nil? version) (= version core-version-prefix))
    (ac/completed-future (assoc-context (:current-int-system context) context))
    (do-sync [code-systems (handles db version)]
      (or (some-> (first (fu/sort-by-priority code-systems))
                  (assoc-context context))
          (ba/not-found (code-system-not-found-msg version))))))

(defmethod c/enhance :sct
  [{:sct/keys [context]} code-system]
  (assoc-context code-system context))

(defmethod c/expand-complete :sct
  [_ _]
  (ba/conflict
   "Expanding all SNOMED CT concepts is too costly."
   :fhir/issue "too-costly"))

(defn- find-fully-specified-name
  [{{:keys [module-dependency-index fully-specified-name-index]} :sct/context
    :sct/keys [module-id version]} code]
  (context/find-fully-specified-name module-dependency-index
                                     fully-specified-name-index
                                     module-id version code))

(defn- filter-preferred
  [{{:keys [acceptability-index]} :sct/context
    :sct/keys [version]} synonyms]
  (into
   []
   (keep
    (fn [[id synonym]]
      (when (= :preferred (context/find-acceptability acceptability-index version id))
        synonym)))
   synonyms))

(defn- display
  [code-system find-synonyms code
   {:keys [display-language] :or {display-language "en"}}]
  (or (second
       (first (sort-by (fn [[language-code]] (not= display-language language-code))
                       (filter-preferred code-system (find-synonyms code)))))
      (find-fully-specified-name code-system code)))

(defn- fully-specified-name-designation [term]
  {:language #fhir/code "en"
   :use #fhir/Coding{:system #fhir/uri "http://snomed.info/sct"
                     :code #fhir/code "900000000000003001"
                     :display #fhir/string "Fully specified name"}
   :value (type/string term)})

(defn- synonym-designation [[_id [language-code term]]]
  {:language (type/code language-code)
   :use #fhir/Coding{:system #fhir/uri "http://snomed.info/sct"
                     :code #fhir/code "900000000000013009"
                     :display #fhir/string "Synonym"}
   :value (type/string term)})

(defn- assoc-designations [concept code-system find-synonyms code]
  (let [fsn (find-fully-specified-name code-system code)
        synonyms (find-synonyms code)]
    (cond-> concept
      (seq synonyms) (assoc :designation (into (or (some-> fsn fully-specified-name-designation vector) []) (map synonym-designation) synonyms)))))

(defn- build-concept
  [code-system find-synonyms code
   {:keys [include-version include-designations] :as params}]
  (cond->
   {:system #fhir/uri "http://snomed.info/sct"
    :code (type/code (str code))
    :display (type/string (display code-system find-synonyms code params))}
    include-version (assoc :version (:version code-system))
    include-designations (assoc-designations code-system find-synonyms code)))

(defn- concept-xf
  [{{:keys [module-dependency-index concept-index synonym-index]} :sct/context
    :sct/keys [module-id version] :as code-system} params]
  (let [find-concept (partial context/find-concept module-dependency-index
                              concept-index module-id version)
        find-synonyms (partial context/find-synonyms module-dependency-index
                               synonym-index module-id version)]
    (keep
     (fn [code]
       (when-some [active (find-concept code)]
         (cond-> (build-concept code-system find-synonyms code params)
           (false? active)
           (assoc :inactive #fhir/boolean true)))))))

(defn- active-concept-xf
  [{{:keys [module-dependency-index concept-index synonym-index]} :sct/context
    :sct/keys [module-id version] :as code-system} params]
  (let [find-concept (partial context/find-concept module-dependency-index
                              concept-index module-id version)
        find-synonyms (partial context/find-synonyms module-dependency-index
                               synonym-index module-id version)]
    (keep
     (fn [code]
       (when (find-concept code)
         (build-concept code-system find-synonyms code params))))))

(defmethod c/expand-concept :sct
  [code-system concepts {:keys [active-only] :as params}]
  (into
   []
   (comp
    (keep (comp parse-sctid type/value :code))
    ((if active-only active-concept-xf concept-xf) code-system params))
   concepts))

(defmethod c/expand-filter :sct
  [code-system filter {:keys [active-only] :as params}]
  (when-ok [codes (filter/expand-filter code-system filter)]
    (into
     #{}
     ((if active-only active-concept-xf concept-xf) code-system params)
     codes)))

(defmethod c/find-complete :sct
  [{{:keys [module-dependency-index concept-index synonym-index]} :sct/context
    :sct/keys [module-id version] :as code-system} {{:keys [code]} :clause :as params}]
  (when-let [code (parse-sctid code)]
    (when-some [active (context/find-concept module-dependency-index concept-index
                                             module-id version code)]
      (let [find-synonyms (partial context/find-synonyms module-dependency-index
                                   synonym-index module-id version)]
        (cond-> (build-concept code-system find-synonyms code
                               (assoc params :include-version true
                                      :include-designations true))
          (not active) (assoc :inactive #fhir/boolean true))))))

(defmethod c/find-filter :sct
  [code-system filter {{:keys [code]} :clause}]
  (when-let [code (parse-sctid code)]
    (when-ok [found (filter/satisfies-filter code-system filter code)]
      (when found
        (let [{{:keys [module-dependency-index synonym-index]}
               :sct/context :sct/keys [module-id version]} code-system
              find-synonyms (partial context/find-synonyms module-dependency-index
                                     synonym-index module-id version)]
          (build-concept code-system find-synonyms code
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
  (-> (cs-u/code-system-versions (d/db node) url)
      (ac/then-compose
       (fn [existing-versions]
         (let [tx-ops (cs-u/tx-ops context existing-versions code-systems)]
           (if (seq tx-ops)
             (do (log/debug "Create" (count tx-ops) "new SNOMED CT CodeSystem resources...")
                 (d/transact node tx-ops))
             (ac/completed-future nil)))))))
