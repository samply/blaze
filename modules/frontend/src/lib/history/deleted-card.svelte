<script lang="ts">
  import type { BundleEntryRequest } from 'fhir/r5';
  import DateTime from '$lib/values/date-time.svelte';
  import MethodBadge from './method-badge.svelte';

  interface Props {
    request: BundleEntryRequest;
    lastModified: string;
  }

  let { request, lastModified }: Props = $props();
</script>

<div class="overflow-hidden">
  <div class="px-4 py-5 sm:px-6">
    <div class="flex">
      <h3 class="grow text-base font-semibold leading-6 text-gray-900 dark:text-gray-100">
        {request.url}
      </h3>
      <MethodBadge method="DELETE" />
    </div>
    <p class="mt-1 max-w-2xl text-sm text-gray-500 dark:text-gray-400">
      Last Updated&nbsp;<DateTime value={lastModified} />
    </p>
  </div>
</div>
