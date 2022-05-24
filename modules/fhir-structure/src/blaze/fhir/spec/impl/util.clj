(ns blaze.fhir.spec.impl.util
  (:require
    [clojure.alpha.spec :as s2]
    [clojure.string :as str]))


(defn update-extended-primitives [x]
  (if (map? x)
    (reduce-kv
      (fn [ret k v]
        (if (str/starts-with? (name k) "_")
          (if (map? v)
            (-> (update ret (keyword (subs (name k) 1)) (partial assoc v :value))
                (dissoc k))
            (dissoc ret k))
          ret))
      x
      x)
    ::s2/invalid))
