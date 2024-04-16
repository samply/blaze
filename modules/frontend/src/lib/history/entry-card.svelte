<script lang="ts">
	import type { FhirObjectBundleEntry } from '$lib/resource/resource-card.js';
	import ResourceCard from '$lib/resource/resource-card.svelte';
	import DeletedCard from './deleted-card.svelte';
	import Badge from './badge.svelte';

	export let entry: FhirObjectBundleEntry;
</script>

{#if entry.fhirObject}
	<ResourceCard resource={entry.fhirObject} versionLink={true} embedded={true}>
		<Badge {entry} slot="header" />
	</ResourceCard>
{:else if entry.request?.method === 'DELETE' && entry.response?.lastModified}
	<DeletedCard request={entry.request} lastModified={entry.response.lastModified} />
{/if}
