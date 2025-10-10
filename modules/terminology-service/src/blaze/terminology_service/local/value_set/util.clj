(ns blaze.terminology-service.local.value-set.util
  (:require
   [clojure.string :as str]))

(defn find-version [{:keys [system-versions]} system]
  (some
   #(let [[s v] (str/split (:value %) #"\|")]
      (when (= system s) v))
   system-versions))
