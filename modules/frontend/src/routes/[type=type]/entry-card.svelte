<script lang="ts">
	import type { SearchSetBundleEntry } from '../../fhir.js';
	import type { FhirObject } from '../../resource/resource-card.js';
	import ResourceCard from '../../resource/resource-card.svelte';
	import SearchModeBadge from './search-mode-badge.svelte';

	export let entry: SearchSetBundleEntry<FhirObject>;
</script>

<ResourceCard resource={entry.resource} embedded={true}>
	<SearchModeBadge searchMode={entry.search.mode} slot="header" />
</ResourceCard>
