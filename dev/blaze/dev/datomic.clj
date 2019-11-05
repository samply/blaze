(ns blaze.dev.datomic
  (:require
    [blaze.datomic.util :as datomic-util]
    [blaze.dev :refer [system]]
    [datomic.api :as d]))


(defn count-resources [db type]
  (d/q '[:find (count ?e) . :in $ ?id :where [?e ?id]] db (datomic-util/resource-id-attr type)))


(comment
  (def conn (::conn system))
  (def db (d/db conn))
  (def hdb (d/history db))

  (count-resources (d/db conn) "Coding")
  (count-resources (d/db conn) "Organization")
  (count-resources (d/db conn) "Patient")
  (count-resources (d/db conn) "Specimen")
  (count-resources (d/db conn) "Observation")

  (d/pull (d/db conn) '[*] 1262239348687945)
  (d/entity (d/db conn) [:Patient/id "0"])
  (d/q '[:find (pull ?e [*]) :where [?e :code/id]] (d/db conn))

  (d/pull (d/db conn) '[*] (d/t->tx 1197))
  )

