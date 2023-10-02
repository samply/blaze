<script lang="ts">
	import {
		type FhirProperty,
		isPrimitive,
		type FhirPrimitive,
		type FhirObject
	} from './resource-card.js';
	import PrimitiveValue from './primitive-value.svelte';
	import ComplexValue from './complex-value.svelte';
	import Attachment from '../values/attachment.svelte';
	import Identifier from '../values/identifier.svelte';
	import HumanName from '../values/human-name.svelte';
	import Address from '../values/address.svelte';
	import ContactPoint from '../values/contact-point.svelte';
	import Reference from '../values/reference.svelte';
	import Dosage from '../values/dosage.svelte';

	export let property: FhirProperty;

	function toArray<T>(x: T[] | T): T[] {
		return Array.isArray(x) ? x : [x];
	}

	const name = property.name.substring(0, 1).toUpperCase() + property.name.substring(1);

	const singlePrimitiveValue =
		isPrimitive(property.type) && !Array.isArray(property.value)
			? (property.value as FhirPrimitive)
			: undefined;

	const multiplePrimitiveValues =
		isPrimitive(property.type) && Array.isArray(property.value)
			? (property.value as FhirPrimitive[])
			: undefined;

	const multipleComplexValues = !isPrimitive(property.type)
		? (toArray(property.value) as FhirObject[])
		: undefined;
</script>

<div class="py-4 sm:grid sm:grid-cols-4 sm:gap-4 sm:px-6 sm:py-5">
	<dt class="text-sm font-medium text-gray-500">{name}</dt>
	<dd class="mt-1 text-sm text-gray-900 sm:col-span-3 sm:mt-0">
		{#if singlePrimitiveValue}
			<PrimitiveValue value={singlePrimitiveValue} />
		{:else if multiplePrimitiveValues}
			{#each multiplePrimitiveValues as primitiveValue}
				<PrimitiveValue value={primitiveValue} />
			{/each}
		{:else if multipleComplexValues}
			{#if property.type.code == 'Attachment'}
				<Attachment values={multipleComplexValues} />
			{:else if property.type.code == 'Identifier'}
				<Identifier values={multipleComplexValues} />
			{:else if property.type.code == 'HumanName'}
				<HumanName values={multipleComplexValues} />
			{:else if property.type.code == 'Address'}
				<Address values={multipleComplexValues} />
			{:else if property.type.code == 'ContactPoint'}
				<ContactPoint values={multipleComplexValues} />
			{:else if property.type.code == 'Reference'}
				<Reference values={multipleComplexValues} />
			{:else if property.type.code == 'Dosage'}
				<Dosage values={multipleComplexValues} />
			{:else if property.type.code == 'Element'}
				<p class="text-gray-500">{multipleComplexValues.length > 0 ? '<elements>' : '<element>'}</p>
			{:else if property.type.code == 'BackboneElement'}
				<p class="text-gray-500">
					{multipleComplexValues.length > 0 ? '<backbone-elements>' : '<backbone-element>'}
				</p>
			{:else if property.type.code == 'Extension'}
				<p class="text-gray-500">
					{multipleComplexValues.length > 0 ? '<extensions>' : '<extension>'}
				</p>
			{:else if property.type.code == 'Resource'}
				<p class="text-gray-500">
					{multipleComplexValues.length > 0 ? '<resources>' : '<resource>'}
				</p>
			{:else}
				{#each multipleComplexValues as complexValue}
					<ComplexValue value={complexValue} />
				{/each}
			{/if}
		{/if}
	</dd>
</div>
