(ns blaze.datomic.cql
  (:require
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]))


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

(defn find-code
  "Returns the code or nil if none is found."
  [db system code]
  (some->>
    (d/q
      '[:find ?c .
        :in $ ?code ?system
        :where
        [?c :code/code ?code]
        [?c :code/system ?system]]
      db code system)
    (d/entity db)))
