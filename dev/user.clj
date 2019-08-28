(ns user
  (:require
    [clojure.repl :refer [pst]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [criterium.core :refer [bench quick-bench]]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [env-tools.alpha :as env-tools]
    [blaze.cql-translator :as cql]
    [blaze.elm.compiler :as compiler]
    [blaze.elm.deps-infer :as deps-infer]
    [blaze.elm.evaluator :as evaluator]
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.literals]
    [blaze.elm.normalizer :as normalizer]
    [blaze.elm.type-infer :as type-infer]
    [blaze.datomic.cql :as datomic-cql]
    [blaze.datomic.pull]
    [blaze.datomic.util :as datomic-util]
    [blaze.spec]
    [blaze.system :as system]
    [prometheus.alpha :as prom]
    [spec-coerce.alpha :refer [coerce]])
  (:import
    [io.prometheus.client CollectorRegistry]
    [java.time OffsetDateTime]))


;; Spec Instrumentation
(st/instrument)
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
  (st/unstrument)
  )


(defn count-resources [db type]
  (d/q '[:find (count ?e) . :in $ ?id :where [?e ?id]] db (datomic-util/resource-id-attr type)))

(comment

  (def conn (:database-conn system))
  (def db (d/db conn))
  (def hdb (d/history db))

  (count-resources (d/db conn) "Coding")
  (count-resources (d/db conn) "Organization")
  (count-resources (d/db conn) "Patient")
  (count-resources (d/db conn) "Specimen")
  (count-resources (d/db conn) "Observation")

  (d/pull (d/db conn) '[*] 1262239348687945)
  (d/entity (d/db conn) [:Patient/id "0"])
  (d/q '[:find (pull ?e [*]) :where [?e :code/id]] (d/db conn))

  (d/pull (d/db conn) '[*] (d/t->tx 1197))

  )

(comment

  (def conn (d/connect "datomic:free://localhost:4334/dev-7?password=foo"))
  (def db (d/db conn))
  (def now (OffsetDateTime/now))

  (def library (cql/translate (slurp "queries/q1-patient-gender.cql")))

  (-> library
      normalizer/normalize-library
      equiv-relationships/find-equiv-rels-library
      deps-infer/infer-library-deps
      type-infer/infer-library-types
      )

  (def expression-defs
    (:life/compiled-expression-defs
      (compiler/compile-library db library {})))

  (time (into {} (filter (fn [[name]] (str/starts-with? name "Anzahl"))) @(evaluator/evaluate db now (compiler/compile-library db library {}))))

  (compiler/-hash (:life/expression (nth expression-defs 3)))
  (pst)

  (dotimes [_ 10]
    (time (into {} (filter (fn [[name]] (str/starts-with? name "Anzahl")))
                @(evaluator/evaluate db now expression-defs))))

  (d/pull db '[{:Observation/_subject [*]}] [:Patient/id "1000"])

  (.clear (CollectorRegistry/defaultRegistry))
  (.register (CollectorRegistry/defaultRegistry) compiler/evaluation-seconds)
  (.register (CollectorRegistry/defaultRegistry) datomic-cql/call-seconds)
  (prom/clear! compiler/evaluation-seconds)
  (prom/clear! datomic-cql/call-seconds)
  (println (:body (prom/dump-metrics)))

  )

;; Extract Codes from Code System
(comment
  (mapcat
    (fn [{:strs [code concept]}]
      (if concept
        (cons code (map #(get % "code") concept))
        [code]))
    (cheshire.core/parse-string ""))
  )
