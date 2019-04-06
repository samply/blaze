(ns life-fhir-store.structure-definition
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [life-fhir-store.spec]
    [life-fhir-store.util :as u]))


(def ^:private has-type
  (filter :type))


(defn- resolveDataTypeChoices
  "Takes an element and returns multiple elements in case of data type choices.

  See: http://hl7.org/fhir/formats.html#choice"
  [{types :type :as element}]
  (if (next types)
    (for [{:keys [code] :as type} types]
      (-> element
          (update :path str/replace "[x]" (u/title-case code))
          (assoc :type [type])))
    [element]))


(s/fdef path->key
  :args (s/cat :path :fhir.element-definition/path)
  :ret :life.element-definition/key)

(defn- path->key [path]
  (let [parts (str/split path #"\.")]
    (keyword
      (some->> (butlast parts) (str/join "."))
      (last parts))))


(defn element-definition
  [{:keys [path max isSummary] [{type-code :code}] :type}]
  (cond->
    #:life.element-definition
        {:key (path->key path)
         :type {:life.element-definition.type/code type-code}
         :summary? isSummary}
    (not (str/blank? max))
    (assoc :life.element-definition/max (if (= "*" max) :* (Integer/parseInt max)))))


(s/fdef structure-definition
  :args (s/cat :structure-definition :fhir/structure-definition)
  :ret :life/structure-definition)

(defn structure-definition
  [{:keys [id name] {elements :element} :snapshot}]
  #:life.structure-definition
      {:id id
       :name name
       :elements
       (transduce
         (comp
           has-type
           (mapcat resolveDataTypeChoices))
         (completing
           (fn [r {:keys [path] :as element}]
             (assoc r (path->key path) (element-definition element))))
         {}
         elements)})


(s/fdef primitive-type?
  :args (s/cat :element :life/element-definition)
  :ret boolean?)

(defn primitive-type?
  {:arglists '([element-definition])}
  [{:life.element-definition/keys [type]}]
  (Character/isLowerCase ^char (first (:life.element-definition.type/code type))))


(s/fdef cardinality-many?
  :args (s/cat :element :life/element-definition)
  :ret boolean?)

(defn cardinality-many?
  [{:life.element-definition/keys [max]}]
  (or (= :* max) (< 1 max)))


(defn- read-structure-definition [file]
  (structure-definition (json/parse-string (slurp file) keyword)))


(defn read-structure-definitions [path]
  (reduce
    (fn [ret file]
      (let [{:life.structure-definition/keys [id] :as def}
            (read-structure-definition file)]
        (assoc ret id def)))
    {}
    (rest (file-seq (io/file path)))))
