<script lang="ts">
	import type { FhirPrimitive } from './resource-card';

	import Canonical from '../values/canonical.svelte';
	import Date from '../values/date.svelte';
	import Time from '../values/time.svelte';
	import DateTime from '../values/date-time.svelte';

	export let value: FhirPrimitive;
</script>

{#if value.type.code == 'boolean'}
	{value.value}
{:else if value.type.code == 'canonical'}
	<Canonical {value} />
{:else if value.type.code == 'code'}
	{value.value}
{:else if value.type.code == 'date' && typeof value.value == 'string'}
	<Date value={value.value} />
{:else if value.type.code == 'dateTime' && typeof value.value == 'string'}
	<DateTime value={value.value} />
{:else if value.type.code == 'decimal'}
	{value.value}
{:else if value.type.code == 'id'}
	{value.value}
{:else if value.type.code == 'instant' && typeof value.value == 'string'}
	<DateTime value={value.value} />
{:else if value.type.code == 'integer'}
	{value.value}
{:else if value.type.code == 'integer64'}
	{value.value}
{:else if value.type.code == 'markdown'}
	{value.value}
{:else if value.type.code == 'oid'}
	{value.value}
{:else if value.type.code == 'string'}
	{value.value}
{:else if value.type.code == 'positiveInt'}
	{value.value}
{:else if value.type.code == 'time' && typeof value.value == 'string'}
	<Time value={value.value} />
{:else if value.type.code == 'unsignedInt'}
	{value.value}
{:else if value.type.code == 'uri'}
	{value.value}
{:else if value.type.code == 'url'}
	{value.value}
{:else if value.type.code == 'uuid'}
	{value.value}
{:else}
	({value.type.code}) {value}
{/if}
