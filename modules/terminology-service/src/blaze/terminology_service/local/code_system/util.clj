(ns blaze.terminology-service.local.code-system.util
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.luid :as luid]
   [blaze.module :as m]))

(defn code-systems [db url]
  (d/pull-many db (vec (d/type-query db "CodeSystem" [["url" url]]))))

(defn code-system-versions [db url]
  (do-sync [code-systems (code-systems db url)]
    (into #{} (map (comp type/value :version)) code-systems)))

(defn tx-op [{:keys [url version] :as code-system} id]
  [:create (assoc code-system :id id)
   [["url" (type/value url)]
    ["version" (type/value version)]]])

(defn tx-ops [context existing-versions code-systems]
  (transduce
   (remove (comp existing-versions type/value :version))
   (fn
     ([{:keys [tx-ops]}] tx-ops)
     ([{:keys [luid-generator] :as ret} code-system]
      (-> (update ret :tx-ops conj (tx-op code-system (luid/head luid-generator)))
          (update :luid-generator luid/next))))
   {:tx-ops []
    :luid-generator (m/luid-generator context)}
   code-systems))
