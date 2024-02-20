<script lang="ts">
	import type { CodeableConcept } from 'fhir/r4';
	import Coding from './coding.svelte';

	export let value: CodeableConcept;
</script>

{#if value.coding}
	{#each value.coding as coding}
		<Coding value={coding} />
	{/each}
{:else if value.text !== undefined}
	<p>{value.text}</p>
{/if}
