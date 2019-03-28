(ns user
  (:require
    [cheshire.core :as json]
    [clojure.repl :refer [pst]]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [criterium.core :refer [bench quick-bench]]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [env-tools.alpha :as env-tools]
    [life-fhir-store.cql :as cql]
    [life-fhir-store.elm.compiler :as compiler]
    [life-fhir-store.elm.deps-infer :as deps-infer]
    [life-fhir-store.elm.evaluator :as evaluator]
    [life-fhir-store.elm.equiv-relationships :as equiv-relationships]
    [life-fhir-store.elm.normalizer :as normalizer]
    [life-fhir-store.elm.type-infer :as type-infer]
    [life-fhir-store.datomic.pull]
    [life-fhir-store.datomic.cql :as dcql]
    [life-fhir-store.datomic.schema :as schema]
    [life-fhir-store.datomic.search :as search]
    [life-fhir-store.datomic.time :as dtime]
    [life-fhir-store.datomic.transaction :as tx]
    [life-fhir-store.elm.literals]
    [life-fhir-store.fhir-client :as client]
    [life-fhir-store.spec]
    [life-fhir-store.structure-definition :refer [structure-definition]]
    [life-fhir-store.system :as system]
    [life-fhir-store.util :as util]
    [manifold.stream :as stream]
    [prometheus.alpha :as prom]
    [spec-coerce.alpha :refer [coerce]])
  (:import
    [io.prometheus.client CollectorRegistry]
    [java.time OffsetDateTime]))


;; Spec Instrumentation
(st/instrument)
(comment (st/unstrument))
(dst/instrument)


(defonce system nil)

(defn init []
  (let [config (coerce :system/config (env-tools/build-config :system/config))]
    (if (s/valid? :system/config config)
      (alter-var-root #'system (constantly (system/init! config)))
      (s/explain :system/config config))
    nil))

(defn reset []
  (system/shutdown! system)
  (refresh :after 'user/init))

;; Init Development
(comment
  (init)
  (pst)
  )

;; Reset after making changes
(comment
  (reset)
  )


(defn count-resources [db type]
  (d/q '[:find (count ?e) . :in $ ?id :where [?e ?id]] db (keyword type "id")))

(comment

  (d/create-database "datomic:mem://dev")
  (d/create-database "datomic:free://localhost:4334/dev-7?password=foo")
  (d/delete-database "datomic:mem://dev")
  (d/delete-database "datomic:mem://dev")
  (def conn (d/connect "datomic:mem://dev"))


  @(d/transact conn (schema/all-schema (vals (util/read-structure-definitions "fhir/r4"))))


  (count-resources (d/db conn) "Coding")
  (count-resources (d/db conn) "Organization")
  (count-resources (d/db conn) "Patient")
  (count-resources (d/db conn) "Specimen")
  (count-resources (d/db conn) "Observation")

  )

(comment

  (def conn (d/connect "datomic:free://localhost:4334/dev-7?password=foo"))
  (def db (d/db conn))
  (def now (OffsetDateTime/now))

  (def library (cql/translate (slurp "queries/time-based-2.cql")))

  (-> library
      normalizer/normalize-library
      equiv-relationships/find-equiv-rels-library
      deps-infer/infer-library-deps
      type-infer/infer-library-types
      )

  (def expression-defs
    (compiler/compile-library library {}))

  (time (into {} (filter (fn [[name]] (str/starts-with? name "Anzahl"))) @(evaluator/evaluate db now (compiler/compile-library library {}))))

  (compiler/-hash (:life/expression (nth expression-defs 3)))
  (pst)

  (dotimes [_ 10]
    (time (into {} (filter (fn [[name]] (str/starts-with? name "Anzahl")))
                @(evaluator/evaluate db now expression-defs))))

  (dtime/read (:Patient/birthDate (d/entity db [:Patient/id "1000"])))
  (d/pull db '[{:Observation/_subject [*]}] [:Patient/id "1000"])

  (.clear (CollectorRegistry/defaultRegistry))
  (.register (CollectorRegistry/defaultRegistry) compiler/evaluation-seconds)
  (prom/clear! compiler/evaluation-seconds)
  (println (:body (prom/dump-metrics)))

  )
