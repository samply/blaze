<script lang="ts">
	import type { HistoryBundleEntry } from '../fhir';
	import type { FhirObject } from '../resource/resource-card';
	import { HttpVerb } from '../fhir';
	import ResourceCard from '../resource/resource-card.svelte';
	import DeletedCard from './deleted-card.svelte';
	import MethodBadge from './method-badge.svelte';

	export let entry: HistoryBundleEntry<FhirObject>;
</script>

{#if entry.resource}
	<ResourceCard resource={entry.resource} versionLink={true} embedded={true}>
		<MethodBadge method={entry.request.method} slot="header" />
	</ResourceCard>
{:else if entry.request.method == HttpVerb.DELETE && entry.response?.lastModified}
	<DeletedCard request={entry.request} lastModified={entry.response.lastModified} />
{/if}
