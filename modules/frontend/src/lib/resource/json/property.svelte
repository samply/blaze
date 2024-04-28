<script lang="ts">
	import {
		type FhirPrimitive,
		type FhirProperty,
		isPrimitive,
		wrapPrimitiveExtensions
	} from '../resource-card.js';
	import ArrayValue from './array.svelte';
	import Value from './value.svelte';

	export let indent: number;
	export let isLast: boolean;
	export let property: FhirProperty;

	$: primitiveExtensions =
		!Array.isArray(property.value) && isPrimitive(property.value.type)
			? (property.value as FhirPrimitive).extensions
			: undefined;
</script>

{' '.repeat(indent)}<span class="text-orange-700">"{property.name}"</span
>{': '}{#if Array.isArray(property.value)}<ArrayValue
		{indent}
		values={property.value}
	/>{:else}<Value
		{indent}
		value={property.value}
	/>{/if}{#if primitiveExtensions !== undefined}{',\n'}{' '.repeat(indent)}<span
		class="text-orange-700">"_{property.name}"</span
	>{': '}<Value {indent} value={wrapPrimitiveExtensions(primitiveExtensions)} />{/if}{isLast
	? '\n'
	: ',\n'}
