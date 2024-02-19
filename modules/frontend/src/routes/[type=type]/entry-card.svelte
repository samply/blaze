<script lang="ts">
	import type { FhirObjectBundleEntry } from '../../resource/resource-card.js';
	import ResourceCard from '../../resource/resource-card.svelte';
	import SearchModeBadge from './search-mode-badge.svelte';

	export let entry: FhirObjectBundleEntry;
</script>

{#if entry.fhirObject}
	<ResourceCard resource={entry.fhirObject} embedded={true}>
		<SearchModeBadge searchMode={entry.search?.mode || 'unknown'} slot="header" />
	</ResourceCard>
{/if}
