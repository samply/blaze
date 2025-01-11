<script lang="ts">
	import type { FhirObjectBundleEntry } from '$lib/resource/resource-card';

	import MethodBadge from './method-badge.svelte';
	import SearchModeBadge from './search-mode-badge.svelte';

	interface Props {
		entry: FhirObjectBundleEntry;
	}

	let { entry }: Props = $props();
</script>

{#if entry.request}
	<MethodBadge method={entry.request.method} />
{:else if entry.search?.mode}
	<SearchModeBadge mode={entry.search.mode} />
{:else}
	<span></span>
{/if}
