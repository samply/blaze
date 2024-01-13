<script lang="ts">
	import SimpleStats from '../../../../../../tailwind/stats/simple.svelte';
	import LevelsCard from './level-table.svelte';
	import { toTitleCase } from '../../../../../../util.js';
	import { descriptions } from './util.js';
	import { base } from '$app/paths';
	import { page } from '$app/stores';
	import type { Data } from './+page.js';
	import { pascalCase } from 'change-case';
	import prettyBytes from 'pretty-bytes';

	export let data: Data;
</script>

<svelte:head>
	<title
		>${pascalCase($page.params.cfId)} Column Family - {toTitleCase($page.params.dbId)} Database - Admin
		- Blaze</title
	>
</svelte:head>

<main class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
	<div class="mt-4 pb-5">
		<h3 class="text-base font-semibold leading-7 text-gray-900">
			<a href="{base}/__admin/dbs/{$page.params.dbId}">{toTitleCase($page.params.dbId)}</a> -
			{pascalCase($page.params.cfId)}
		</h3>
		<p class="mt-1 max-w-4xl text-sm leading-6 text-gray-500">
			{descriptions[$page.params.dbId][$page.params.cfId]}
		</p>
	</div>

	<dl class="mx-auto grid grid-cols-1 gap-px bg-gray-900/5 sm:grid-cols-2 border-y border-gray-200">
		<SimpleStats title="File System Usage">
			{prettyBytes(data.fileSize, { binary: true, maximumFractionDigits: 1 })}
		</SimpleStats>
		<SimpleStats title="# Files">
			{data.numFiles}
		</SimpleStats>
	</dl>

	<LevelsCard levels={data.levels} />
</main>
