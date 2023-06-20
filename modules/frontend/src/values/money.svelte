<script lang="ts">
	import type { Money } from '../fhir';

	export let value: Money;
</script>

{#if value.value}
	{value.value}
	{#if value.currency}{value.currency}{/if}
{:else}
	unknown
{/if}
