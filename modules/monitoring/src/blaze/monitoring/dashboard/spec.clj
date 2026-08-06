(ns blaze.monitoring.dashboard.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.monitoring.dashboard/title
  string?)

(s/def :blaze.monitoring.dashboard.variable/type
  #{:datasource :query :custom})

(s/def :blaze.monitoring.dashboard.variable/name
  string?)

(s/def :blaze.monitoring.dashboard.variable/label
  string?)

(s/def :blaze.monitoring.dashboard.variable/description
  string?)

(s/def :blaze.monitoring.dashboard.variable/plugin-id
  string?)

(s/def :blaze.monitoring.dashboard.variable/query
  string?)

(s/def :blaze.monitoring.dashboard.variable/sort
  #{:alphabetical-asc})

(s/def :blaze.monitoring.dashboard.variable/multi
  boolean?)

(s/def :blaze.monitoring.dashboard.variable/hide
  boolean?)

(s/def :blaze.monitoring.dashboard.variable/values
  (s/coll-of string? :kind vector? :min-count 1))

(s/def :blaze.monitoring.dashboard.variable/selected
  (s/coll-of string? :kind vector?))

(s/def :blaze.monitoring.dashboard/variable
  (s/keys
   :req-un [:blaze.monitoring.dashboard.variable/type
            :blaze.monitoring.dashboard.variable/name]
   :opt-un [:blaze.monitoring.dashboard.variable/label
            :blaze.monitoring.dashboard.variable/description
            :blaze.monitoring.dashboard.variable/plugin-id
            :blaze.monitoring.dashboard.variable/query
            :blaze.monitoring.dashboard.variable/sort
            :blaze.monitoring.dashboard.variable/multi
            :blaze.monitoring.dashboard.variable/hide
            :blaze.monitoring.dashboard.variable/values
            :blaze.monitoring.dashboard.variable/selected]))

(s/def :blaze.monitoring.dashboard/variables
  (s/coll-of :blaze.monitoring.dashboard/variable :kind vector?))

(s/def :blaze.monitoring.dashboard.query/expr
  string?)

(s/def :blaze.monitoring.dashboard.query/legend
  string?)

(s/def :blaze.monitoring.dashboard.panel/query
  (s/keys :req-un [:blaze.monitoring.dashboard.query/expr
                   :blaze.monitoring.dashboard.query/legend]))

(s/def :blaze.monitoring.dashboard.panel/queries
  (s/coll-of :blaze.monitoring.dashboard.panel/query :kind vector?
             :min-count 1 :max-count 26))

(s/def :blaze.monitoring.dashboard.panel/title
  string?)

(s/def :blaze.monitoring.dashboard.panel/description
  string?)

(s/def :blaze.monitoring.dashboard.panel/unit
  string?)

(s/def :blaze.monitoring.dashboard.panel/width
  (s/int-in 1 25))

(s/def :blaze.monitoring.dashboard.panel/height
  pos-int?)

(s/def :blaze.monitoring.dashboard.panel/min
  (s/nilable number?))

(s/def :blaze.monitoring.dashboard.panel/max
  number?)

(s/def :blaze.monitoring.dashboard.panel/stacking
  boolean?)

(s/def :blaze.monitoring.dashboard.panel/span-nulls
  boolean?)

(s/def :blaze.monitoring.dashboard.panel/fill-opacity
  (s/int-in 0 101))

(s/def :blaze.monitoring.dashboard.panel/show-points
  #{"auto" "always" "never"})

(s/def :blaze.monitoring.dashboard.panel/legend
  boolean?)

(s/def :blaze.monitoring.dashboard.panel/legend-calcs
  (s/coll-of string? :kind vector?))

(s/def :blaze.monitoring.dashboard.panel/legend-placement
  #{"bottom" "right"})

(s/def :blaze.monitoring.dashboard.panel/tooltip-mode
  #{"single" "multi" "none"})

(s/def :blaze.monitoring.dashboard.panel/repeat
  string?)

(s/def :blaze.monitoring.dashboard.panel/max-per-row
  pos-int?)

(s/def :blaze.monitoring.dashboard.panel/overrides
  (s/coll-of map? :kind vector?))

(s/def :blaze.monitoring.dashboard.row/panel
  (s/keys
   :req-un [:blaze.monitoring.dashboard.panel/title
            :blaze.monitoring.dashboard.panel/unit
            :blaze.monitoring.dashboard.panel/queries]
   :opt-un [:blaze.monitoring.dashboard.panel/description
            :blaze.monitoring.dashboard.panel/width
            :blaze.monitoring.dashboard.panel/height
            :blaze.monitoring.dashboard.panel/min
            :blaze.monitoring.dashboard.panel/max
            :blaze.monitoring.dashboard.panel/stacking
            :blaze.monitoring.dashboard.panel/span-nulls
            :blaze.monitoring.dashboard.panel/fill-opacity
            :blaze.monitoring.dashboard.panel/show-points
            :blaze.monitoring.dashboard.panel/legend
            :blaze.monitoring.dashboard.panel/legend-calcs
            :blaze.monitoring.dashboard.panel/legend-placement
            :blaze.monitoring.dashboard.panel/tooltip-mode
            :blaze.monitoring.dashboard.panel/repeat
            :blaze.monitoring.dashboard.panel/max-per-row
            :blaze.monitoring.dashboard.panel/overrides]))

(s/def :blaze.monitoring.dashboard.row/panels
  (s/coll-of :blaze.monitoring.dashboard.row/panel :kind vector? :min-count 1))

(s/def :blaze.monitoring.dashboard.row/title
  string?)

(s/def :blaze.monitoring.dashboard.row/repeat
  string?)

(s/def :blaze.monitoring.dashboard/row
  (s/keys :req-un [:blaze.monitoring.dashboard.row/title
                   :blaze.monitoring.dashboard.row/panels]
          :opt-un [:blaze.monitoring.dashboard.row/repeat]))

(s/def :blaze.monitoring.dashboard/rows
  (s/coll-of :blaze.monitoring.dashboard/row :kind vector?))

(s/def :blaze.monitoring/dashboard
  (s/keys :req-un [:blaze.monitoring.dashboard/title
                   :blaze.monitoring.dashboard/variables
                   :blaze.monitoring.dashboard/rows]))
