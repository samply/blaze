import type { Theme } from "vitepress";
import DefaultTheme from "vitepress/theme";
import BarChart from "./chart/BarChart.vue";
import DiskPerfChart from "./chart/DiskPerfChart.vue";
import DiskPerfStats from "./chart/DiskPerfStats.vue";
import LineChart from "./chart/LineChart.vue";
import "./custom.css";

// The chart components are registered globally, so a page can place a chart
// without importing the component. A page imports only its own data loader,
// which is what carries the paths of the data files.
export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component("BarChart", BarChart);
    app.component("DiskPerfChart", DiskPerfChart);
    app.component("DiskPerfStats", DiskPerfStats);
    app.component("LineChart", LineChart);
  },
} satisfies Theme;
