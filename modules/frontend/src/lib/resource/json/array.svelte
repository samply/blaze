<script lang="ts">
	import type { FhirObject, FhirPrimitive } from '../resource-card.js';
	import Value from './value.svelte';

	export let indent: number;
	export let values: (FhirObject | FhirPrimitive)[];
</script>

<span>{'[\n'}</span>{#each values as value, index}<Value
		indent={indent + 4}
		insideArray={true}
		{value}
	/>{index < values.length - 1 ? ',\n' : '\n'}{/each}<span>{' '.repeat(indent)}{']'}</span>
