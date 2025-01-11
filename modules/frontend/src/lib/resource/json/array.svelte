<script lang="ts">
	import type { FhirObject, FhirPrimitive } from '../resource-card.js';
	import Value from './value.svelte';

	interface Props {
		indent: number;
		values: (FhirObject | FhirPrimitive)[];
	}

	let { indent, values }: Props = $props();
</script>

<span>{'[\n'}</span>{#each values as value, index}<Value
		indent={indent + 4}
		insideArray={true}
		{value}
	/>{index < values.length - 1 ? ',\n' : '\n'}{/each}<span>{' '.repeat(indent)}{']'}</span>
