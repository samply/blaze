(ns blaze.datomic.cql
  (:require
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [prometheus.alpha :as prom :refer [defhistogram]]))


(defhistogram call-seconds
  "Call times in seconds."
  {:namespace "datomic_cql"}
  [0.0000001 0.0000002 0.0000005
   0.000001 0.000002 0.000005
   0.00001 0.00002 0.00005
   0.0001 0.0002 0.0005
   0.001 0.002 0.005
   0.01 0.02 0.05
   0.1 0.2 0.5]
  "function")


(s/fdef find-patient
  :args (s/cat :db ::ds/db :id string?)
  :ret ::ds/entity)

(defn find-patient
  "Returns the patient with `id` in `db` as Datomic entity or nil if not found."
  [db id]
  (d/entity db [:Patient/id id]))


(s/fdef list-patient-ids
  :args (s/cat :db ::ds/db)
  :ret (s/coll-of string?))

(defn list-patient-ids [db]
  (mapv :v (d/datoms db :aevt :Patient/id)))


(s/fdef list-resource
  :args (s/cat :db ::ds/db :data-type-name string?)
  :ret (s/coll-of ::ds/entity))

(defn list-resource
  "Returns a reducible collection of all resources (as Datomic entity) of `type`
  in `db`."
  [db type]
  (mapv
    (fn [{eid :e}]
      (d/entity db eid))
    (d/datoms db :aevt (keyword type "id"))))


(s/fdef list-resource-by-code
  :args (s/cat :db ::ds/db
               :data-type-name string?
               :code-property-name string?
               :codes (s/coll-of ::ds/entity-id)))

(defn list-resource-by-code
  [db data-type-name code-property-name codes]
  (let [code-index-attr (keyword (str data-type-name ".index") code-property-name)]
    (into
      []
      (comp
        (mapcat #(d/datoms db :vaet % code-index-attr))
        (map (fn [[e]] (d/entity db e))))
      codes)))


(s/fdef find-code
  :args (s/cat :db ::ds/db :system string? :code string?)
  :ret ::ds/entity-id)

(defn find-code [db system code]
  (d/q
    '[:find ?c .
      :in $ ?code ?system
      :where
      [?c :code/code ?code]
      [?c :code/system ?system]]
    db code system))


(defn- get-codes [db attr eid]
  (into #{} (map :v) (d/datoms db :eavt eid attr)))


(s/fdef list-patient-resource-by-code
  :args (s/cat :patient ::ds/entity
               :data-type-name string?
               :code-property-name string?
               :codes (s/coll-of ::ds/entity-id))
  :ret (s/coll-of ::ds/entity))

(defn list-patient-resource-by-code
  "Lists resources of `patient` and of a type specified by `data-type-name`
   which refer to one of the `codes` through a property called
   `code-property-name`."
  [patient data-type-name code-property-name codes]
  (with-open [_ (prom/timer call-seconds "list-patient-resource-by-code")]
    (let [db (d/entity-db patient)
          code-index-attr (keyword (str data-type-name ".index")
                                   code-property-name)
          subject-attr (keyword data-type-name "subject")]
      (into
        []
        (comp
          (map :e)
          (filter
            (fn [e]
              (let [cs (get-codes db code-index-attr e)]
                (some #(contains? cs %) codes))))
          (map (partial d/entity db)))
        (d/datoms db :vaet (:db/id patient) subject-attr)))))
