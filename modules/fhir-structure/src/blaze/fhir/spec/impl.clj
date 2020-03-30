(ns blaze.fhir.spec.impl
  (:require
    [blaze.fhir.util :as u]
    [clojure.alpha.spec :as s]
    [cuerdas.core :as str]))


(defn type->spec [{:keys [code]}]
  (case code
    "http://hl7.org/fhirpath/System.String" `string?
    "http://hl7.org/fhirpath/System.Time" `string?
    "http://hl7.org/fhirpath/System.Date" `string?
    "http://hl7.org/fhirpath/System.DateTime" `string?
    "http://hl7.org/fhirpath/System.Integer" `int?
    "http://hl7.org/fhirpath/System.Decimal" `number?
    "http://hl7.org/fhirpath/System.Boolean" `boolean?
    (keyword "fhir" code)))



(defn split-path [path]
  (str/split path #"\."))


(defn key-name [last-path-part {:keys [code]}]
  (if (str/ends-with? last-path-part "[x]")
    (str/replace last-path-part "[x]" (str/capital code))
    last-path-part))


(defn path-parts->key [path-parts type]
  (keyword
    (str/join "." (cons "fhir" (butlast path-parts)))
    (key-name (last path-parts) type)))


(defn path->key [path type]
  (path-parts->key (split-path path) type))


(defn elem-def->spec-def [{:keys [path min max] [_ & more :as types] :type}]
  ;; TODO: handle :contentReference
  (into
    (if more
      [{:key (path->key path {:code ""})
        :min min
        :max max
        :spec-form `(s/or ~@(mapcat (fn [type] [(keyword (name (path->key path type))) (type->spec type)]) types))}]
      [])
    (map
      (fn [type]
        {:key (path->key path type)
         :min min
         :max max
         :spec-form (type->spec type)}))
    types))


(defn- spec-key [parent-path-parts path-part]
  (keyword (str/join "." (cons "fhir" parent-path-parts)) path-part))


(defn build-specs [parent-path-parts indexed-elem-defs]
  (into
    []
    (comp
      (filter (comp string? first))
      (mapcat
        (fn [[path-part {:keys [elem-def] :as more}]]
          (if (= 1 (count (keys more)))
            (elem-def->spec-def elem-def)
            (let [child-spec-defs
                  (build-specs (conj parent-path-parts path-part) more)
                  schema-spec-form
                  `(s/schema
                     ~(into
                        {}
                        (comp
                          (filter :key)
                          (map
                            (fn [{:keys [key max spec-form]}]
                              (let [child-spec
                                    (if (or (ident? spec-form) (= `s/or (first spec-form)))
                                      spec-form
                                      key)]
                                [(keyword (name key))
                                 (if (= "1" max)
                                   child-spec
                                   `(s/coll-of ~child-spec))]))))
                        child-spec-defs))]
              [child-spec-defs
               {:key (spec-key parent-path-parts path-part)
                :min (:min elem-def)
                :max (:max elem-def)
                :spec-form schema-spec-form}])))))
    indexed-elem-defs))


(defn index-by-path [elem-defs]
  (reduce
    (fn [res {:keys [path] :as elem-def}]
      (assoc-in res (split-path path) {:elem-def elem-def}))
    {}
    elem-defs))


(defn struct-def->spec-def [{{elem-defs :element} :snapshot}]
  #_(index-by-path elem-defs)
  (flatten (build-specs [] (index-by-path elem-defs))))


(defn to-predefine-spec-def [spec-def]
  (some #(when (= "fhir" (namespace (:key %))) {:key (:key %) :spec-form `any?}) spec-def))


(defn select-primitive-value
  "Selects the value out of primitive data-type specs."
  [data]
  (some
    (fn [{:keys [key] :as data}]
      (when (= "value" (name key))
        (update data :key #(apply keyword (str/split (namespace %) #"\.")))))
    data))


(defn fix-known-issues
  "See: https://chat.fhir.org/#narrow/stream/179177-conformance/topic/Int.20type.20question.20in.20R4.20StructureDef"
  [primitive-types]
  (mapv
    (fn [{:keys [key] :as primitive-type}]
      (if (#{:fhir/unsignedInt :fhir/positiveInt} key)
        (assoc primitive-type :spec-form `int?)
        primitive-type))
    primitive-types))


(defn register
  "Registers `spec-defs`"
  [spec-defs]
  (doseq [{:keys [key spec-form]} spec-defs]
    (s/register key (s/resolve-spec spec-form))))


;; register all primitive type specs without id and extension
(register (fix-known-issues (map select-primitive-value (map struct-def->spec-def (u/primitive-types)))))


;; preregister all complex type specs with any? because there are circles which
;; cause spec registration to fail
(register (map (comp to-predefine-spec-def struct-def->spec-def) (u/complex-types)))


;; register all complex type specs
(register (mapcat struct-def->spec-def (u/complex-types)))


;; register all resource specs
(register (mapcat struct-def->spec-def (u/resources)))


;; should be above 8000
(comment (count (keys (s/registry))))
