<script lang="ts">
	import type { FhirObject } from './resource-card';

	import { base } from '$app/paths';
	import { page } from '$app/stores';
	import { fade } from 'svelte/transition';
	import { quintIn } from 'svelte/easing';

	import { isTabActive } from './../util';
	import { willBeRendered as willMetaBeRendered } from '../values/meta';

	import TabItem from '../tab-item.svelte';
	import TabItemEmbedded from '../tab-item-embedded.svelte';
	import DateTime from '../values/date-time.svelte';
	import Property from './property.svelte';
	import Object from './json/object.svelte';

	export let resource: FhirObject;
	export let embedded = false;
	export let versionLink = false;

	function href(resource: FhirObject) {
		const href = `${base}/${resource.type.code}/${resource.object.id}`;
		return versionLink ? href + `/_history/${resource.object.meta.versionId}` : href;
	}

	$: properties = resource.properties.filter(
		(p) =>
			p.name != 'resourceType' &&
			p.name != 'id' &&
			(p.type.code != 'Meta' || willMetaBeRendered((p.value as FhirObject).object)) &&
			p.type.code != 'Narrative'
	);

	let selectedTab = 'default';

	const fadeParams = { duration: 300, easing: quintIn };
</script>

<div in:fade|global={fadeParams} class="overflow-hidden bg-white shadow sm:rounded-lg">
	<div class="border-b border-gray-200 px-8">
		<nav class="-mb-px flex space-x-8" aria-label="Tabs">
			{#if embedded}
				<TabItemEmbedded name="default" label="Normal" bind:selected={selectedTab} />
				<TabItemEmbedded name="json" label="Json" bind:selected={selectedTab} />
			{:else}
				<TabItem name="default" label="Normal" />
				<TabItem name="json" label="Json" />
			{/if}
		</nav>
	</div>
	{#if embedded ? selectedTab == 'default' : isTabActive($page.url, 'default')}
		<div in:fade|global={fadeParams} class="px-4 py-5 sm:px-6">
			<div class="flex">
				<h3 class="flex-grow text-base font-semibold leading-6 text-gray-900">
					<a href={href(resource)}>{resource.type.code}/{resource.object.id}</a>
				</h3>
				<slot name="header" />
			</div>
			<p class="mt-1 max-w-2xl text-sm text-gray-500">
				Last Updated&nbsp;<DateTime value={resource.object.meta.lastUpdated} />
			</p>
		</div>
		<div in:fade|global={fadeParams} class="border-t border-gray-200 px-4 py-5 sm:p-0">
			<dl class="sm:divide-y sm:divide-gray-200">
				{#each properties as property (property.name)}
					<Property {property} />
				{/each}
			</dl>
		</div>
	{:else}
		<pre in:fade|global={fadeParams} class="flex overflow-auto text-sm"><code class="p-4"
				><Object object={resource} /></code
			></pre>
	{/if}
</div>
