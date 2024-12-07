(ns blaze.terminology-service.local
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.module :as m]
   [blaze.spec]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.local.concept :as concept]
   [blaze.terminology-service.local.filter :as filter]
   [blaze.terminology-service.protocols :as p]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log])
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn- expand-complete-code-system [{:keys [url] concepts :concept}]
  (mapv
   (fn [{:keys [code display]}]
     (cond-> {:system url :code code}
       display (assoc :display display)))
   concepts))

(defn- code-system-not-found-msg [{:keys [system version]}]
  (if version
    (format "The code system with URL `%s` and version `%s` was not found."
            (type/value system) (type/value version))
    (format "The code system with URL `%s` was not found." (type/value system))))

(defn- code-system-not-found-anom [include]
  (ba/not-found (code-system-not-found-msg include)))

(defn- all-version-expansion-msg [url]
  (format "Expanding the code system with URL `%s` in all versions is unsupported." url))

(defn- all-version-expansion-anom [url]
  (ba/unsupported (all-version-expansion-msg url)))

(defn- include-clauses [{:keys [system version]}]
  (if (= "*" (type/value version))
    (all-version-expansion-anom (type/value system))
    (cond-> [["url" (type/value system)]]
      version (conj ["version" (type/value version)]))))

(defn- code-system-query [db include]
  (when-ok [clauses (include-clauses include)]
    (d/type-query db "CodeSystem" clauses)))

(defn- code-system-not-complete-msg [{:keys [url content]}]
  (format "Can't expand the code system with URL `%s` because it is not complete. It's content is `%s`."
          url (type/value content)))

(defn- code-system-not-complete-anom [code-system]
  (ba/unsupported (code-system-not-complete-msg code-system) :http/status 409))

(defn- check-code-system-content [{:keys [content] :as code-system}]
  (if (= "complete" (type/value content))
    code-system
    (code-system-not-complete-anom code-system)))

(defn- find-code-system [db include]
  (if-ok [resource-handles (code-system-query db include)]
    (if-let [code-system (coll/first resource-handles)]
      (-> (d/pull db code-system)
          (ac/then-apply check-code-system-content))
      (ac/completed-future (code-system-not-found-anom include)))
    ac/completed-future))

(defn- version-parameter [url version]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"version"
   :value (type/uri (str url "|" version))})

(defn- append-version [concepts {:keys [url version]}]
  (cond-> {:contains concepts}
    version (assoc :parameter #{(version-parameter url version)})))

(defn- expand-code-system [code-system concepts filters]
  (cond
    (seq concepts) (concept/expand-code-system code-system concepts)
    (seq filters) (filter/expand-code-system code-system filters)
    :else (expand-complete-code-system code-system)))

(defn- include-system
  [{:keys [db]} {concepts :concept filters :filter :as include}]
  (if (and (seq concepts) (seq filters))
    (ac/completed-future (ba/incorrect "Incorrect combination of concept and filter."))
    (do-sync [code-system (find-code-system db include)]
      (when-ok [concepts (expand-code-system code-system concepts filters)]
        (append-version concepts code-system)))))

(declare find-value-set-by-url)
(declare expand-value-set)

(defn- expand-value-set-by-canonical [{:keys [db] :as context} canonical]
  (-> (find-value-set-by-url db canonical)
      (ac/then-compose (partial expand-value-set context))))

(defn- include-value-sets [context value-sets]
  (let [futures (mapv #(expand-value-set-by-canonical context (type/value %)) value-sets)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map (comp #(select-keys % [:parameter :contains]) :expansion ac/join)) (partial merge-with into) futures))))

(defn- include [context {:keys [system] value-sets :valueSet :as include}]
  (cond
    (and system value-sets)
    (ac/completed-future (ba/incorrect "Incorrect combination of system and valueSet."))

    system (include-system context include)
    value-sets (include-value-sets context value-sets)

    :else (ac/completed-future (ba/incorrect "Missing system or valueSet."))))

(defn- expand-includes [context includes]
  (let [futures (mapv (partial include context) includes)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) (partial merge-with into) futures))))

(defn remove-excludes-duplicates [includes excludes]
  (into [] (comp (remove #(some (partial = %) excludes)) (distinct)) includes))

(defn- count-parameter [count]
  {:fhir/type :fhir.ValueSet.expansion/parameter
   :name #fhir/string"count"
   :value (type/integer count)})

(defn- append-params [parameters {:keys [count]}]
  (cond-> parameters
    count (conj (count-parameter count))))

(defn- expansion [{:keys [clock] {:keys [count] :as request} :request} parameters concepts]
  (cond->
   {:fhir/type :fhir.ValueSet/expansion
    :identifier (type/uri (str "urn:uuid:" (random-uuid)))
    :timestamp (time/offset-date-time clock)
    :total (clojure.core/count concepts)
    :parameter (append-params parameters request)}
    (nil? count) (assoc :contains concepts)
    (pos-int? count) (assoc :contains (into [] (take count) concepts))))

(defn- expand-value-set**
  [{{:keys [include-definition] :or {include-definition false}} :request
    :as context}
   {{includes :include excludes :exclude} :compose :as value-set}]
  (let [includes (expand-includes context includes)
        excludes (expand-includes context excludes)]
    (do-sync [_ (ac/all-of [includes excludes])]
      (let [includes (ac/join includes)
            excludes (ac/join excludes)
            concepts (remove-excludes-duplicates (:contains includes) (:contains excludes))]
        (cond->
         (assoc
          value-set
          :expansion
          (expansion context (vec (:parameter includes)) concepts))
          (not include-definition) (dissoc :compose))))))

(defn- expand-value-set-msg [{:keys [url]}]
  (format "Error while expanding the value set with URL `%s`. " url))

(defn- expand-value-set* [context {:keys [expansion] :as value-set}]
  (if expansion
    (ac/completed-future value-set)
    (-> (expand-value-set** context value-set)
        (ac/exceptionally
         #(update % ::anom/message (partial str (expand-value-set-msg value-set)))))))

(defn- expand-value-set [context value-set]
  (expand-value-set* context (dissoc value-set :id :meta)))

(defn- value-set-not-found-by-id-anom [id]
  (ba/not-found (format "The value set with id `%s` was not found." id)))

(defn- non-deleted-resource-handle [db type id]
  (when-let [handle (d/resource-handle db type id)]
    (when-not (d/deleted? handle)
      handle)))

(defn- find-value-set-by-id [db id]
  (if-let [value-set (non-deleted-resource-handle db "ValueSet" id)]
    (d/pull db value-set)
    (ac/completed-future (value-set-not-found-by-id-anom id))))

(defn- value-set-not-found-by-url-anom
  ([url]
   (ba/not-found (format "The value set with URL `%s` was not found." url)))
  ([url version]
   (ba/not-found (format "The value set with URL `%s` and version `%s` was not found." url version))))

(defn- find-value-set-by-url
  ([db url]
   (if-let [value-set (coll/first (d/type-query db "ValueSet" [["url" url]]))]
     (d/pull db value-set)
     (ac/completed-future (value-set-not-found-by-url-anom url))))
  ([db url version]
   (if-let [value-set (coll/first (d/type-query db "ValueSet" [["url" url] ["version" version]]))]
     (d/pull db value-set)
     (ac/completed-future (value-set-not-found-by-url-anom url version)))))

(defn- find-value-set [db {:keys [id url] version :value-set-version}]
  (cond
    (and url version) (find-value-set-by-url db url version)
    url (find-value-set-by-url db url)
    id (find-value-set-by-id db id)
    :else (ac/completed-future (ba/incorrect "Missing ID or URL."))))

(defn- handle-close [stage db]
  (ac/handle
   stage
   (fn [value-set e]
     (let [res (if e (assoc e :t (d/t db)) value-set)]
       (.close ^AutoCloseable db)
       res))))

(defmethod m/pre-init-spec ::ts/local [_]
  (s/keys :req-un [:blaze.db/node :blaze/clock]))

(defmethod ig/init-key ::ts/local
  [_ {:keys [node clock]}]
  (log/info "Init local terminology server")
  (reify p/TerminologyService
    (-expand-value-set [_ request]
      (let [db (d/new-batch-db (d/db node))]
        (-> (find-value-set db request)
            (ac/then-compose (partial expand-value-set {:db db :clock clock :request request}))
            (handle-close db))))))

(derive ::ts/local :blaze/terminology-service)
