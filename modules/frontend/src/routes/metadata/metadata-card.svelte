<script lang="ts">
	import {
		type CapabilityStatement,
		RestfulInteraction,
		ConditionalDeleteStatus
	} from '../../fhir.js';
	import type { FhirObject } from '../../resource/resource-card.js';
	import { isTabActive } from '../../util.js';
	import { base } from '$app/paths';
	import { page } from '$app/stores';
	import { fade } from 'svelte/transition';
	import { quintIn } from 'svelte/easing';

	import DateTime from '../../values/date-time.svelte';
	import Download from './download.svelte';
	import Check from './check.svelte';
	import XMark from './x-mark.svelte';
	import Object from '../../resource/json/object.svelte';
	import TabItem from '../../tab-item.svelte';
	import InteractionTh from './interaction-th.svelte';
	import InteractionTd from './interaction-td.svelte';

	export let resource: FhirObject;

	$: capabilityStatement = resource.object as CapabilityStatement;

	const fadeParams = { duration: 300, easing: quintIn };
</script>

<div in:fade={fadeParams} class="overflow-hidden bg-white shadow sm:rounded-lg">
	<div class="border-b border-gray-200 px-8">
		<nav class="-mb-px flex space-x-8" aria-label="Tabs">
			<TabItem name="default" label="Interactions" />
			<TabItem name="json" label="Json" />
		</nav>
	</div>
	{#if isTabActive($page.url, 'default')}
		<div in:fade={fadeParams} class="px-4 py-5 sm:px-6">
			<div class="flex">
				<h3 class="flex-grow text-base font-semibold leading-6 text-gray-900">
					{capabilityStatement.software?.name} v{capabilityStatement.software?.version}
				</h3>
			</div>
			<p class="mt-1 max-w-2xl text-sm text-gray-500">
				Last Updated&nbsp;<DateTime value={capabilityStatement.date} />
			</p>
		</div>
		<div in:fade={fadeParams} class="border-t border-gray-200 px-4 py-5 sm:px-6">
			<table class="w-full divide-y divide-gray-300">
				<thead>
					<tr>
						<th
							scope="col"
							class="whitespace-nowrap py-3.5 pl-4 align-bottom text-left text-sm font-semibold text-gray-900 sm:pl-0"
							>Resource Type</th
						>
						<InteractionTh label="Profile" />
						<InteractionTh label="Read" />
						<InteractionTh label="VRead" />
						<InteractionTh label="Search-Type" />
						<InteractionTh label="History" />
						<InteractionTh label="History-Type" />
						<InteractionTh label="Create" />
						<InteractionTh label="Update" />
						<InteractionTh label="Patch" />
						<InteractionTh label="Delete" />
						<InteractionTh label="Read History" />
						<InteractionTh label="Update Create" />
						<InteractionTh label="Conditional Create" />
						<InteractionTh label="Conditional Read" />
						<InteractionTh label="Conditional Update" />
						<InteractionTh label="Conditional Patch" />
						<InteractionTh label="Conditional Delete" />
					</tr>
				</thead>
				<tbody class="divide-y divide-gray-200 bg-white">
					{#each capabilityStatement.rest[0].resource as resource (resource.type)}
						<tr>
							<td class="whitespace-nowrap py-2 pl-4 text-sm sm:pl-0 text-gray-900"
								><a href="{base}/{resource.type}" class="hover:text-gray-500">{resource.type}</a
								></td
							>
							<td class="py-2 text-sm text-gray-900">
								<a
									href="{base}/__metadata/StructureDefinition?url={resource.profile}&_format=json"
									download="{resource.type}.json"><Download /></a
								>
							</td>
							<InteractionTd {resource} interaction={RestfulInteraction.read} />
							<InteractionTd {resource} interaction={RestfulInteraction.vread} />
							<InteractionTd {resource} interaction={RestfulInteraction.searchType} />
							<InteractionTd {resource} interaction={RestfulInteraction.historyInstance} />
							<InteractionTd {resource} interaction={RestfulInteraction.historyType} />
							<InteractionTd {resource} interaction={RestfulInteraction.create} />
							<InteractionTd {resource} interaction={RestfulInteraction.update} />
							<InteractionTd {resource} interaction={RestfulInteraction.patch} />
							<InteractionTd {resource} interaction={RestfulInteraction.delete} />
							<td class="py-2">
								{#if resource.readHistory}
									<Check />
								{:else}
									<XMark />
								{/if}
							</td>
							<td class="py-2">
								{#if resource.updateCreate}
									<Check />
								{:else}
									<XMark />
								{/if}
							</td>
							<td class="py-2">
								{#if resource.conditionalCreate}
									<Check />
								{:else}
									<XMark />
								{/if}
							</td>
							<td class="py-2">
								{#if resource.conditionalRead}
									<Check />
								{:else}
									<XMark />
								{/if}
							</td>
							<td class="py-2">
								{#if resource.conditionalUpdate}
									<Check />
								{:else}
									<XMark />
								{/if}
							</td>
							<td class="py-2">
								{#if resource.conditionalPath}
									<Check />
								{:else}
									<XMark />
								{/if}
							</td>
							<td class="py-2">
								{#if resource.conditionalDelete !== ConditionalDeleteStatus.notSupported}
									<Check />
								{:else}
									<XMark />
								{/if}
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	{:else}
		<pre in:fade={fadeParams} class="flex overflow-auto text-sm"><code class="p-4"
				><Object object={resource} /></code
			></pre>
	{/if}
</div>
