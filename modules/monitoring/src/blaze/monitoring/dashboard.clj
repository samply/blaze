(ns blaze.monitoring.dashboard
  "Transformation of the dashboard source data into the Grafana Classic
  dashboard model.

  The source data is the content of `dashboard.edn`. It only carries the
  information that has meaning: row and panel titles, descriptions, units,
  widths and queries. Everything else - the visualisation boilerplate, the grid
  positions, the panel ids and the query reference ids - is filled in here.

  The Classic model is the dashboard JSON model every Grafana from v10 on
  accepts on import."
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]))

(def ^:private grid-width
  "Width of the Grafana dashboard grid in columns."
  24)

(def ^:private default-panel-width 8)
(def ^:private default-panel-height 6)

(def ^:private row-panel-height
  "Height of a row panel, which is only the collapsible header."
  1)

(def ^:private plugin-version
  "The single version all panels are pinned to."
  "11.6.7")

(def ^:private schema-version 39)

(def units
  "Units used by the panels of the dashboard."
  #{"binBps" "bytes" "none" "ops" "percentunit" "s" "short"})

(def ^:private ref-ids
  "Query reference ids in the order they are assigned."
  (mapv str "ABCDEFGHIJKLMNOPQRSTUVWXYZ"))

(def ^:private datasource
  {"type" "prometheus"
   "uid" "${datasource}"})

(def ^:private thresholds
  {"mode" "absolute"
   "steps" [{"color" "green" "value" nil}
            {"color" "red" "value" 80}]})

(def ^:private base-custom
  "The visualisation defaults shared by all panels."
  {"axisBorderShow" false
   "axisCenteredZero" false
   "axisColorMode" "text"
   "axisLabel" ""
   "axisPlacement" "auto"
   "barAlignment" 0
   "barWidthFactor" 0.6
   "drawStyle" "line"
   "gradientMode" "none"
   "hideFrom" {"legend" false "tooltip" false "viz" false}
   "insertNulls" false
   "lineInterpolation" "linear"
   "lineWidth" 1
   "pointSize" 5
   "scaleDistribution" {"type" "linear"}
   "thresholdsStyle" {"mode" "off"}})

(defn- target [ref-id {:keys [expr legend]}]
  {"datasource" datasource
   "editorMode" "code"
   "expr" expr
   "legendFormat" legend
   "range" true
   "refId" ref-id})

(defn- custom [{:keys [stacking span-nulls fill-opacity show-points]}]
  (assoc base-custom
         "fillOpacity" (or fill-opacity 10)
         "showPoints" (or show-points "never")
         "spanNulls" (true? span-nulls)
         "stacking" {"group" "A" "mode" (if stacking "normal" "none")}))

(defn- field-config [{:keys [unit overrides] max-value :max :as panel}]
  {"defaults"
   (cond-> {"color" {"mode" "palette-classic"}
            "custom" (custom panel)
            "thresholds" thresholds
            "unit" unit}
     (some? (get panel :min 0)) (assoc "min" (get panel :min 0))
     (some? max-value) (assoc "max" max-value))
   "overrides" (vec overrides)})

(defn- options [{:keys [legend legend-calcs legend-placement tooltip-mode]}]
  {"legend" {"calcs" (vec legend-calcs)
             "displayMode" "list"
             "placement" (or legend-placement "bottom")
             "showLegend" (not (false? legend))}
   "tooltip" {"hideZeros" false
              "mode" (or tooltip-mode "multi")
              "sort" "none"}})

(defn- panel
  [id grid-pos {:keys [title description queries max-per-row] repeat-var :repeat
                :as panel}]
  (cond-> {"datasource" datasource
           "fieldConfig" (field-config panel)
           "gridPos" grid-pos
           "id" id
           "options" (options panel)
           "pluginVersion" plugin-version
           "targets" (mapv target ref-ids queries)
           "title" title
           "type" "timeseries"}
    description (assoc "description" description)
    repeat-var (assoc "repeat" repeat-var "repeatDirection" "h")
    max-per-row (assoc "maxPerRow" max-per-row)))

(defn- place
  "Places one panel of `width` and `height` into the flow state `state`,
  wrapping to the next line if it doesn't fit into the remaining columns."
  [{:keys [x y line-height] :as state} width height]
  (let [wrap? (< grid-width (+ x width))
        x (if wrap? 0 x)
        y (if wrap? (+ y line-height) y)]
    (-> (assoc state
               :x (+ x width)
               :y y
               :line-height (if wrap? height (max line-height height)))
        (update :grid-positions conj
                {"h" height "w" width "x" x "y" y}))))

(defn- grid-positions
  "Returns the grid positions of `panels`, flowing them left to right starting
  at `y` and wrapping at the grid width of 24 columns."
  [y panels]
  (reduce
   (fn [state {:keys [width height]}]
     (place state (or width default-panel-width)
            (or height default-panel-height)))
   {:x 0 :y y :line-height 0 :grid-positions []}
   panels))

(defn- row-panel [id y {:keys [title] repeat-var :repeat} panels]
  (cond-> {"collapsed" true
           "gridPos" {"h" row-panel-height "w" grid-width "x" 0 "y" y}
           "id" id
           "panels" panels
           "title" title
           "type" "row"}
    repeat-var (assoc "repeat" repeat-var)))

(defn- add-row [{:keys [y id] :as state} {row-panels :panels :as row}]
  (let [flow (grid-positions (+ y row-panel-height) row-panels)
        panels (mapv panel (iterate inc (inc id)) (:grid-positions flow)
                     row-panels)]
    (-> (assoc state
               :y (+ (:y flow) (:line-height flow))
               :id (+ id (count row-panels) 1))
        (update :panels conj (row-panel id y row panels)))))

(defn- panels
  "Returns the flat panel list of `rows`, each row being a collapsed row panel
  with its own panels nested inside."
  [rows]
  (:panels (reduce add-row {:y 0 :id 1 :panels []} rows)))

(defmulti ^:private variable
  "Returns the templating list entry of the source `variable`."
  :type)

(defmethod variable :datasource [{var-name :name :keys [label plugin-id]}]
  {"current" {}
   "hide" 0
   "includeAll" false
   "label" label
   "multi" false
   "name" var-name
   "options" []
   "query" plugin-id
   "refresh" 1
   "regex" ""
   "skipUrlSync" false
   "type" "datasource"})

(defmethod variable :query [{var-name :name :keys [label query sort]}]
  (cond-> {"current" {}
           "datasource" datasource
           "definition" query
           "hide" 0
           "includeAll" false
           "multi" false
           "name" var-name
           "options" []
           "query" {"query" query
                    "refId" "PrometheusVariableQueryEditor-VariableQuery"}
           ;; 2 means "On Time Range Change", so that the values a variable
           ;; offers match the time range the dashboard shows.
           "refresh" 2
           "regex" ""
           "skipUrlSync" false
           "sort" (if (= :alphabetical-asc sort) 1 0)
           "type" "query"}
    label (assoc "label" label)))

(defmethod variable :custom
  [{var-name :name :keys [label description multi hide values selected]}]
  (let [selected? (set selected)
        selected (filterv selected? values)
        current (if multi selected (first selected))]
    (cond-> {"current" {"text" current "value" current}
             "hide" (if hide 2 0)
             "includeAll" false
             "multi" (boolean multi)
             "name" var-name
             "options" (mapv #(hash-map "selected" (contains? selected? %)
                                        "text" % "value" %)
                             values)
             "query" (str/join "," values)
             "skipUrlSync" false
             "type" "custom"}
      label (assoc "label" label)
      description (assoc "description" description))))

(def ^:private annotations
  {"list" [{"builtIn" 1
            "datasource" {"type" "grafana" "uid" "-- Grafana --"}
            "enable" true
            "hide" true
            "iconColor" "rgba(0, 211, 255, 1)"
            "name" "Annotations & Alerts"
            "type" "dashboard"}]})

(def ^:private links
  [{"asDropdown" false
    "icon" "external link"
    "includeVars" false
    "keepTime" false
    "tags" []
    "targetBlank" false
    "title" "GitHub"
    "tooltip" ""
    "type" "link"
    "url" "https://github.com/samply/blaze"}])

(def ^:private timepicker
  {"refresh_intervals"
   ["5s" "10s" "30s" "1m" "5m" "15m" "30m" "1h" "2h" "1d"]})

(defn- sort-keys
  "Replaces every map in `x` with a sorted map, so that the JSON encoding of `x`
  is deterministic."
  [x]
  (walk/postwalk #(if (map? %) (into (sorted-map) %) %) x))

(defn dashboard
  "Returns the Grafana Classic dashboard model of the dashboard `source`."
  [{:keys [title variables rows]}]
  (sort-keys
   {"annotations" annotations
    "editable" true
    "graphTooltip" 1
    "links" links
    "panels" (panels rows)
    "refresh" "10s"
    "schemaVersion" schema-version
    "tags" []
    "templating" {"list" (mapv variable variables)}
    "time" {"from" "now-15m" "to" "now"}
    "timepicker" timepicker
    "timezone" "browser"
    "title" title}))
