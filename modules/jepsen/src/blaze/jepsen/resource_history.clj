(ns blaze.jepsen.resource-history
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.fhir-client :as fhir-client]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.structure-definition-repo]
   [blaze.jepsen.util :as u]
   [clojure.tools.logging :refer [info]]
   [hato.client :as hc]
   [integrant.core :as ig]
   [jepsen.checker :as checker]
   [jepsen.cli :as cli]
   [jepsen.client :as client]
   [jepsen.generator :as gen]
   [jepsen.tests :as tests]
   [knossos.model :as model])
  (:import
   [knossos.model Model]))

(set! *warn-on-reflection* true)

(ig/init {:blaze.fhir/structure-definition-repo {}})

(defn read-history "Reads all history values." [_ _]
  {:type :invoke :f :read :value nil})

(defn add-history "Adds a random value to the history." [_ _]
  {:type :invoke :f :add :value (str (random-uuid))})

(defn reset-history "Resets the history to only it's first value." [_ _]
  {:type :invoke :f :reset :value nil})

(defrecord History [values]
  Model
  (step [r op]
    (condp identical? (:f op)
      :add (History. (cons (:value op) values))
      :reset (History. (some-> (first values) list))
      :read (if (or (nil? (:value op))                      ; We don't know what the read was
                    (= values (:value op)))                 ; Read was a specific value
              r
              (model/inconsistent
               (str (pr-str values) "≠" (pr-str (:value op)))))))

  Object
  (toString [_] (pr-str values)))

(defn client-read-history [{:keys [base-uri] :as context} id]
  @(-> (fhir-client/history-instance base-uri "Patient" id context)
       (ac/then-apply
        (fn [versions]
          {:type :ok :value (map (comp :value :value first :identifier) versions)}))
       (ac/exceptionally
        (fn [e]
          {:type (if (ba/not-found? e) :ok :fail) :value nil}))))

(defn client-add-history [{:keys [base-uri] :as context} id value]
  @(-> (fhir-client/update
        base-uri
        {:fhir/type :fhir/Patient :id id
         :identifier [(type/identifier {:value (type/string value)})]}
        context)
       (ac/then-apply (constantly {:type :ok}))
       (ac/exceptionally (constantly {:type :fail}))))

(defn client-reset-history [{:keys [base-uri] :as context} id]
  @(-> (fhir-client/delete-history base-uri "Patient" id context)
       (ac/then-apply (constantly {:type :ok}))
       (ac/exceptionally (fn [e] (prn e) {:type :fail}))))

(defrecord Client [context]
  client/Client
  (open! [this _test node]
    (info "Open client on node" node)
    (update this :context assoc
            :base-uri (str "http://" node "/fhir")
            :http-client (hc/build-http-client {:connect-timeout 10000})
            :parsing-context (:blaze.fhir/parsing-context u/system)
            :writing-context (:blaze.fhir/writing-context u/system)))

  (setup! [this _test]
    this)

  (invoke! [_ test op]
    (case (:f op)
      :read (merge op (client-read-history context (:id test)))
      :add (merge op (client-add-history context (:id test) (:value op)))
      :reset (merge op (client-reset-history context (:id test)))))

  (teardown! [this _test]
    this)

  (close! [_ _test]))

(defn blaze-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge
   tests/noop-test
   {:pure-generators true
    :name "resource-history"
    :remote (u/->Remote)
    :client (->Client {})
    :checker (checker/linearizable
              {:model (->History nil)
               :algorithm :linear})
    :generator (->> (gen/mix [read-history add-history])
                    (gen/limit 120)
                    (gen/then (gen/once reset-history))
                    (gen/cycle)
                    (gen/stagger (:delta-time opts))
                    (gen/nemesis [])
                    (gen/time-limit (:time-limit opts)))}
   opts))

(def cli-opts
  "Additional command line options."
  [[nil "--id ID" "The ID of the patient to use." :default (str (random-uuid))]
   [nil "--delta-time s" "The duration between requests."
    :default 0.1
    :parse-fn parse-double]])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn blaze-test :opt-spec cli-opts})
            args))
