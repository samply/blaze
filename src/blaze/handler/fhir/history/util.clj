(ns blaze.handler.fhir.history.util
  (:require
    [blaze.datomic.util :as datomic-util]
    [datomic.api :as d])
  (:import
    [java.time Instant]
    [java.util Date]))


(defn since-t
  "Uses the `_since` param to derive the since-t of db."
  {:arglists '([db params])}
  [db {since "_since"}]
  (when since
    (d/since-t (d/since db (Date/from (Instant/parse since))))))


(defn method [resource]
  (cond
    (= -3 (:instance/version resource)) "POST"
    (datomic-util/deleted? resource) "DELETE"
    :else "PUT"))


(defn url [base-uri type id version]
  (cond-> (str base-uri "/fhir/" type)
    (not= -3 version)
    (str "/" id)))


(defn status [resource]
  (cond
    (#{-3 -4} (:instance/version resource)) "201"
    (datomic-util/deleted? resource) "204"
    :else "200"))


(defn changed-resources [log db t]
  (into
    []
    (map #(d/entity db %))
    (let [version-attr-id (d/entid db :instance/version)]
      (sort
        (into
          #{}
          (comp
            (mapcat
              (fn [{:keys [data]}]
                data))
            (filter
              (fn [{:keys [a]}]
                (= version-attr-id a)))
            (map :e))
          (d/tx-range log t (inc t)))))))
