<script lang="ts">
	import type { FhirObjectBundleEntry } from '$lib/resource/resource-card.js';
	import ResourceCard from '$lib/resource/resource-card.svelte';
	import DeletedCard from './deleted-card.svelte';
	import MethodBadge from './method-badge.svelte';

	export let entry: FhirObjectBundleEntry;
</script>

{#if entry.fhirObject}
	<ResourceCard resource={entry.fhirObject} versionLink={true} embedded={true}>
		{#if entry.request}
			<MethodBadge method={entry.request.method} slot="header" />
		{/if}
	</ResourceCard>
{:else if entry.request?.method === 'DELETE' && entry.response?.lastModified}
	<DeletedCard request={entry.request} lastModified={entry.response.lastModified} />
{/if}
