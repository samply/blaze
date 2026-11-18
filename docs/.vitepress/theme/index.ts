import { h } from "vue";
import type { Theme } from "vitepress";
import DefaultTheme from "vitepress/theme";
import BarChart from "./chart/BarChart.vue";
import DiskPerfChart from "./chart/DiskPerfChart.vue";
import DiskPerfStats from "./chart/DiskPerfStats.vue";
import LineChart from "./chart/LineChart.vue";
import VersionedLineChart from "./chart/VersionedLineChart.vue";
import SecurityWarning from "./SecurityWarning.vue";
import "./custom.css";

export default {
  extends: DefaultTheme,
  // The security warning sits above the hero, so it is the first thing seen on
  // the home page.
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      "home-hero-before": () => h(SecurityWarning),
    }),
  // The chart components are registered globally so that a chart's `src` is
  // depth-independent: `cql/simple-code-search-100k.txt` is written the same way
  // in `performance.md` and in `performance/cql.md`.
  enhanceApp({ app }) {
    app.component("BarChart", BarChart);
    app.component("DiskPerfChart", DiskPerfChart);
    app.component("DiskPerfStats", DiskPerfStats);
    app.component("LineChart", LineChart);
    app.component("VersionedLineChart", VersionedLineChart);
  },
} satisfies Theme;
