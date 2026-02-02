(ns blaze.db.search-param-registry
  (:refer-clojure :exclude [get str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.coll.core :as coll]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.chained :as spc]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.module :as m]
   [blaze.util :refer [conj-vec str]]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache Caffeine LoadingCache]))

(set! *warn-on-reflection* true)

(defn parse
  "Parses the search parameter `param` relative to `type` and returns either a
  tuple of search parameter and modifier or an anomaly in case of errors."
  [search-param-registry type param]
  (p/-parse search-param-registry type param))

(defn get
  "Returns the search parameter with `code` and `type` or nil if not found."
  [search-param-registry code type]
  (p/-get search-param-registry code type))

(defn get-by-url
  "Returns the search parameter with `url` or nil if not found."
  [search-param-registry url]
  (p/-get-by-url search-param-registry url))

(defn all-types
  "Returns a set of all types with search parameters."
  [search-param-registry]
  (p/-all-types search-param-registry))

(defn list-by-type
  "Returns a seq of search params of `type` in no particular order."
  [search-param-registry type]
  (p/-list-by-type search-param-registry type))

(defn list-by-target-type
  "Returns a seq of search params of type reference which point to
  `target-type`."
  [search-param-registry target-type]
  (p/-list-by-target search-param-registry target-type))

(defn linked-compartments
  "Returns a list of compartments linked to `resource`.

  For example an Observation may be linked to the compartment `[\"Patient\" \"0\"]`
  because its subject points to this patient. Compartments are represented
  through a tuple of `code` and `id`."
  [search-param-registry resource]
  (p/-linked-compartments search-param-registry resource))

(defn compartment-resources
  "Returns a seq of `[type codes]` tuples of resources in compartment of
  `compartment-type` or a list of codes if the optional `type` is given.

  Example:
  * `[\"Observation\" [\"subject\" \"performer\"]]` and others for \"Patient\"
  * `[\"subject\"]` and others for \"Patient\" and \"Observation\""
  ([search-param-registry compartment-type]
   (p/-compartment-resources search-param-registry compartment-type))
  ([search-param-registry compartment-type type]
   (p/-compartment-resources search-param-registry compartment-type type)))

(defn patient-compartment-search-param-codes
  "Returns the search parameter codes for `type` in the patient compartment.

  Will be either `#{\"subject\"}`, `#{\"patient\"}` or
  `#{\"subject\" \"patient\"}`."
  [search-param-registry type]
  (p/-patient-compartment-search-param-codes search-param-registry type))

(deftype MemSearchParamRegistry [url-index index target-index compartment-index
                                 compartment-resource-index
                                 compartment-resource-index-by-type
                                 parse-cache
                                 patient-compartment-search-param-codes-cache]
  p/SearchParamRegistry
  (-parse [_ type s]
    (.get ^LoadingCache parse-cache [type s]))

  (-get [_ code type]
    (or (get-in index [type code])
        (get-in index ["Resource" code])))

  (-get-by-url [_ url]
    (url-index url))

  (-all-types [_]
    (disj (set (keys index)) "Resource"))

  (-list-by-type [_ type]
    (-> (into [] (map val) (index "Resource"))
        (into (map val) (index type))))

  (-list-by-target [_ target]
    (target-index target))

  (-linked-compartments [_ resource]
    (transduce
     (comp
      (map
       (fn [{:keys [def-code search-param]}]
         (when-ok [compartment-ids (search-param/compartment-ids search-param resource)]
           (coll/eduction
            (map (fn [id] [def-code id]))
            compartment-ids))))
      (halt-when ba/anomaly?)
      cat)
     conj
     #{}
     (compartment-index (name (:fhir/type resource)))))

  (-compartment-resources [_ compartment-type]
    (compartment-resource-index compartment-type []))

  (-compartment-resources [_ compartment-type type]
    (get-in compartment-resource-index-by-type [compartment-type type] []))

  (-patient-compartment-search-param-codes [_ type]
    (.get ^LoadingCache patient-compartment-search-param-codes-cache type)))

(def ^:private object-mapper
  (j/object-mapper
   {:decode-key-fn true}))

(defn- read-json-resource [x]
  (with-open [rdr (io/reader x)]
    (j/read-value rdr object-mapper)))

(defn- read-classpath-json-resource
  "Reads the JSON encoded resource with `name` from classpath."
  [name]
  (log/trace (format "Read resource `%s` from class path." name))
  (read-json-resource (io/resource name)))

(defn- read-file-json-resource
  "Reads the JSON encoded resource with `name` from filesystem."
  [name]
  (log/trace (format "Read resource `%s` from filesystem." name))
  (read-json-resource name))

(defn read-standard-entries []
  (into
   []
   (remove
    (comp
     #{"http://hl7.org/fhir/SearchParameter/Bundle-message"
       "http://hl7.org/fhir/SearchParameter/Bundle-composition"}
     :fullUrl))
   (:entry (read-classpath-json-resource "blaze/db/search-parameters.json"))))

(defn- read-bundle-entries [extra-bundle-file]
  (cond-> (read-standard-entries)
    true
    (into (:entry (read-classpath-json-resource "blaze/db/Bundle-JobSearchParameterBundle.json")))
    extra-bundle-file
    (into (:entry (read-file-json-resource extra-bundle-file)))))

(defn- index-search-param [context {:keys [url] :as sp}]
  (if-ok [search-param (sc/search-param context sp)]
    (assoc-in context [:index url] search-param)
    #(if (ba/unsupported? %)
       context
       (reduced %))))

(defn- index-compartment-def
  {:arglists '([search-param-index compartment-def])}
  [search-param-index {def-code :code resource-defs :resource}]
  (into
   {}
   (map
    (fn [{res-type :code param-codes :param}]
      [res-type
       (into
        []
        (comp
         (keep #(get-in search-param-index [res-type %]))
         (map
          (fn [search-param]
            {:search-param search-param
             :def-code def-code})))
        param-codes)]))
   resource-defs))

(defn- index-compartment-resources [{def-code :code resource-defs :resource}]
  {def-code
   (into
    []
    (keep
     (fn [{res-type :code param-codes :param}]
       (when param-codes
         [res-type param-codes])))
    resource-defs)})

(defn- index-compartment-resources-by-type [{def-code :code resource-defs :resource}]
  {def-code
   (reduce
    (fn [res {res-type :code param-codes :param}]
      (cond-> res param-codes (assoc res-type param-codes)))
    {}
    resource-defs)})

(def ^:private list-search-param
  {:type "special"
   :name "_list"})

(def ^:private has-search-param
  {:type "special"
   :name "_has"})

(def ^:private near-search-param
  {:type "special"
   :name "near"})

(defn- add-special
  "Add special search params to :index in `context`.

  See: https://www.hl7.org/fhir/search.html#special"
  [context]
  (-> (assoc-in context [:index "Resource" "_list"] (sc/search-param context list-search-param))
      (assoc-in [:index "Resource" "_has"] (sc/search-param context has-search-param))
      (assoc-in [:index "Location" "near"] (sc/search-param context near-search-param))))

(defn- build-url-index* [context filter entries]
  (transduce
   (comp (map :resource)
         filter)
   (completing index-search-param)
   context
   entries))

(def ^:private remove-composite
  (remove (comp #{"composite"} :type)))

(def ^:private filter-composite
  (filter (comp #{"composite"} :type)))

(defn- search-param-context [code-expression? index]
  {:code-expression? code-expression?
   :index index})

(defn- build-url-index
  "Builds an index from url to search-param.

  Ensures that non-composite search params are build first so that composite
  search params will find it's components in the already partial build index."
  [code-expression? entries]
  (when-ok [non-composite (build-url-index*
                           (search-param-context code-expression? {})
                           remove-composite entries)]
    (build-url-index* non-composite filter-composite entries)))

(defn- build-index
  "Builds an index from [type code] to search param."
  [url-index entries]
  (transduce
   (map :resource)
   (completing
    (fn [index {:keys [url base code]}]
      (if-let [search-param (clojure.core/get url-index url)]
        (reduce
         (fn [index base]
           (assoc-in index [base code] search-param))
         index
         base)
        index)))
   {}
   entries))

(defn- build-target-index [url-index entries]
  (transduce
   (comp
    (map :resource)
    (filter (comp #{"reference"} :type)))
   (completing
    (fn [index {:keys [url target]}]
      (if-let [search-param (clojure.core/get url-index url)]
        (reduce
         (fn [index target]
           (update index target conj-vec search-param))
         index
         target)
        index)))
   {}
   entries))

(defn- search-param-not-found-msg [code type]
  (format "The search-param with code `%s` and type `%s` was not found."
          code type))

(defn- resolve-search-param [index type code]
  (if-let [search-param (or (get-in index [type code])
                            (get-in index ["Resource" code]))]
    search-param
    (ba/not-found (search-param-not-found-msg code type) :http/status 400)))

(defn- reference-type-msg [ref-code s type]
  (format "The search parameter with code `%s` in the chain `%s` must be of type reference but has type `%s`."
          ref-code s type))

(defn- ambiguous-target-type-msg [types s]
  (format "Ambiguous target types `%s` in the chain `%s`. Please use a modifier to constrain the type."
          types s))

(defn- parse-search-param* [index [type s]]
  (let [chain (str/split s #"\.")]
    (case (count chain)
      1
      (let [[code :as ret] (str/split (first chain) #":" 2)]
        (when-ok [search-param (resolve-search-param index type code)]
          (assoc ret 0 search-param)))

      2
      (let [[[ref-code ref-modifier] [code modifier]] (mapv #(str/split % #":" 2) chain)]
        (when-ok [{:keys [type target] :as ref-search-param} (resolve-search-param index type ref-code)]
          (cond
            (not= "reference" type)
            (ba/incorrect (reference-type-msg ref-code s type))

            (= 1 (count target))
            (when-ok [search-param (resolve-search-param index (first target) code)]
              (spc/chained-search-param search-param ref-search-param (first target)
                                        s modifier))

            ref-modifier
            (when-ok [search-param (resolve-search-param index ref-modifier code)]
              (spc/chained-search-param search-param ref-search-param ref-modifier
                                        s modifier))

            :else
            (ba/incorrect (ambiguous-target-type-msg (str/join ", " target) s)))))

      (ba/unsupported "Search parameter chains longer than 2 are currently not supported. Please file an issue."))))

(defn- patient-compartment-search-param-codes*
  [index compartment-resource-index-by-type type]
  (when (get-in compartment-resource-index-by-type ["Patient" type])
    (cond
      (nil? (get-in index [type "subject"])) #{"patient"}
      (nil? (get-in index [type "patient"])) #{"subject"}
      :else #{"subject" "patient"})))

(defmethod m/pre-init-spec :blaze.db/search-param-registry [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]
          :opt-un [::extra-bundle-file]))

(defmethod ig/init-key :blaze.db/search-param-registry
  [_ {:keys [structure-definition-repo extra-bundle-file]}]
  (log/info
   (cond-> "Init in-memory fixed R4 search parameter registry"
     extra-bundle-file
     (str " including extra search parameters from file: " extra-bundle-file)))
  (let [entries (read-bundle-entries extra-bundle-file)
        patient-compartment (read-classpath-json-resource "blaze/db/compartment/patient.json")
        code-expression? (sdr/code-expressions structure-definition-repo)]
    (if-ok [{url-index :index} (build-url-index code-expression? entries)]
      (let [index (build-index url-index entries)
            {:keys [index]} (add-special (search-param-context code-expression? index))
            index-compartment-resources-by-type (index-compartment-resources-by-type patient-compartment)]
        (->MemSearchParamRegistry
         url-index
         index
         (build-target-index url-index entries)
         (index-compartment-def index patient-compartment)
         (index-compartment-resources patient-compartment)
         index-compartment-resources-by-type
         (-> (Caffeine/newBuilder)
             (.maximumSize 1000)
             (.build (partial parse-search-param* index)))
         (-> (Caffeine/newBuilder)
             (.maximumSize 1000)
             (.build (partial patient-compartment-search-param-codes*
                              index index-compartment-resources-by-type)))))
      ba/throw-anom)))
