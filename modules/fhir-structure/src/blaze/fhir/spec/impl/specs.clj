(ns blaze.fhir.spec.impl.specs
  "Custom Specs for primitive and complex types."
  (:refer-clojure :exclude [meta])
  (:require
    [blaze.fhir.spec.impl.util :as u]
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
  [[_ pattern f-form]]
  {:clojure.spec/op `regex
   :pattern pattern
   :f-form f-form})


(defmethod s/create-spec `regex
  [{:keys [pattern f-form]}]
  (regex-impl pattern f-form))



;; ---- JSON Regex Primitive Spec ---------------------------------------------

(declare json-regex-primitive)


(defn- json-regex-primitive-impl
  [pattern f-form]
  (let [extended-spec
        (delay
          (s/resolve-spec
            `(s/schema
               {:id (s/nilable string?)
                :extension (s/coll-of :fhir.json/Extension)
                :value (s/nilable (regex ~pattern identity))})))
        f (s/resolve-fn f-form)]
    (reify
      sp/Spec
      (conform* [_ x _ _]
        (cond
          (and (string? x) (re-matches pattern x)) (f x)

          (map? x)
          (let [conformed (s/conform @extended-spec x)]
            (if (or (s/invalid? conformed) (empty? conformed))
              ::s/invalid
              (f conformed)))

          :else ::s/invalid))
      (unform* [_ x] x)
      (explain* [_ path via in x _ _]
        (when-not (and (string? x) (re-matches pattern x))
          [{:path path :pred pattern :val x :via via :in in}]))
      (gen* [_ _ _ _])
      (with-gen* [_ _])
      (describe* [_] `(json-regex-primitive ~pattern ~f-form)))))


(defmethod s/expand-spec `json-regex-primitive
  [[_ pattern f-form]]
  {:clojure.spec/op `json-regex-primitive
   :pattern pattern
   :f-form f-form})


(defmethod s/create-spec `json-regex-primitive
  [{:keys [pattern f-form]}]
  (json-regex-primitive-impl pattern f-form))



;; ---- JSON Pred Primitive Spec ----------------------------------------------

(declare json-pred-primitive)


(defn- json-pred-primitive-impl
  [pred-sym f-form]
  (let [pred (resolve pred-sym)
        extended-spec
        (delay
          (s/resolve-spec
            `(s/schema
               {:id (s/nilable string?)
                :extension (s/coll-of :fhir.json/Extension)
                :value (s/nilable ~pred-sym)})))
        f (s/resolve-fn f-form)]
    (reify
      sp/Spec
      (conform* [_ x _ _]
        (cond
          (pred x) (f x)

          (map? x)
          (let [conformed (s/conform @extended-spec x)]
            (if (or (s/invalid? conformed) (empty? conformed))
              ::s/invalid
              (f conformed)))

          :else ::s/invalid))
      (unform* [_ x] x)
      (explain* [_ path via in x _ _]
        (when-not (pred x)
          [{:path path :pred pred :val x :via via :in in}]))
      (gen* [_ _ _ _])
      (with-gen* [_ _])
      (describe* [_] `(json-pred-primitive ~pred-sym ~f-form)))))


(defmethod s/expand-spec `json-pred-primitive
  [[_ pred-sym f-form]]
  {:clojure.spec/op `json-pred-primitive
   :pred-sym pred-sym
   :f-form f-form})


(defmethod s/create-spec `json-pred-primitive
  [{:keys [pred-sym f-form]}]
  (json-pred-primitive-impl pred-sym f-form))



;; ---- CBOR Pred Primitive Spec ----------------------------------------------

(declare cbor-primitive)


(defn- cbor-primitive-impl
  [f-form]
  (let [extended-spec
        (delay
          (s/resolve-spec
            `(s/schema
               {:id (s/nilable string?)
                :extension (s/coll-of :fhir.cbor/Extension)
                :value any?})))
        f (s/resolve-fn f-form)]
    (reify
      sp/Spec
      (conform* [_ x _ _]
        (if (map? x)
          (f (s/conform @extended-spec x))
          (f x)))
      (unform* [_ x] x)
      (explain* [_ _ _ _ _ _ _])
      (gen* [_ _ _ _])
      (with-gen* [_ _])
      (describe* [_] `(cbor-primitive ~f-form)))))


(defmethod s/expand-spec `cbor-primitive
  [[_ f-form]]
  {:clojure.spec/op `cbor-primitive
   :f-form f-form})


(defmethod s/create-spec `cbor-primitive
  [{:keys [f-form]}]
  (cbor-primitive-impl f-form))



;; ---- Record Spec -----------------------------------------------------------

(declare record)


(defn- record-impl [class-sym spec-forms]
  (let [class (resolve class-sym)
        specs (delay (update-vals spec-forms s/resolve-spec))]
    (reify
      sp/Spec
      (conform* [_ x _ settings]
        (if (instance? class x)
          (loop [ret x [[k v] & ks] x]
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


(declare json-object)


(defn- json-object-impl [constructor-sym spec-forms key-map]
  (let [constructor (resolve constructor-sym)
        specs (delay (update-vals spec-forms s/resolve-spec))
        internal-key #(key-map % %)]
    (reify
      sp/Spec
      (conform* [_ x _ settings]
        (if (map? x)
          (let [res (reduce-kv
                      (fn [ret k v]
                        (if-let [conformed (when-let [sp (@specs k)] (sp/conform* sp v k settings))]
                          (if (s/invalid? conformed)
                            (reduced ::s/invalid)
                            (assoc ret (internal-key k) conformed))
                          ret))
                      {}
                      (u/update-extended-primitives x))]
            (if (s/invalid? res)
              ::s/invalid
              (constructor res)))
          ::s/invalid))
      (unform* [_ x] x)
      (explain* [_ path via in x _ settings]
        (if (not (map? x))
          [{:path path :pred `map? :val x :via via :in in}]
          (reduce-kv
            (fn [p k v]
              (if-let [sp (@specs k)]
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
