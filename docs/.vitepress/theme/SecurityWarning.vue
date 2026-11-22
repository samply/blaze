<template>
  <div class="security-warning custom-block danger">
    <p class="custom-block-title">Security Warning</p>
    <p>
      Blaze versions before v1.14.0 are affected by
      <a href="https://www.cve.org/CVERecord?id=CVE-2026-114938">CVE-2026-114938</a>.
      Please update to
      <a href="https://github.com/samply/blaze/releases/tag/v1.14.0">v1.14.0</a>.
      If you can't update, start every Blaze instance with
      <code>-XX:-UseBASE64Intrinsics</code> added to the
      <code>JAVA_TOOL_OPTIONS</code> environment variable, in addition to all
      other options, like
      <code>JAVA_TOOL_OPTIONS="-Xmx4g -XX:-UseBASE64Intrinsics"</code>.
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
