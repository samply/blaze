(ns blaze.fhir.spec.impl.specs
  "Custom Specs for primitive and complex types."
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
      ;; Validates the properties without rebuilding the value, so that
      ;; conforming a FHIR type yields that very type again.
      ;;
      ;; The conformed properties are deliberately discarded. Assoc'ing them
      ;; back would flatten the value into a plain map, because a choice
      ;; property conforms to a `MapEntry` of tag and value, which no typed
      ;; field accepts. Nothing consumes the conformed value of a record spec
      ;; anyway: `conform-xml` is the only caller of `s/conform` and it uses the
      ;; `:fhir.xml` schema specs, and `unform*` is identity here.
      (conform* [_ x _ settings]
        (if (instance? class x)
          (loop [[[k v] & ks] x]
            (if k
              (if (or (nil? v) (nil? (@specs k))
                      (not (s/invalid? (sp/conform* (@specs k) v k settings))))
                (recur ks)
                ::s/invalid)
              x))
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
