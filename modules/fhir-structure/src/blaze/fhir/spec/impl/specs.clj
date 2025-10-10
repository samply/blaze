(ns blaze.fhir.spec.impl.specs
  "Custom Specs for primitive and complex types."
  (:refer-clojure :exclude [meta])
  (:require
   [clojure.alpha.spec :as s]
   [clojure.alpha.spec.protocols :as sp]))

(set! *warn-on-reflection* true)

;; ---- Regex Spec ------------------------------------------------------------

(declare regex)

(defn- regex-impl
  [pattern f-form]
  (let [f (s/resolve-fn f-form)]
    (reify
      sp/Spec
      (conform* [_ x _ _]
        (if (and (string? x) (.matches (re-matcher pattern x)))
          (f x)
          ::s/invalid))
      (unform* [_ x] x)
      (explain* [_ path via in x _ _]
        (when-not (and (string? x) (re-matches pattern x))
          [{:path path :pred pattern :val x :via via :in in}]))
      (gen* [_ _ _ _])
      (with-gen* [_ _])
      (describe* [_] `(regex ~pattern ~f-form)))))

(defmethod s/expand-spec `regex
  [[_ pattern f-form]]
  {:clojure.spec/op `regex
   :pattern pattern
   :f-form f-form})

(defmethod s/create-spec `regex
  [{:keys [pattern f-form]}]
  (regex-impl pattern f-form))

;; ---- Record Spec -----------------------------------------------------------

(declare record)

(defn- record-impl [class-sym spec-forms]
  (let [class (resolve class-sym)
        specs (delay (update-vals spec-forms s/resolve-spec))]
    (reify
      sp/Spec
      (conform* [_ x _ settings]
        (if (instance? class x)
          (loop [ret (into {} x) [[k v] & ks] x]
            (if k
              (if (some? v)
                (let [conformed (if-let [sp (@specs k)] (sp/conform* sp v k settings) v)]
                  (if (s/invalid? conformed)
                    ::s/invalid
                    (recur (if-not (identical? v conformed) (assoc ret k conformed) ret) ks)))
                (recur ret ks))
              ret))
          ::s/invalid))
      (unform* [_ x] x)
      (explain* [_ _path _via _in _x _ _])
      (gen* [_ _ _ _])
      (with-gen* [_ _])
      (describe* [_] `(record ~class-sym ~spec-forms)))))

(defmethod s/expand-spec `record
  [[_ class-sym spec-forms]]
  {:clojure.spec/op `record
   :class-sym class-sym
   :spec-forms spec-forms})

(defmethod s/create-spec `record
  [{:keys [class-sym spec-forms]}]
  (record-impl class-sym spec-forms))
