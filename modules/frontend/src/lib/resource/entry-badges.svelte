<script lang="ts">
  import type { FhirObjectBundleEntry } from '$lib/resource/resource-card';
  import MethodBadge from '../history/method-badge.svelte';
  import SearchModeBadge from '../history/search-mode-badge.svelte';
  import SubsettedBadge from '../history/subsetted-badge.svelte';

  interface Props {
    entry: FhirObjectBundleEntry;
  }

  let { entry }: Props = $props();
</script>

<SubsettedBadge {entry} />

{#if entry.request}
  <MethodBadge method={entry.request.method} />
{:else if entry.search?.mode}
  <SearchModeBadge mode={entry.search.mode} />
{:else}
  <span></span>
{/if}
