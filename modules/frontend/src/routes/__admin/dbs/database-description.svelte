<script lang="ts">
	import List from '../../../tailwind/description/left-aligned/list.svelte';
	import Row from '../../../tailwind/description/left-aligned/row.svelte';
	import type { Stats } from './[dbId=id]/+page.js';
	import { toTitleCase } from '../../../util.js';
	import { descriptions } from './util.js';
	import { base } from '$app/paths';
	import prettyBytes from 'pretty-bytes';

	export let stats: Stats;
</script>

<List clazz="mb-8">
	<h3 slot="title">
		<a href="{base}/__admin/dbs/{stats.name}">{toTitleCase(stats.name)}</a>
	</h3>
	<p slot="description">{descriptions[stats.name]}</p>

	<Row title="File System Usage">
		{prettyBytes(stats.estimateLiveDataSize, { binary: true, maximumFractionDigits: 1 })}
	</Row>
	<Row title="Usable Space">
		{prettyBytes(stats.usableSpace, { binary: true, maximumFractionDigits: 1 })}
	</Row>
	{#if stats.blockCache}
		<Row title="Block Cache Usage">
			{(stats.blockCache.usage / stats.blockCache.capacity).toLocaleString(undefined, {
				style: 'percent',
				minimumFractionDigits: 0
			})}
			of
			{prettyBytes(stats.blockCache.capacity, {
				binary: true,
				maximumFractionDigits: 1
			})}
		</Row>
	{/if}
	<Row title="Compactions">
		<span title="pending">{stats.compactions.pending}</span>
		/
		<span title="running">{stats.compactions.running}</span>
	</Row>
</List>
