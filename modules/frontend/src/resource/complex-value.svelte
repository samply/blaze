<script lang="ts">
	import type { FhirObject } from './resource-card';

	import CodeableConcept from '../values/codeable-concept.svelte';
	import Coding from '../values/coding.svelte';
	import Meta from '../values/meta.svelte';
	import Money from '../values/money.svelte';
	import Period from '../values/period.svelte';
	import Quantity from '../values/quantity.svelte';

	export let value: FhirObject;
</script>

{#if value.type.code == 'CodeableConcept'}
	<CodeableConcept value={value.object} />
{:else if value.type.code == 'Coding'}
	<Coding value={value.object} />
{:else if value.type.code == 'Meta'}
	<Meta value={value.object} />
{:else if value.type.code == 'Money'}
	<Money value={value.object} />
{:else if value.type.code == 'Period'}
	<Period value={value.object} />
{:else if value.type.code == 'Quantity'}
	<Quantity value={value.object} />
{:else}
	({value.type.code}) {value.object}
{/if}
