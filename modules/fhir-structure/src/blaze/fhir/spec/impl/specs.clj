(ns blaze.fhir.spec.impl.specs
  "Custom Specs for primitive and complex types."
  (:refer-clojure :exclude [meta])
  (:require
    [clojure.alpha.spec :as s]
    [clojure.alpha.spec.protocols :as sp]))


(set! *warn-on-reflection* true)


(defn- regex-impl
  [pattern f-form]
  (let [f (s/resolve-fn f-form)]
    (reify
      sp/Spec
      (conform* [_ x _ _]
        (if (and (string? x) (re-matches pattern x))
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
  [[_ pattern f]]
  {:clojure.spec/op `regex
   :pattern pattern
   :f f})


(defmethod s/create-spec `regex
  [{:keys [pattern f]}]
  (regex-impl pattern f))



;; ---- Record Spec -----------------------------------------------------------

(defn- record-impl [class-sym spec-forms]
  (let [class (resolve class-sym)
        specs (delay (into {} (map #(vector (key %) (s/resolve-spec (val %)))) spec-forms))
        lookup #(get @specs %)]
    (reify
      sp/Spec
      (conform* [_ x _ settings]
        (if (instance? class x)
          (loop [ret x [[k v] & ks] x]
            (if k
              (if (some? v)
                (let [conformed (if-let [sp (lookup k)] (sp/conform* sp v k settings) v)]
                  (if (s/invalid? conformed)
                    ::s/invalid
                    (recur (if-not (identical? v conformed) (assoc ret k conformed) ret) ks)))
                (recur ret ks))
              ret))
          ::s/invalid))
      (unform* [_ x] x)
      (explain* [_ path via in x _ _])
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


(defn- maybe-spec
  "spec-or-k must be a spec, regex or resolvable kw/sym, else returns nil."
  [spec-or-k]
  (let [s (or (and (ident? spec-or-k) (#'s/reg-resolve spec-or-k))
              (s/spec? spec-or-k)
              (s/regex? spec-or-k)
              nil)]
    (if (s/regex? s)
      (#'s/with-name s (#'s/spec-name s))
      s)))


(defn- explain-1 [form pred path via in v settings-key settings]
  (let [pred (maybe-spec pred)]
    (if (s/spec? pred)
      (sp/explain* pred path (if-let [name (#'s/spec-name pred)] (conj via name) via) in v settings-key settings)
      [{:path path :pred form :val v :via via :in in}])))


(defn- json-object-impl [constructor-sym spec-forms key-map]
  (let [constructor (resolve constructor-sym)
        specs (delay (into {} (map #(vector (key %) (s/resolve-spec (val %)))) spec-forms))
        lookup #(get @specs %)
        internal-key #(get key-map % %)]
    (reify
      sp/Spec
      (conform* [_ x _ settings]
        (if (map? x)
          (loop [ret x [[k v] & ks] x]
            (if k
              (let [conformed (if-let [sp (lookup k)] (sp/conform* sp v k settings) v)]
                (if (s/invalid? conformed)
                  ::s/invalid
                  (let [k' (internal-key k)]
                    (if (identical? k' k)
                      (recur (if-not (identical? v conformed) (assoc ret k conformed) ret) ks)
                      (recur (-> ret (dissoc k) (assoc k' conformed)) ks)))))
              (constructor ret)))
          ::s/invalid))
      (unform* [_ x] x)
      (explain* [_ path via in x _ settings]
        (if (not (map? x))
          [{:path path :pred `map? :val x :via via :in in}]
          (reduce-kv
            (fn [p k v]
              (if-let [sp (lookup k)]
                (into p (explain-1 (s/form sp) sp (conj path k) via (conj in k) v k settings))
                p))
            [] x)))
      (gen* [_ _ _ _])
      (with-gen* [_ _])
      (describe* [_] `(json-object ~constructor-sym ~spec-forms ~key-map)))))


(defmethod s/expand-spec `json-object
  [[_ constructor-sym spec-forms key-map]]
  {:clojure.spec/op `json-object
   :constructor-sym constructor-sym
   :spec-forms spec-forms
   :key-map key-map})


(defmethod s/create-spec `json-object
  [{:keys [constructor-sym spec-forms key-map]}]
  (json-object-impl constructor-sym spec-forms key-map))
