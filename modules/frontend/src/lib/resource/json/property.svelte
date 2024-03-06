<script lang="ts">
	import type { FhirProperty } from '../resource-card.js';
	import ArrayCode from './array.svelte';
	import Value from './value.svelte';

	export let indent: number;
	export let isLast: boolean;
	export let property: FhirProperty;
</script>

{' '.repeat(indent)}<span class="text-orange-700">"{property.name}"</span
>{': '}{#if Array.isArray(property.value)}<ArrayCode
		{indent}
		values={property.value}
	/>{:else}<Value {indent} value={property.value} />{/if}{isLast ? '\n' : ',\n'}
