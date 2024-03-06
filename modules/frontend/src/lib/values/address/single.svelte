<script lang="ts">
	import type { Address } from 'fhir/r4';
	import { joinStrings } from '../../util.js';

	export let value: Address;
</script>

{#each value.line ?? [] as line}
	<p>{line}</p>
{/each}
{#if value.postalCode || value.city}
	<p>{joinStrings(' ', value.postalCode, value.city)}</p>
{/if}
{#if value.state || value.country}
	<p>{joinStrings(' ', value.state, value.country)}</p>
{/if}
