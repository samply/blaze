<script lang="ts">
	import type { Period } from '../fhir';
	import DateTime from './date-time.svelte';
	import UseBadge from './util/gray-badge.svelte';

	export let value: Period;
</script>

{#if value.start && value.end}
	<p>
		<DateTime value={value.start} />
	</p>

	<p class="pt-2">
		<DateTime value={value.end} />
	</p>
{:else if value.start}
	<p>
		<DateTime value={value.start} />
		<UseBadge value="start" />
	</p>
{:else if value.end}
	<p>
		<DateTime value={value.end} />
		<UseBadge value="end" />
	</p>
{/if}
