(defproject life-fhir-store "0.1-SNAPSHOT"
  :description "A FHIR Store including a CQL Engine based on Datomic"
  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies
  [[aleph "0.4.6"]
   [bidi "2.1.5"]
   [camel-snake-kebab "0.4.0"]
   [cheshire "5.8.1"]
   [com.cognitect/anomalies "0.1.12"]
   [com.datomic/datomic-free "0.9.5697"]
   [com.taoensso/timbre "4.10.0"]
   [danlentz/clj-uuid "0.1.7"]
   [info.cqframework/cql-to-elm "1.3.10"]
   [integrant "0.7.0"]
   [org.clojars.akiel/datomic-spec "0.4-SNAPSHOT"]
   [org.clojars.akiel/datomic-tools "0.4"]
   [org.clojars.akiel/env-tools "0.2"]
   [org.clojars.akiel/spec-coerce "0.3"]
   [org.clojure/clojure "1.10.0"]
   [org.clojure/core.cache "0.7.2"]
   [phrase "0.3-alpha3"]
   [prom-metrics "0.5-alpha2"]
   [ring/ring-core "1.7.1"
    :exclusions [clj-time commons-codec commons-fileupload
                 commons-io crypto-equality crypto-random]]]

  :profiles
  {:dev
   {:source-paths ["dev"]
    :dependencies
    [[criterium "0.4.4"]
     [org.clojars.akiel/iota "0.1"]
     [org.clojure/test.check "0.9.0"]
     [org.clojure/tools.namespace "0.2.11"]]}
   :jdk-11
   {:dependencies
    [[javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
     [com.sun.xml.bind/jaxb-core "2.3.0.1"]
     [com.sun.xml.bind/jaxb-impl "2.3.2"]]}

   :uberjar
   {:aot [life-fhir-store.core]}}

  :main ^:skip-aot life-fhir-store.core)
