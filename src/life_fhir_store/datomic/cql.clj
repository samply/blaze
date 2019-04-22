(ns life-fhir-store.datomic.cql
  (:require
    [clojure.core.cache :as cache]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]))

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


(defn list-resource-by-code*
  [db data-type-name code-property-name codings]
  (mapv
    (partial d/entity db)
    (d/q
      [:find '[?e ...]
       :in '$ '[?coding ...]
       :where
       '[?c :CodeableConcept/coding ?coding]
       ['?e (keyword data-type-name code-property-name) '?c]]
      db codings)))


;; TODO: make cache controllable upstream
(def list-resource-by-code-cache (atom (cache/lru-cache-factory {})))


(s/fdef list-resource-by-code
  :args (s/cat :db ::ds/db
               :data-type-name string?
               :code-property-name string?
               :codings (s/coll-of ::ds/entity-id)))

(defn list-resource-by-code
  [db data-type-name code-property-name codings]
  (let [key [(d/basis-t db) data-type-name code-property-name codings]]
    (get (swap! list-resource-by-code-cache cache/through-cache
                key
                (fn [_]
                  (list-resource-by-code* db data-type-name code-property-name codings)))
         key)))


(s/fdef find-coding
  :args (s/cat :db ::ds/db :system string? :code string?)
  :ret ::ds/entity-id)

(defn find-coding [db system code]
  (d/q
    '[:find ?coding .
      :in $ ?code ?system
      :where
      [?coding :Coding/code ?code]
      [?coding :Coding/system ?system]]
    db code system))


(s/fdef list-patient-resource-by-code
  :args (s/cat :patient ::ds/entity
               :data-type-name string?
               :code-property-name string?
               :codings (s/coll-of ::ds/entity-id)))

(defn list-patient-resource-by-code
  [patient data-type-name code-property-name codings]
  (let [db (d/entity-db patient)]
    (mapv
      (partial d/entity db)
      (d/q
        [:find '[?e ...]
         :in '$ '?p '[?coding ...]
         :where
         ;; TODO: are there resources without a subject property?
         ['?e (keyword data-type-name "subject") '?p]
         ['?e (keyword data-type-name code-property-name) '?c]
         '[?c :CodeableConcept/coding ?coding]]
        db (:db/id patient) codings))))
