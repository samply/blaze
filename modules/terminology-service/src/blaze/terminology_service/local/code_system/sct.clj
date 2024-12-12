(ns blaze.terminology-service.local.code-system.sct
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.luid :as luid]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.sct.context :as context :refer [url core-version-prefix]]
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]]
   [blaze.terminology-service.local.code-system.sct.util :as sct-u]
   [blaze.terminology-service.local.code-system.util :as u]
   [blaze.terminology-service.local.priority :as priority]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [taoensso.timbre :as log]))

(defn- code-systems [db]
  (d/pull-many db (d/type-query db "CodeSystem" [["url" url]])))

(defn- handles [db version]
  (cond
    (re-matches sct-u/module-only-version-pattern version)
    (do-sync [code-systems (code-systems db)]
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
  (format "The code system with URL `http://snomed.info/sct` and version `%s` was not found." version))

(defmethod c/find :sct
  [{:keys [db] :sct/keys [context]} _ & [version]]
  (if (or (nil? version) (= version core-version-prefix))
    (ac/completed-future (assoc-context (:current-int-system context) context))
    (do-sync [code-systems (handles db version)]
      (or (some-> (first (priority/sort-by-priority code-systems))
                  (assoc-context context))
          (ba/not-found (code-system-not-found-msg version))))))

(defmethod c/enhance :sct
  [{:sct/keys [context]} code-system]
  (assoc-context code-system context))

(defn- not-found-msg [code]
  (format "The provided code `%s` was not found in the code system with URL `http://snomed.info/sct`." code))

(defn- find-concept
  [{{:keys [concept-index]} :sct/context :sct/keys [module-id version]} code]
  (context/find-concept concept-index module-id version code))

(defn- find-description
  [{{:keys [description-index]} :sct/context :sct/keys [version]} code]
  (context/find-description description-index code version))

(defmethod c/validate-code :sct
  [{:keys [url version] :as code-system} request]
  (if-ok [code (u/extract-code request (type/value url))
          code (or (parse-sctid code) (ba/not-found (not-found-msg code)))
          _ (or (find-concept code-system code)
                (ba/not-found (not-found-msg code)))]
    {:fhir/type :fhir/Parameters
     :parameter
     [(u/parameter "result" #fhir/boolean true)
      (u/parameter "code" (type/code (str code)))
      (u/parameter "system" url)
      (u/parameter "version" version)
      (u/parameter "display" (find-description code-system code))]}
    (fn [{::anom/keys [message]}]
      {:fhir/type :fhir/Parameters
       :parameter
       [(u/parameter "result" #fhir/boolean false)
        (u/parameter "message" (type/string message))]})))

(defmethod c/expand-complete :sct
  [_ _]
  (ba/conflict
   "Expanding all Snomed CT concepts is too costly."
   :fhir/issue "too-costly"))

(defn- concept-xf
  [{{:keys [concept-index description-index]} :sct/context
    :sct/keys [module-id version]}]
  (keep
   (fn [code]
     (when-some [active (context/find-concept concept-index module-id version code)]
       (cond->
        {:system #fhir/uri"http://snomed.info/sct"
         :code (type/code (str code))
         :display (type/string (context/find-description description-index code version))}
         (false? active)
         (assoc :inactive #fhir/boolean true))))))

(defn- active-concept-xf
  [{{:keys [concept-index description-index]} :sct/context
    :sct/keys [module-id version]}]
  (keep
   (fn [code]
     (when (context/find-concept concept-index module-id version code)
       {:system #fhir/uri"http://snomed.info/sct"
        :code (type/code (str code))
        :display (type/string (context/find-description description-index code version))}))))

(defmethod c/expand-concept :sct
  [{:keys [active-only]} code-system concepts]
  (into
   []
   (comp
    (keep (comp parse-sctid type/value :code))
    ((if active-only active-concept-xf concept-xf) code-system))
   concepts))

(defmulti filter-one
  {:arglists '([code-system filter])}
  (fn [_ {:keys [op]}] (-> op type/value keyword)))

(defmethod filter-one :is-a
  [{{:keys [child-index]} :sct/context :sct/keys [module-id version]}
   {:keys [value]}]
  (context/transitive-neighbors-including child-index module-id version
                                          (parse-sctid (type/value value))))

(defmethod filter-one :descendent-of
  [{{:keys [child-index]} :sct/context :sct/keys [module-id version]}
   {:keys [value]}]
  (context/transitive-neighbors child-index module-id version
                                (parse-sctid (type/value value))))

(defmethod filter-one :default
  [_ {:keys [op]}]
  (ba/unsupported (format "Unsupported filter operator `%s` in code system `http://snomed.info/sct`." (type/value op))))

(defmethod c/expand-filter :sct
  [{:keys [active-only]} code-system filter]
  (when-ok [codes (filter-one code-system filter)]
    (into
     #{}
     ((if active-only active-concept-xf concept-xf) code-system)
     codes)))

(defn build-context [release-path]
  (context/build release-path))

(defn- code-system-versions [db]
  (do-sync [code-systems (code-systems db)]
    (into #{} (map (comp type/value :version)) code-systems)))

(defn- luid-generator [{:keys [clock rng-fn]}]
  (luid/generator clock (rng-fn)))

(defn- tx-ops [context existing-versions code-systems]
  (transduce
   (remove (comp existing-versions type/value :version))
   (fn
     ([{:keys [tx-ops]}] tx-ops)
     ([{:keys [luid-generator] :as ret} code-system]
      (-> (update ret :tx-ops conj [:create (assoc code-system :id (luid/head luid-generator))])
          (update :luid-generator luid/next))))
   {:tx-ops []
    :luid-generator (luid-generator context)}
   code-systems))

(defn ensure-code-systems
  "Ensures that all Snomed CT code systems are present in the database node."
  [{:keys [node] :as context} {:keys [code-systems]}]
  (-> (code-system-versions (d/db node))
      (ac/then-compose
       (fn [existing-versions]
         (let [tx-ops (tx-ops context existing-versions code-systems)]
           (if (seq tx-ops)
             (do (log/debug "Create" (count tx-ops) "new Snomed CT CodeSystem resources...")
                 (d/transact node tx-ops))
             (ac/completed-future nil)))))))
