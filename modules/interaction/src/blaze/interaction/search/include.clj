(ns blaze.interaction.search.include
  (:require
   [blaze.db.api :as d]))

(defn- forward-includes* [db handle {:keys [code target-type]}]
  (if target-type
    (d/include db handle code target-type)
    (d/include db handle code)))

(defn- reverse-includes* [db handle {:keys [source-type code]}]
  (d/rev-include db handle source-type code))

(defn- forward-includes [db include-defs key handle]
  (into
   []
   (comp
    (mapcat #(forward-includes* db handle %))
    (mapcat #(into [%] (forward-includes db include-defs :iterate %))))
   (get-in include-defs [key :forward (name (:fhir/type handle))])))

(defn- reverse-includes [db include-defs key handle]
  (into
   []
   (mapcat #(reverse-includes* db handle %))
   (concat
    (get-in include-defs [key :reverse (name (:fhir/type handle))])
    (get-in include-defs [key :reverse :any]))))

(defn- includes [db include-defs key handle]
  (-> (forward-includes db include-defs key handle)
      (into (reverse-includes db include-defs key handle))))

(defn add-includes
  "Returns a set of included resource handles of `resource-handles`."
  [db include-defs resource-handles]
  (into #{} (mapcat #(includes db include-defs :direct %)) resource-handles))
