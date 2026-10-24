(ns blaze.jepsen.independent-register
  "The register test over many independent patients, checking the
  linearizability of each patient on its own.

  Groups of threads work on one patient each, moving on to a new patient after
  a fixed number of operations. Running Blaze with a resource handle cache that
  is smaller than the patients worked on concurrently, keeps the cache evicting
  their entries. So readers insert entries again and again while other threads
  write the same patient, which tests that a reader never inserts an outdated
  resource handle."
  (:require
   [blaze.jepsen.register :as register]
   [blaze.jepsen.util :as u]
   [clojure.tools.logging :refer [info]]
   [jepsen.checker :as checker]
   [jepsen.cli :as cli]
   [jepsen.client :as client]
   [jepsen.generator :as gen]
   [jepsen.independent :as independent]
   [jepsen.tests :as tests]
   [knossos.model :as model]))

(defn- patient-id
  "Returns the id of the patient of `key` in the test with `id`."
  [id key]
  (str id "-" key))

(defrecord Client [context]
  client/Client
  (open! [this _test node]
    (info "Open client on node" node)
    (update this :context merge (u/client-context node)))

  (setup! [this _test]
    this)

  (invoke! [_ test op]
    (let [[key value] (:value op)
          id (patient-id (:id test) key)]
      (case (:f op)
        :read (let [{:keys [type value]} (register/read context id)]
                (assoc op :type type :value (independent/tuple key value)))
        :write (merge op (register/write! context id value)))))

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
    :name "independent-register"
    :remote (u/->Remote)
    :client (->Client {})
    :checker (independent/checker
              (checker/linearizable
               {:model (model/register)
                :algorithm :linear}))
    :generator (->> (independent/concurrent-generator
                     (:threads-per-key opts)
                     (range)
                     (fn [_]
                       (->> (gen/mix [register/r register/w])
                            (gen/stagger (:delta-time opts))
                            (gen/limit (:ops-per-key opts)))))
                    (gen/nemesis [])
                    (gen/time-limit (:time-limit opts)))}
   opts))

(def cli-opts
  "Additional command line options."
  [[nil "--id ID" "The ID prefix of the patients to use." :default (str (random-uuid))]
   [nil "--delta-time s" "The duration between requests."
    :default 0.01
    :parse-fn parse-double]
   [nil "--threads-per-key n" "The number of threads working on one patient."
    :default 4
    :parse-fn parse-long]
   [nil "--ops-per-key n" "The number of operations on one patient."
    :default 200
    :parse-fn parse-long]])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn blaze-test :opt-spec cli-opts})
            args))
