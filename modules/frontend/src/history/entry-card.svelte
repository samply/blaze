<script lang="ts">
	import type { FhirObjectBundleEntry } from '../resource/resource-card.js';
	import ResourceCard from '../resource/resource-card.svelte';
	import DeletedCard from './deleted-card.svelte';
	import MethodBadge from './method-badge.svelte';

	export let entry: FhirObjectBundleEntry;
</script>

{#if entry.fhirObject}
	<ResourceCard resource={entry.fhirObject} versionLink={true} embedded={true}>
		<MethodBadge method={entry.request?.method || 'unknown'} slot="header" />
	</ResourceCard>
{:else if entry.request?.method === 'DELETE' && entry.response?.lastModified}
	<DeletedCard request={entry.request} lastModified={entry.response.lastModified} />
{/if}
