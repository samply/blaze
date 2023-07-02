<script lang="ts">
	import type { CapabilityStatementRestResource, RestfulInteraction } from '../../fhir';
	import Check from './check.svelte';
	import XMark from './x-mark.svelte';

	export let resource: CapabilityStatementRestResource;
	export let interaction: RestfulInteraction;

	function hasInteraction(
		resource: CapabilityStatementRestResource,
		code: RestfulInteraction
	): boolean {
		return resource.interaction?.find((i) => i.code == code) !== undefined;
	}
</script>

<td class="py-2">
	{#if hasInteraction(resource, interaction)}
		<Check />
	{:else}
		<XMark />
	{/if}
</td>
