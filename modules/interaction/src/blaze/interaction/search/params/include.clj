(ns blaze.interaction.search.params.include
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.handler.fhir.util :as fhir-util]
    [clojure.string :as str]))


(defn- missing-code-msg [source-type]
  (format "Missing search parameter code in _include search parameter with source type `%s`." source-type))


(defn- forward-value [handling v]
  (let [[source-type code target-type] (str/split v #":")]
    (if code
      [source-type
       (cond-> {:code code}
         target-type
         (assoc :target-type target-type))]
      (when (identical? :blaze.preference.handling/strict handling)
        (ba/incorrect (missing-code-msg source-type))))))


(defn- forward-defs [handling name query-params]
  (transduce
    (comp
      (filter (fn [[k]] (= name k)))
      (mapcat
        (fn [[_k v]] (keep #(forward-value handling %) (fhir-util/to-seq v)))))
    (completing
      (fn [res x]
        (if (ba/anomaly? x)
          (reduced x)
          (let [[source-type include-def] x]
            (update res source-type (fnil conj []) include-def)))))
    {}
    query-params))


(defn- reverse-value [handling v]
  (let [[source-type code target-type] (str/split v #":")]
    (if code
      [(or target-type :any) {:source-type source-type :code code}]
      (when (identical? :blaze.preference.handling/strict handling)
        (ba/incorrect (missing-code-msg source-type))))))


(defn- reverse-defs [handling name query-params]
  (transduce
    (comp
      (filter (fn [[k]] (= name k)))
      (mapcat
        (fn [[_k v]] (keep #(reverse-value handling %) (fhir-util/to-seq v)))))
    (completing
      (fn [res x]
        (if (ba/anomaly? x)
          (reduced x)
          (let [[target-type include-def] x]
            (update res target-type (fnil conj []) include-def)))))
    {}
    query-params))


(defn include-defs [handling query-params]
  (when-ok [fwd-direct (forward-defs handling "_include" query-params)]
    (when-ok [fwd-iterate (forward-defs handling "_include:iterate"
                                        query-params)]
      (when-ok [rev-direct (reverse-defs handling "_revinclude" query-params)]
        (when-ok [rev-iterate (reverse-defs handling "_revinclude:iterate"
                                            query-params)]
          (cond-> nil
            (or (seq fwd-direct) (seq rev-direct))
            (assoc
              :direct
              (cond-> {}
                (seq fwd-direct)
                (assoc :forward fwd-direct)
                (seq rev-direct)
                (assoc :reverse rev-direct)))
            (or (seq fwd-iterate) (seq rev-iterate))
            (assoc
              :iterate
              (cond-> {}
                (seq fwd-iterate)
                (assoc :forward fwd-iterate)
                (seq rev-iterate)
                (assoc :reverse rev-iterate)))))))))
