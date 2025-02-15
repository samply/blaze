(ns blaze.terminology-service.local.code-system.util
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.luid :as luid]))

(defn code-systems [db url]
  (d/pull-many db (d/type-query db "CodeSystem" [["url" url]])))

(defn code-system-versions [db url]
  (do-sync [code-systems (code-systems db url)]
    (into #{} (map (comp type/value :version)) code-systems)))

(defn- luid-generator [{:keys [clock rng-fn]}]
  (luid/generator clock (rng-fn)))

(defn tx-ops [context existing-versions code-systems]
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
