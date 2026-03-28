(ns build
  (:require
   [clojure.tools.build.api :as b])
  (:import
   [java.time LocalDate]))

(def lib 'samply/blaze)
(def version "1.6.2")
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
                    blaze.cache-collector
                    blaze.core
                    blaze.cql
                    blaze.db.kv.mem
                    blaze.db.kv.rocksdb
                    blaze.db.node
                    blaze.db.node.resource-indexer
                    blaze.db.resource-cache
                    blaze.db.resource-store.cassandra
                    blaze.db.resource-store.kv
                    blaze.db.search-param-registry
                    blaze.db.tx-cache
                    blaze.db.tx-log.kafka
                    blaze.db.tx-log.local
                    blaze.elm.expression.cache
                    blaze.fhir.operation.code-system.lookup
                    blaze.fhir.operation.code-system.validate-code
                    blaze.fhir.operation.compact
                    blaze.fhir.operation.cql
                    blaze.fhir.operation.evaluate-measure
                    blaze.fhir.operation.graphql
                    blaze.fhir.operation.graphql.middleware
                    blaze.fhir.operation.totals
                    blaze.fhir.operation.value-set.expand
                    blaze.fhir.operation.value-set.validate-code
                    blaze.fhir.parsing-context
                    blaze.fhir.structure-definition-repo
                    blaze.fhir.writing-context
                    blaze.handler.app
                    blaze.handler.health
                    blaze.http-client
                    blaze.interaction.conditional-delete-type
                    blaze.interaction.create
                    blaze.interaction.delete
                    blaze.interaction.delete-history
                    blaze.interaction.history.instance
                    blaze.interaction.history.system
                    blaze.interaction.history.type
                    blaze.interaction.read
                    blaze.interaction.search-compartment
                    blaze.interaction.search-system
                    blaze.interaction.search-type
                    blaze.interaction.transaction
                    blaze.interaction.update
                    blaze.interaction.vread
                    blaze.job-scheduler
                    blaze.job.async-interaction
                    blaze.job.compact
                    blaze.job.re-index
                    blaze.metrics.handler
                    blaze.metrics.registry
                    blaze.openid-auth
                    blaze.operation.graph
                    blaze.operation.patient.everything
                    blaze.operation.patient.purge
                    blaze.page-id-cipher
                    blaze.page-store.cassandra
                    blaze.page-store.local
                    blaze.rest-api
                    blaze.rest-api.async-status-cancel-handler
                    blaze.rest-api.async-status-handler
                    blaze.rest-api.batch-handler
                    blaze.rest-api.capabilities-handler
                    blaze.scheduler
                    blaze.server
                    blaze.terminology-service.extern
                    blaze.terminology-service.local
                    blaze.terminology-service.local.code-system.loinc
                    blaze.terminology-service.local.code-system.sct
                    blaze.terminology-service.not-available
                    blaze.thread-pool-executor-collector]
                  :compile-opts
                  {:direct-linking true
                   :elide-meta [:doc :file :line :added]}
                  :java-opts
                  ["--enable-native-access=ALL-UNNAMED"]})
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
            ".*-linux-riscv64.so$"
            ".*.dll$"
            ".*.jnilib$"]
           :conflict-handlers
           {"META-INF/io.netty.versions.properties" :append
            :default :warn}}))
