<script lang="ts">
	import BreadcrumbEntryHome from '../../../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryAdmin from '../../breadcrumb-entry-admin.svelte';
	import BreadcrumbEntryDb from './breadcrumb-entry-db.svelte';
	import StatsCard from './stats-card.svelte';
	import ColumnFamilyCard from './column-family-card.svelte';
	import { toTitleCase } from '../../../../util.js';
	import { page } from '$app/stores';
	import type { Data } from './+page.js';
	import prettyBytes from 'pretty-bytes';

	export let data: Data;
</script>

<svelte:head>
	<title>{toTitleCase($page.params.dbId)} - Database - Admin - Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow z-10">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryAdmin />
				<BreadcrumbEntryDb />
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
					{prettyBytes(data.stats.estimateLiveDataSize, { binary: true, maximumFractionDigits: 1 })}
					<svg
						xmlns="http://www.w3.org/2000/svg"
						viewBox="0 0 24 24"
						fill="currentColor"
						class="ml-4 w-8 h-8 inline align-text-top"
					>
						<title>free</title>
						<path
							fill-rule="evenodd"
							d="M2.25 13.5a8.25 8.25 0 018.25-8.25.75.75 0 01.75.75v6.75H18a.75.75 0 01.75.75 8.25 8.25 0 01-16.5 0z"
							clip-rule="evenodd"
						/>
						<path
							fill-rule="evenodd"
							d="M12.75 3a.75.75 0 01.75-.75 8.25 8.25 0 018.25 8.25.75.75 0 01-.75.75h-7.5a.75.75 0 01-.75-.75V3z"
							clip-rule="evenodd"
						/>
					</svg>
					{prettyBytes(data.stats.usableSpace, { binary: true, maximumFractionDigits: 1 })}
				</StatsCard>
				<StatsCard title="Block Cache Usage">
					{(data.stats.blockCache.usage / data.stats.blockCache.capacity).toLocaleString(
						undefined,
						{
							style: 'percent',
							minimumFractionDigits: 0
						}
					)}
					of
					{prettyBytes(data.stats.blockCache.capacity, { binary: true, maximumFractionDigits: 1 })}
				</StatsCard>
				<StatsCard title="Compactions">
					<svg
						xmlns="http://www.w3.org/2000/svg"
						viewBox="0 0 24 24"
						fill="currentColor"
						class="w-8 h-8 inline align-text-top"
					>
						<title>pending</title>
						<path
							fill-rule="evenodd"
							d="M12 2.25c-5.385 0-9.75 4.365-9.75 9.75s4.365 9.75 9.75 9.75 9.75-4.365 9.75-9.75S17.385 2.25 12 2.25zM12.75 6a.75.75 0 00-1.5 0v6c0 .414.336.75.75.75h4.5a.75.75 0 000-1.5h-3.75V6z"
							clip-rule="evenodd"
						/>
					</svg>
					{data.stats.compactions.pending}
					<svg
						xmlns="http://www.w3.org/2000/svg"
						viewBox="0 0 24 24"
						fill="currentColor"
						class="ml-4 w-8 h-8 inline align-text-top"
					>
						<title>running</title>
						<path
							fill-rule="evenodd"
							d="M11.828 2.25c-.916 0-1.699.663-1.85 1.567l-.091.549a.798.798 0 01-.517.608 7.45 7.45 0 00-.478.198.798.798 0 01-.796-.064l-.453-.324a1.875 1.875 0 00-2.416.2l-.243.243a1.875 1.875 0 00-.2 2.416l.324.453a.798.798 0 01.064.796 7.448 7.448 0 00-.198.478.798.798 0 01-.608.517l-.55.092a1.875 1.875 0 00-1.566 1.849v.344c0 .916.663 1.699 1.567 1.85l.549.091c.281.047.508.25.608.517.06.162.127.321.198.478a.798.798 0 01-.064.796l-.324.453a1.875 1.875 0 00.2 2.416l.243.243c.648.648 1.67.733 2.416.2l.453-.324a.798.798 0 01.796-.064c.157.071.316.137.478.198.267.1.47.327.517.608l.092.55c.15.903.932 1.566 1.849 1.566h.344c.916 0 1.699-.663 1.85-1.567l.091-.549a.798.798 0 01.517-.608 7.52 7.52 0 00.478-.198.798.798 0 01.796.064l.453.324a1.875 1.875 0 002.416-.2l.243-.243c.648-.648.733-1.67.2-2.416l-.324-.453a.798.798 0 01-.064-.796c.071-.157.137-.316.198-.478.1-.267.327-.47.608-.517l.55-.091a1.875 1.875 0 001.566-1.85v-.344c0-.916-.663-1.699-1.567-1.85l-.549-.091a.798.798 0 01-.608-.517 7.507 7.507 0 00-.198-.478.798.798 0 01.064-.796l.324-.453a1.875 1.875 0 00-.2-2.416l-.243-.243a1.875 1.875 0 00-2.416-.2l-.453.324a.798.798 0 01-.796.064 7.462 7.462 0 00-.478-.198.798.798 0 01-.517-.608l-.091-.55a1.875 1.875 0 00-1.85-1.566h-.344zM12 15.75a3.75 3.75 0 100-7.5 3.75 3.75 0 000 7.5z"
							clip-rule="evenodd"
						/>
					</svg>
					{data.stats.compactions.running}
				</StatsCard>
			</dl>
		</div>
		<div class="mt-4">
			<h3 class="text-base font-semibold leading-6 text-gray-900">Column Families</h3>
			<ul
				class="mt-2 divide-y divide-gray-100 overflow-hidden bg-white shadow-sm ring-1 ring-gray-900/5 sm:rounded-xl"
			>
				{#each data.columnFamilies as columnFamily}
					<ColumnFamilyCard {...columnFamily} />
				{/each}
			</ul>
		</div>
	</div>
</main>
