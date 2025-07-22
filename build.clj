(ns build
  (:require
   [clojure.tools.build.api :as b])
  (:import
   [java.time LocalDate]))

(def lib 'samply/blaze)
(def version "1.0.4")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/write-file {:path (str class-dir "/blaze/version.edn")
                 :content {:blaze/version version
                           :blaze/release-date (str (LocalDate/now))}})
  (b/compile-clj {:basis basis
                  :class-dir class-dir
                  :ns-compile
                  '[blaze.admin-api
                    blaze.handler.app
                    blaze.handler.health
                    blaze.cache-collector
                    blaze.elm.expression.cache
                    blaze.db.node
                    blaze.db.resource-cache
                    blaze.db.search-param-registry
                    blaze.db.tx-cache
                    blaze.db.node.resource-indexer
                    blaze.db.tx-log.local
                    blaze.db.resource-store.kv
                    blaze.db.resource-store.cassandra
                    blaze.db.tx-log.kafka
                    blaze.terminology-service.extern
                    blaze.fhir.parsing-context
                    blaze.fhir.structure-definition-repo
                    blaze.fhir.writing-context
                    blaze.http-client
                    blaze.interaction.conditional-delete-type
                    blaze.interaction.create
                    blaze.interaction.delete
                    blaze.interaction.delete-history
                    blaze.interaction.read
                    blaze.interaction.search-compartment
                    blaze.interaction.search-system
                    blaze.interaction.search-type
                    blaze.interaction.transaction
                    blaze.interaction.update
                    blaze.interaction.vread
                    blaze.interaction.history.instance
                    blaze.interaction.history.system
                    blaze.interaction.history.type
                    blaze.job.async-interaction
                    blaze.job.compact
                    blaze.job.re-index
                    blaze.job-scheduler
                    blaze.db.kv.mem
                    blaze.metrics.handler
                    blaze.openid-auth
                    blaze.fhir.operation.code-system.validate-code
                    blaze.fhir.operation.compact
                    blaze.fhir.operation.graphql
                    blaze.fhir.operation.graphql.middleware
                    blaze.fhir.operation.evaluate-measure
                    blaze.operation.patient.everything
                    blaze.operation.patient.purge
                    blaze.fhir.operation.totals
                    blaze.fhir.operation.value-set.expand
                    blaze.fhir.operation.value-set.validate-code
                    blaze.page-id-cipher
                    blaze.page-store.local
                    blaze.page-store.cassandra
                    blaze.rest-api
                    blaze.rest-api.async-status-cancel-handler
                    blaze.rest-api.async-status-handler
                    blaze.rest-api.batch-handler
                    blaze.rest-api.capabilities-handler
                    blaze.db.kv.rocksdb
                    blaze.scheduler
                    blaze.server
                    blaze.terminology-service.local
                    blaze.terminology-service.local.code-system.loinc
                    blaze.terminology-service.local.code-system.sct
                    blaze.thread-pool-executor-collector
                    blaze.core]
                  :compile-opts
                  {:direct-linking true
                   :elide-meta [:doc :file :line :added]}})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'blaze.core
           :exclude
           ["^about.html"
            "^META-INF/versions/\\d+/module-info.class"
            "^HISTORY-JAVA.md"
            "^dse_protocol_v\\d.spec"
            "^native_protocol_v\\d.spec"
            ".*-musl.so$"
            ".*-ppc64le.so$"
            ".*-s390x.so$"
            ".*-linux32.so$"
            ".*.dll$"
            ".*.jnilib$"]
           :conflict-handlers
           {"META-INF/io.netty.versions.properties" :append
            :default :warn}}))
