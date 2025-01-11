<script lang="ts">
	import { isPrimitive } from '../resource-card.js';
	import PrimitiveValue from './primitive-value.svelte';
	import Object from './object.svelte';

	interface Props {
		indent: number;
		insideArray?: boolean;
		// eslint-disable-next-line
		value: any; //FhirObject | FhirPrimitive
	}

	let { indent, insideArray = false, value }: Props = $props();
</script>

<!-- eslint-disable -->
{#if isPrimitive(value.type)}{insideArray ? ' '.repeat(indent) : ''}<PrimitiveValue
		{value}
	/>{:else}<Object {indent} {insideArray} object={value} />{/if}
