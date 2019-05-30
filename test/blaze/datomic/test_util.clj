(ns blaze.datomic.test-util
  (:require
    [blaze.datomic.util :as util]
    [clojure.test :refer :all]
    [datomic.api :as d]))


(defn with-resource
  "Returns a tuple of a database with the given resource and its entity id.

  The resource id of `type`, has a logical `id` and possibly `more` elements.

  Example: `(with-resource db \"Patient\" \"0\")`"
  {:arglists '([db type id & more])}
  [db type id & {:as more}]
  (let [tid (d/tempid (keyword "part" type))
        {db :db-after :keys [tempids]}
        (d/with db [(merge {:db/id tid (util/resource-id-attr type) id :version 0} more)])
        id (d/resolve-tempid db tempids tid)]
    [db id]))


(defn with-deleted-resource
  [db type id]
  (let [tid (d/tempid (keyword "part" type))
        {db :db-after :keys [tempids]}
        (d/with db [{:db/id tid (util/resource-id-attr type) id :version -1}])
        id (d/resolve-tempid db tempids tid)]
    [db id]))


(defn with-non-primitive
  {:arglists '([db & more])}
  [db attr value & {:as more}]
  (let [tid (d/tempid (keyword "part" (namespace attr)))
        {db :db-after :keys [tempids]}
        (d/with db [(merge {:db/id tid attr value} more)])
        id (d/resolve-tempid db tempids tid)]
    [db id]))


(defn with-code
  ([db code]
   (with-code db nil nil code))
  ([db system code]
   (with-code db system nil code))
  ([db system version code]
   (let [tid (d/tempid :part/code)
         {db :db-after :keys [tempids]}
         (d/with db [(cond->
                       {:db/id tid
                        :code/id (str system "|" version "|" code)}
                       system
                       (assoc :code/system system)
                       version
                       (assoc :code/version version)
                       code
                       (assoc :code/code code))])
         id (d/resolve-tempid db tempids tid)]
     [db id])))


(defn with-gender-code [db gender]
  (with-code db "http://hl7.org/fhir/administrative-gender" gender))


(defn with-icd10-code [db version code]
  (with-code db "http://hl7.org/fhir/sid/icd-10" version code))
