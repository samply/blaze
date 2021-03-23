(ns blaze.interaction.search.include
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.fhir.spec.type :as type]))


(defn- add-includes* [db include-defs key handle]
  (let [type (name (type/type handle))]
    (when-let [{:keys [code target-type]} (get-in include-defs [key type])]
      (into
        []
        (mapcat #(into [%] (add-includes* db include-defs :iterate %)))
        (if target-type
          (d/include db handle code target-type)
          (d/include db handle code))))))


(defn add-includes
  "Returns a reducible collection of maps that contain the original resource
  handle as :match and all included resource handles as :includes."
  [db include-defs resource-handles]
  (coll/eduction
    (map
      (fn [handle]
        {:match handle
         :includes (add-includes* db include-defs :direct handle)}))
    resource-handles))


(defn build-page
  "Returns a map with :matches, :includes and a possible :next-match.

  :matches is a vector of all matches, while :includes is a set of all includes.

  Takes maps of one match and possibly more than one include as long as the
  total number of resource handles is smaller than `size`.

  Because `build-page` will not truncate includes, it will return more than
  `size` resource handles if the number of includes of the last map will
  overshoot `size`."
  [size matches+includes]
  (reduce
    (fn [{res-matches :matches res-includes :includes :as res}
         {:keys [match includes]}]
      (if (< (+ (count res-matches) (count res-includes)) size)
        (-> (update res :matches conj match)
            (update :includes into includes))
        (reduced (assoc res :next-match match))))
    {:matches [] :includes #{}}
    matches+includes))
