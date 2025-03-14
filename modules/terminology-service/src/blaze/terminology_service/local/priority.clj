(ns blaze.terminology-service.local.priority
  (:require
   [blaze.async.comp]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.version :as version])
  (:import
   [java.util Comparator]))

(set! *warn-on-reflection* true)

(defn- t [resource]
  (:blaze.db/t (:blaze.db/tx (meta resource))))

(def ^:private priority-cmp
  (-> (Comparator/comparing #(-> % :status type/value) (Comparator/nullsFirst (.reversed (Comparator/naturalOrder))))
      (.thenComparing #(-> % :version type/value) version/cmp)
      (.thenComparing t (Comparator/naturalOrder))
      (.thenComparing #(% :id) (Comparator/naturalOrder))
      (.reversed)))

(defn sort-by-priority
  "Sorts `resources` by:
   * status
   * version
   * t
   * id"
  [resources]
  (sort priority-cmp resources))
