(ns blaze.fhir.spec.impl.util
  (:require
    [clojure.alpha.spec :as s2]
    [clojure.string :as str]))


(defn- assoc-value [v m]
  (if (nil? v) m (if m (assoc m :value v) v)))


(defn- assoc-values [vs ms]
  (loop [[v & vs] vs
         [m & ms] ms
         ret []]
    (if (or v m)
      (recur vs ms (conj ret (assoc-value v m)))
      ret)))


(defn- trim-underscore [k]
  (keyword (subs (name k) 1)))


(defn update-extended-primitives [x]
  (if (map? x)
    (reduce-kv
      (fn [ret k v]
        (if (str/starts-with? (name k) "_")
          (cond
            (map? v)
            (-> (update ret (trim-underscore k) assoc-value v)
                (dissoc k))

            (vector? v)
            (-> (update ret (trim-underscore k) assoc-values v)
                (dissoc k))

            :else
            (dissoc ret k))
          ret))
      x
      x)
    ::s2/invalid))
