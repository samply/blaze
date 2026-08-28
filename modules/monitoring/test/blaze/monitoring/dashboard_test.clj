(ns blaze.monitoring.dashboard-test
  (:require
   [blaze.monitoring.dashboard :as dashboard]
   [blaze.monitoring.dashboard-spec]
   [blaze.test-util :as tu]
   [clojure.edn :as edn]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private source
  (edn/read-string (slurp "dashboard.edn")))

(defn- source-panels [source]
  (mapcat :panels (:rows source)))

(defn- source-panel-of [title]
  (first (filter (comp #{title} :title) (source-panels source))))

(defn- panels
  "Returns all panels of `dashboard`, without the row panels."
  [dashboard]
  (mapcat #(get % "panels") (get dashboard "panels")))

(defn- panel-of [dashboard title]
  (first (filter #(= title (get % "title")) (panels dashboard))))

(defn- minimal
  "Returns a minimal source with `rows`."
  [rows]
  {:title "Test" :variables [] :rows rows})

(defn- query [expr]
  {:expr expr :legend ""})

(defn- test-panel
  ([title]
   (test-panel title {}))
  ([title extra]
   (merge {:title title :unit "short" :queries [(query "up")]} extra)))

(deftest source-test
  (testing "every panel has a title"
    (is (every? (comp string? :title) (source-panels source))))

  (testing "every panel has at least one query"
    (is (every? (comp seq :queries) (source-panels source))))

  (testing "every query has an expression"
    (is (every? (comp string? :expr) (mapcat :queries (source-panels source)))))

  (testing "every panel uses a known unit"
    (is (empty? (remove dashboard/units (map :unit (source-panels source))))))

  (testing "every query of a panel with more than one query has its own legend,
            so that its series can be told apart"
    (is (every? (fn [{:keys [queries]}]
                  (or (= 1 (count queries))
                      (apply distinct? (map :legend queries))))
                (source-panels source))))

  (testing "panel count"
    (is (= 119 (count (source-panels source))))))

(deftest indexer-utilization-panel-test
  (testing "the indexer utilization is shown as two series"
    (let [[busy working] (:queries (source-panel-of "Indexer Utilization"))]

      (testing "the time the indexing loop isn't waiting for transactions"
        (is (str/includes? (:expr busy) "op=\"poll-tx-log\""))
        (is (not (str/includes? (:expr busy) "await-resources"))))

      (testing "the time it does work of its own, which subtracts the time it
                waits for the resource indexing as well"
        (is (str/includes? (:expr working) "poll-tx-log"))
        (is (str/includes? (:expr working) "await-resources"))))))

(deftest dashboard-test
  (testing "of the real source"
    (let [dashboard (dashboard/dashboard source)]
      (testing "carries the title"
        (is (= "Blaze" (get dashboard "title"))))

      (testing "carries no uid, so that Grafana assigns one on import"
        (is (not (contains? dashboard "uid"))))

      (testing "has one row panel per row"
        (is (= 17 (count (get dashboard "panels"))))
        (is (every? #(= "row" (get % "type")) (get dashboard "panels"))))

      (testing "has all panels nested in the row panels"
        (is (= 119 (count (panels dashboard)))))

      (testing "every panel is a timeseries panel"
        (is (every? #(= "timeseries" (get % "type")) (panels dashboard))))

      (testing "all ids are distinct"
        (is (= 136 (count (distinct (map #(get % "id")
                                         (concat (get dashboard "panels")
                                                 (panels dashboard))))))))

      (testing "is deterministic"
        (is (= dashboard (dashboard/dashboard source))))))

  (testing "the title is taken from the source"
    (given (dashboard/dashboard (minimal []))
      "title" := "Test"))

  (testing "rows become collapsed row panels"
    (given (first (get (dashboard/dashboard
                        (minimal [{:title "Row" :panels [(test-panel "P")]}]))
                       "panels"))
      "type" := "row"
      "title" := "Row"
      "collapsed" := true
      ["gridPos" "h"] := 1
      ["gridPos" "w"] := 24
      ["gridPos" "x"] := 0
      ["gridPos" "y"] := 0))

  (testing "a repeating row carries the variable name"
    (given (first (get (dashboard/dashboard
                        (minimal [{:title "Row" :repeat "database"
                                   :panels [(test-panel "P")]}]))
                       "panels"))
      "repeat" := "database"))

  (testing "panels of a row are nested in the row panel"
    (given (dashboard/dashboard
            (minimal [{:title "Row" :panels [(test-panel "P")]}]))
      ["panels" 0 "panels" 0 "title"] := "P"))

  (testing "a panel query becomes a target"
    (given (dashboard/dashboard
            (minimal [{:title "Row"
                       :panels [(test-panel
                                 "P" {:queries [{:expr "up" :legend "{{job}}"}]})]}]))
      ["panels" 0 "panels" 0 "targets" 0 "expr"] := "up"
      ["panels" 0 "panels" 0 "targets" 0 "legendFormat"] := "{{job}}"
      ["panels" 0 "panels" 0 "targets" 0 "refId"] := "A"
      ["panels" 0 "panels" 0 "targets" 0 "range"] := true
      ["panels" 0 "panels" 0 "targets" 0 "datasource" "uid"] := "${datasource}")))

(deftest ref-id-test
  (testing "ref ids are assigned in order"
    (is (= ["A" "B" "C" "D"]
           (map #(get % "refId")
                (get (panel-of
                      (dashboard/dashboard
                       (minimal [{:title "Row"
                                  :panels [(test-panel
                                            "P" {:queries (mapv query ["a" "b" "c" "d"])})]}]))
                      "P")
                     "targets"))))))

(defn- grid-positions [& row-panels]
  (map #(get % "gridPos")
       (panels (dashboard/dashboard
                (minimal [{:title "Row" :panels (vec row-panels)}])))))

(deftest grid-flow-test
  (testing "panels flow left to right"
    (is (= [{"h" 6 "w" 8 "x" 0 "y" 1}
            {"h" 6 "w" 8 "x" 8 "y" 1}
            {"h" 6 "w" 8 "x" 16 "y" 1}]
           (grid-positions (test-panel "A") (test-panel "B") (test-panel "C")))))

  (testing "panels wrap at 24 columns"
    (is (= [{"h" 6 "w" 8 "x" 0 "y" 1}
            {"h" 6 "w" 8 "x" 8 "y" 1}
            {"h" 6 "w" 8 "x" 16 "y" 1}
            {"h" 6 "w" 8 "x" 0 "y" 7}]
           (grid-positions (test-panel "A") (test-panel "B") (test-panel "C")
                           (test-panel "D")))))

  (testing "a panel that doesn't fit starts a new line"
    (is (= [{"h" 6 "w" 20 "x" 0 "y" 1}
            {"h" 6 "w" 8 "x" 0 "y" 7}]
           (grid-positions (test-panel "A" {:width 20}) (test-panel "B")))))

  (testing "a full width panel occupies a line of its own"
    (is (= [{"h" 6 "w" 24 "x" 0 "y" 1}
            {"h" 6 "w" 24 "x" 0 "y" 7}]
           (grid-positions (test-panel "A" {:width 24})
                           (test-panel "B" {:width 24})))))

  (testing "the height of a line is the maximum height of its panels"
    (is (= [{"h" 6 "w" 12 "x" 0 "y" 1}
            {"h" 9 "w" 12 "x" 12 "y" 1}
            {"h" 6 "w" 8 "x" 0 "y" 10}]
           (grid-positions (test-panel "A" {:width 12})
                           (test-panel "B" {:width 12 :height 9})
                           (test-panel "C")))))

  (testing "rows are placed below each other"
    (is (= [{"h" 1 "w" 24 "x" 0 "y" 0}
            {"h" 1 "w" 24 "x" 0 "y" 7}]
           (map #(get % "gridPos")
                (get (dashboard/dashboard
                      (minimal [{:title "A" :panels [(test-panel "A")]}
                                {:title "B" :panels [(test-panel "B")]}]))
                     "panels"))))))

(deftest field-config-test
  (testing "the unit is taken from the source"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row"
                                 :panels [(test-panel "P" {:unit "bytes"})]}]))
                     "P")
      ["fieldConfig" "defaults" "unit"] := "bytes"))

  (testing "the minimum defaults to zero"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row" :panels [(test-panel "P")]}]))
                     "P")
      ["fieldConfig" "defaults" "min"] := 0
      ["fieldConfig" "defaults" "max"] := nil))

  (testing "an explicit nil minimum removes the minimum"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row"
                                 :panels [(test-panel "P" {:min nil})]}]))
                     "P")
      ["fieldConfig" "defaults" "min"] := nil))

  (testing "the maximum is taken from the source"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row"
                                 :panels [(test-panel "P" {:max 1})]}]))
                     "P")
      ["fieldConfig" "defaults" "max"] := 1))

  (testing "stacking is off by default"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row" :panels [(test-panel "P")]}]))
                     "P")
      ["fieldConfig" "defaults" "custom" "stacking" "mode"] := "none"
      ["fieldConfig" "defaults" "custom" "spanNulls"] := false
      ["fieldConfig" "defaults" "custom" "fillOpacity"] := 10
      ["fieldConfig" "defaults" "custom" "showPoints"] := "never"))

  (testing "stacking can be switched on"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row"
                                 :panels [(test-panel
                                           "P" {:stacking true :span-nulls true
                                                :fill-opacity 0
                                                :show-points "auto"})]}]))
                     "P")
      ["fieldConfig" "defaults" "custom" "stacking" "mode"] := "normal"
      ["fieldConfig" "defaults" "custom" "spanNulls"] := true
      ["fieldConfig" "defaults" "custom" "fillOpacity"] := 0
      ["fieldConfig" "defaults" "custom" "showPoints"] := "auto"))

  (testing "overrides are passed through"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row"
                                 :panels [(test-panel
                                           "P" {:overrides [{"matcher" {"id" "byName"}}]})]}]))
                     "P")
      ["fieldConfig" "overrides" 0 "matcher" "id"] := "byName"))

  (testing "overrides are empty by default"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row" :panels [(test-panel "P")]}]))
                     "P")
      ["fieldConfig" "overrides"] := [])))

(deftest options-test
  (testing "the legend is shown by default"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row" :panels [(test-panel "P")]}]))
                     "P")
      ["options" "legend" "showLegend"] := true
      ["options" "legend" "calcs"] := []
      ["options" "legend" "placement"] := "bottom"
      ["options" "tooltip" "mode"] := "multi"))

  (testing "the legend can be switched off and configured"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row"
                                 :panels [(test-panel
                                           "P" {:legend false
                                                :legend-calcs ["mean"]
                                                :legend-placement "right"
                                                :tooltip-mode "single"})]}]))
                     "P")
      ["options" "legend" "showLegend"] := false
      ["options" "legend" "calcs"] := ["mean"]
      ["options" "legend" "placement"] := "right"
      ["options" "tooltip" "mode"] := "single")))

(deftest panel-repeat-test
  (testing "a repeating panel carries the variable name and direction"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row"
                                 :panels [(test-panel "P" {:repeat "quantile"})]}]))
                     "P")
      "repeat" := "quantile"
      "repeatDirection" := "h"
      "maxPerRow" := nil))

  (testing "the maximum number of repetitions per line is passed through"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row"
                                 :panels [(test-panel "P" {:repeat "quantile"
                                                           :max-per-row 3})]}]))
                     "P")
      "maxPerRow" := 3))

  (testing "a panel without a description omits it"
    (is (nil? (get (panel-of (dashboard/dashboard
                              (minimal [{:title "Row" :panels [(test-panel "P")]}]))
                             "P")
                   "description"))))

  (testing "a description is passed through"
    (given (panel-of (dashboard/dashboard
                      (minimal [{:title "Row"
                                 :panels [(test-panel "P" {:description "Desc."})]}]))
                     "P")
      "description" := "Desc.")))

(deftest variables-test
  (testing "a datasource variable"
    (given (first (get (get (dashboard/dashboard
                             (assoc (minimal [])
                                    :variables
                                    [{:type :datasource :name "datasource"
                                      :label "Data source"
                                      :plugin-id "prometheus"}]))
                            "templating")
                       "list"))
      "type" := "datasource"
      "name" := "datasource"
      "label" := "Data source"
      "query" := "prometheus"
      "current" := {}))

  (testing "a query variable"
    (given (first (get (get (dashboard/dashboard
                             (assoc (minimal [])
                                    :variables
                                    [{:type :query :name "job" :label "Job"
                                      :query "label_values(up,job)"
                                      :sort :alphabetical-asc}]))
                            "templating")
                       "list"))
      "type" := "query"
      "name" := "job"
      "label" := "Job"
      "definition" := "label_values(up,job)"
      ["query" "query"] := "label_values(up,job)"
      "sort" := 1
      "current" := {}
      "refresh" := 2))

  (testing "a query variable without sorting and label"
    (given (first (get (get (dashboard/dashboard
                             (assoc (minimal [])
                                    :variables
                                    [{:type :query :name "instance"
                                      :query "label_values(up,instance)"}]))
                            "templating")
                       "list"))
      "sort" := 0
      "label" := nil))

  (testing "a custom variable"
    (given (first (get (get (dashboard/dashboard
                             (assoc (minimal [])
                                    :variables
                                    [{:type :custom :name "database"
                                      :label "Database" :description "Kind."
                                      :multi true
                                      :values ["index" "transaction" "resource"]
                                      :selected ["index" "resource"]}]))
                            "templating")
                       "list"))
      "type" := "custom"
      "name" := "database"
      "label" := "Database"
      "description" := "Kind."
      "multi" := true
      "hide" := 0
      "query" := "index,transaction,resource"
      ["current" "value"] := ["index" "resource"]
      ["options" 0 "selected"] := true
      ["options" 1 "selected"] := false))

  (testing "a hidden single-value custom variable"
    (given (first (get (get (dashboard/dashboard
                             (assoc (minimal [])
                                    :variables
                                    [{:type :custom :name "quantile" :hide true
                                      :values ["0.5" "0.9"] :selected ["0.9"]}]))
                            "templating")
                       "list"))
      "hide" := 2
      "multi" := false
      ["current" "value"] := "0.9")))
