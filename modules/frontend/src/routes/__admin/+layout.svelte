<script lang="ts">
  import NavItem from './nav-item.svelte';
  import { page } from '$app/state';

  let { data, children } = $props();
</script>

<div class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <div class="border-b border-gray-200 dark:border-gray-600">
    <nav class="-mb-px flex space-x-8" aria-label="Tabs">
      <NavItem active={page.route.id === '/__admin'} id="/__admin" label="Overview" />
      <NavItem
        active={page.route.id?.startsWith('/__admin/dbs')}
        id="/__admin/dbs"
        label="Databases"
      />
      <NavItem
        active={page.route.id?.startsWith('/__admin/jobs')}
        id="/__admin/jobs"
        label="Jobs"
      />
      {#if data.features.find((f) => f.key === 'cql-expression-cache')?.enabled}
        <NavItem active={page.route.id?.startsWith('/__admin/cql')} id="/__admin/cql" label="CQL" />
      {/if}
    </nav>
  </div>
</div>

{@render children?.()}
