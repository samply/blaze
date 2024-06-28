<script lang="ts">
	import { isPrimitive } from '../resource-card.js';
	import PrimitiveValue from './primitive-value.svelte';
	import Object from './object.svelte';

	export let indent: number;
	export let insideArray = false;
	// eslint-disable-next-line
	export let value: any; //FhirObject | FhirPrimitive
</script>

<!-- eslint-disable -->
{#if isPrimitive(value.type)}{insideArray ? ' '.repeat(indent) : ''}<PrimitiveValue
		{value}
	/>{:else}<Object {indent} {insideArray} object={value} />{/if}
