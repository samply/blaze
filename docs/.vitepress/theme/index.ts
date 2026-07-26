import type { Theme } from "vitepress";
import DefaultTheme from "vitepress/theme";
import BarChart from "./chart/BarChart.vue";
import LineChart from "./chart/LineChart.vue";
import "./custom.css";

// The chart components are registered globally so that a chart's `src` is
// depth-independent: `cql/simple-code-search-100k.txt` is written the same way
// in `performance.md` and in `performance/cql.md`.
export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component("BarChart", BarChart);
    app.component("LineChart", LineChart);
  },
} satisfies Theme;
