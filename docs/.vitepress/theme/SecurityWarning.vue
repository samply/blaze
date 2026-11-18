<script setup lang="ts">
import { withBase } from "vitepress";
</script>

<template>
  <div class="security-warning custom-block danger">
    <p class="custom-block-title">Security Warning</p>
    <p>
      To mitigate
      <a href="https://www.cve.org/CVERecord?id=CVE-2026-114938">CVE-2026-114938</a>,
      every Blaze instance has to be started with
      <code>-XX:-UseBASE64Intrinsics</code> added to the
      <code>JAVA_TOOL_OPTIONS</code> environment variable, in addition to all
      other options, like
      <code>JAVA_TOOL_OPTIONS="-Xmx4g -XX:-UseBASE64Intrinsics"</code>. More
      can be found in the
      <a :href="withBase('/deployment/environment-variables#java-tool-options')">
        Environment Variables</a>
      documentation.
    </p>
  </div>
</template>

<style scoped>
.security-warning {
  max-width: 1152px;
  margin: 32px auto 0;
}

@media (max-width: 1199px) {
  .security-warning {
    margin-left: 24px;
    margin-right: 24px;
  }
}

@media (min-width: 960px) {
  .security-warning {
    margin-left: 64px;
    margin-right: 64px;
  }
}

@media (min-width: 1280px) {
  .security-warning {
    margin-left: auto;
    margin-right: auto;
  }
}
</style>
