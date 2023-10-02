<script lang="ts">
	import BreadcrumbEntryHome from '../../../../../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryAdmin from '../../../../breadcrumb-entry-admin.svelte';
	import BreadcrumbEntryDb from '../../breadcrumb-entry-db.svelte';
	import BreadcrumbEntryCf from './breadcrumb-entry-cf.svelte';
	import StatsCard from '../../stats-card.svelte';
	import LevelsCard from './levels-card.svelte';
	import { toTitleCase } from '../../../../../../util.js';
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

<header class="fixed w-full bg-white shadow z-10">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryAdmin />
				<BreadcrumbEntryDb />
				<BreadcrumbEntryCf />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
		<div>
			<h3 class="text-base font-semibold leading-6 text-gray-900">Stats</h3>
			<dl class="mt-2 grid grid-cols-1 gap-5 sm:grid-cols-3">
				<StatsCard title="File System Usage">
					{prettyBytes(data.size, { binary: true, maximumFractionDigits: 1 })}
					<svg
						xmlns="http://www.w3.org/2000/svg"
						viewBox="0 0 24 24"
						fill="currentColor"
						class="ml-4 w-8 h-8 inline align-text-top"
					>
						<title>files</title>
						<path
							d="M19.5 21a3 3 0 003-3v-4.5a3 3 0 00-3-3h-15a3 3 0 00-3 3V18a3 3 0 003 3h15zM1.5 10.146V6a3 3 0 013-3h5.379a2.25 2.25 0 011.59.659l2.122 2.121c.14.141.331.22.53.22H19.5a3 3 0 013 3v1.146A4.483 4.483 0 0019.5 9h-15a4.483 4.483 0 00-3 1.146z"
						/>
					</svg>
					{data.numFiles}
				</StatsCard>
			</dl>
		</div>
		<LevelsCard levels={data.levels} />
	</div>
</main>
