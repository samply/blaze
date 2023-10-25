<script lang="ts">
	import type { FhirObject, FhirPrimitive } from '../resource-card.js';
	import Value from './value.svelte';

	export let indent: number;
	export let values: (FhirObject | FhirPrimitive)[];
</script>

<span>{'[\n'}</span>{#each values as value}<Value
		indent={indent + 4}
		insideArray={true}
		{value}
	/>{/each}<span>{' '.repeat(indent)}{']'}</span>
