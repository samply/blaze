(ns blaze.interaction.search.include
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.api :as d]
   [clojure.set :as set]))

(defn- forward-includes* [db handle {:keys [code target-type]}]
  (if target-type
    (d/include db handle code target-type)
    (d/include db handle code)))

(defn- reverse-includes* [db handle {:keys [source-type code]}]
  (d/rev-include db handle source-type code))

(defn- forward-includes [db include-defs key handle]
  (into
   #{}
   (mapcat #(forward-includes* db handle %))
   (get-in include-defs [key :forward (name (:fhir/type handle))])))

(defn- reverse-includes [db include-defs key handle]
  (into
   #{}
   (mapcat #(reverse-includes* db handle %))
   (concat
    (get-in include-defs [key :reverse (name (:fhir/type handle))])
    (get-in include-defs [key :reverse :any]))))

(defn- includes* [db include-defs key handle]
  (set/union (forward-includes db include-defs key handle)
             (reverse-includes db include-defs key handle)))

(defn- includes [db include-defs key handles]
  (apply set/union (mapv #(includes* db include-defs key %) handles)))

(def ^:private ^:const ^long max-size 10000)

(def ^:private too-costly-msg
  (format "Inclusion(s) would return more than %,d resources which is too costly to output. Please either lower the page size or use $graphql or $graph operations." max-size))

(defn add-includes
  "Returns a set of included resource handles of `resource-handles`."
  [db include-defs resource-handles]
  (loop [handles (includes db include-defs :direct resource-handles)]
    (let [new-handles (set/union handles (includes db include-defs :iterate handles))]
      (condp < (count new-handles)
        max-size (ba/conflict too-costly-msg :fhir/issue "too-costly")
        (count handles) (recur new-handles)
        handles))))
