<script lang="ts">
	import type { Quantity } from 'fhir/r4';

	export let value: Quantity;
</script>

<p>
	{value.value}
	{#if value.unit}{value.unit}{:else}{value.code}{/if}
</p>
