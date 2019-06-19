(ns blaze.datomic.cql
  (:require
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [prometheus.alpha :as prom :refer [defhistogram]]))


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
  :ret ::ds/entity)

(defn find-code [db system code]
  (some->>
    (d/q
      '[:find ?c .
        :in $ ?code ?system
        :where
        [?c :code/code ?code]
        [?c :code/system ?system]]
      db code system)
    (d/entity db)))


(defn- contains-codes? [db attr eid codes]
  (some #(seq (d/datoms db :eavt eid attr %)) codes))


(s/fdef list-patient-resource-by-code
  :args (s/cat :patient ::ds/entity
               :subject-attr keyword?
               :code-index-attr keyword?
               :codes (s/coll-of ::ds/entity-id))
  :ret (s/coll-of ::ds/entity))

(defn list-patient-resource-by-code
  "Lists resources of `patient` and of a type specified by `data-type-name`
   which refer to one of the `codes` through a property called
   `code-property-name`."
  [patient subject-attr code-index-attr codes]
  (let [db (d/entity-db patient)]
    (into
      []
      (comp
        (map :e)
        (filter #(contains-codes? db code-index-attr % codes))
        (map #(d/entity db %)))
      (d/datoms db :vaet (:db/id patient) subject-attr))))
