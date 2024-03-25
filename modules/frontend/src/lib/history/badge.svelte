<script lang="ts">
	import type { FhirObjectBundleEntry } from '$lib/resource/resource-card';

	import MethodBadge from './method-badge.svelte';
	import SearchModeBadge from './search-mode-badge.svelte';

	export let entry: FhirObjectBundleEntry;
</script>

{#if entry.request}
	<MethodBadge method={entry.request.method} />
{:else if entry.search?.mode}
	<SearchModeBadge mode={entry.search.mode} />
{:else}
	<span />
{/if}
