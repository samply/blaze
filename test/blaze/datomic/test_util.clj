(ns blaze.datomic.test-util
  (:require
    [cheshire.core :as json]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [blaze.datomic.transaction :as tx]))


(defn with-resource
  "Returns a tuple of a database with the given resource and its entity id.

  The resource id of `type`, has a logical `id` and possibly `more` elements.

  Example: `(with-resource db \"Patient\" \"0\")`"
  {:arglists '([db type id & more])}
  [db type id & {:as more}]
  (let [{db :db-after :keys [tempids]}
        (d/with db [(merge {:db/id "tid" (keyword type "id") id :version 0} more)])
        id (d/resolve-tempid db tempids "tid")]
    [db id]))


(defn with-non-primitive
  {:arglists '([db & more])}
  [db & {:as more}]
  (let [{db :db-after :keys [tempids]}
        (d/with db [(merge {:db/id "tid"} more)])
        id (d/resolve-tempid db tempids "tid")]
    [db id]))


(defn with-code-system
  [db file]
  (d/with db (tx/resource-update db (json/parse-string (slurp file)))))


(defn with-code
  ([db system code]
   (with-code db system nil code))
  ([db system version code]
   (let [{db :db-after :keys [tempids]}
         (d/with db [(cond->
                       {:db/id "tid"
                        :code/id (str system "|" code)
                        :code/system system
                        :code/code code}
                       version
                       (assoc :code/version version))])
         id (d/resolve-tempid db tempids "tid")]
     [db id])))


(defn with-gender-code [db gender]
  (with-code db "http://hl7.org/fhir/administrative-gender" gender))


(defn with-icd10-code [db version code]
  (with-code db "http://hl7.org/fhir/sid/icd-10" version code))
