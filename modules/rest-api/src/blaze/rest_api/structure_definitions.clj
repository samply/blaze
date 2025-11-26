(ns blaze.rest-api.structure-definitions
  "Functions for ensuring that base structure definitions are available in the
  database node."
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.luid :as luid]
   [blaze.module :as m]
   [clojure.string :as str]
   [jsonista.core :as j]
   [taoensso.timbre :as log]))

(def ^:private read-only-tag
  #fhir/Coding
   {:system #fhir/uri-interned "https://samply.github.io/blaze/fhir/CodeSystem/AccessControl"
    :code #fhir/code "read-only"})

(defn- structure-definitions [db]
  (d/pull-many db (vec (d/type-list db "StructureDefinition"))))

(def ^:private url-filter
  (filter #(str/starts-with? % "http://hl7.org/fhir/StructureDefinition")))

(defn- structure-definition-urls [db]
  (do-sync [structure-definitions (structure-definitions db)]
    (into #{} (comp (map (comp :value :url)) url-filter) structure-definitions)))

(defn- tx-op [{:keys [url] :as structure-definition} luid-generator]
  [:create (assoc structure-definition :id (luid/head luid-generator))
   [["url" (:value url)]]])

(defn- tx-ops [context existing-urls structure-definitions]
  (transduce
   (remove (comp existing-urls :value :url))
   (fn
     ([{:keys [tx-ops]}] tx-ops)
     ([{:keys [luid-generator] :as ret} structure-definition]
      (-> (update ret :tx-ops conj (tx-op structure-definition luid-generator))
          (update :luid-generator luid/next))))
   {:tx-ops []
    :luid-generator (m/luid-generator context)}
   structure-definitions))

(defn- conform [parsing-context resource]
  (->> (j/write-value-as-bytes resource)
       (fhir-spec/parse-json parsing-context "StructureDefinition")))

(defn- append-read-only-tag [resource]
  (update-in resource [:meta :tag] (fnil conj []) read-only-tag))

(defn- resources-and-types [parsing-context structure-definition-repo]
  (let [f (comp append-read-only-tag (partial conform parsing-context))]
    (into (mapv f (sdr/resources structure-definition-repo))
          (map f) (sdr/complex-types structure-definition-repo))))

(defn ensure-structure-definitions
  "Ensures that all StructureDefinition resources are present in the database node."
  {:arglists '([context])}
  [{:keys [node parsing-context structure-definition-repo] :as context}]
  (-> (structure-definition-urls (d/db node))
      (ac/then-compose
       (fn [existing-urls]
         (let [tx-ops (tx-ops context existing-urls (resources-and-types parsing-context structure-definition-repo))]
           (if (seq tx-ops)
             (do (log/debug "Create" (count tx-ops) "new StructureDefinition resources...")
                 (d/transact node tx-ops))
             (ac/completed-future nil)))))))
