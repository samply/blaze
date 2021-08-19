(ns blaze.jepsen.register
  (:refer-clojure :exclude [read])
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir-client :as fhir-client]
    [blaze.jepsen.util :as u]
    [clojure.tools.logging :refer [info]]
    [cognitect.anomalies :as anom]
    [hato.client :as hc]
    [jepsen.checker :as checker]
    [jepsen.cli :as cli]
    [jepsen.client :as client]
    [jepsen.generator :as gen]
    [jepsen.tests :as tests]
    [knossos.model :as model])
  (:import
    [java.util UUID]))


(defn r [_ _]
  {:type :invoke :f :read :value nil})


(defn w [_ _]
  {:type :invoke :f :write :value (int (rand-int 100))})


(defn read [{:keys [base-uri] :as context} id]
  @(-> (fhir-client/read base-uri "Patient" id context)
       (ac/then-apply :multipleBirth)
       (ac/exceptionally
         (comp
           #(when-not (= ::anom/not-found (::anom/category (ex-data %)))
              (throw %))
           ex-cause))))


(defn write! [{:keys [base-uri] :as context} id value]
  @(fhir-client/update
     base-uri
     {:fhir/type :fhir/Patient :id id :multipleBirth value}
     context))


(defrecord Client [context]
  client/Client
  (open! [this _test node]
    (info "Open client on node" node)
    (update this :context assoc
            :base-uri (str "http://" node "/fhir")
            :http-client (hc/build-http-client {:connect-timeout 10000})))

  (setup! [this _test]
    this)

  (invoke! [_ test op]
    (case (:f op)
      :read (assoc op :type :ok :value (read context (:id test)))
      :write (do (write! context (:id test) (:value op))
                 (assoc op :type :ok))))

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
     :name "register"
     :remote (u/->Remote)
     :client (->Client {})
     :checker (checker/linearizable
                {:model (model/register)
                 :algorithm :linear})
     :generator (->> (gen/mix [r w])
                     (gen/stagger (:delta-time opts))
                     (gen/nemesis nil)
                     (gen/time-limit (:time-limit opts)))}
    opts))


(def cli-opts
  "Additional command line options."
  [[nil "--id ID" "The ID of the patient to use." :default (str (UUID/randomUUID))]
   [nil "--delta-time s" "The duration between requests."
    :default 0.1
    :parse-fn #(Double/parseDouble %)]])


(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn blaze-test :opt-spec cli-opts})
            args))
