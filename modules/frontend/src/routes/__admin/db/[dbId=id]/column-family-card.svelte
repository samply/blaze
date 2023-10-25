<script lang="ts">
	import { base } from '$app/paths';
	import { page } from '$app/stores';
	import { pascalCase } from 'change-case';
	import prettyBytes from 'pretty-bytes';

	export let name: string;
	export let estimateNumKeys: number;
	export let estimateLiveDataSize: number;
	export let liveSstFilesSize: number;
	export let sizeAllMemTables: number;
</script>

<li class="relative flex justify-between gap-x-6 px-4 py-5 hover:bg-gray-50 sm:px-6">
	<div class="flex min-w-0 gap-x-4">
		<div class="min-w-0 flex-auto">
			<p class="text-sm font-semibold leading-6 text-gray-900">
				<a href="{base}/__admin/db/{$page.params.dbId}/column-families/{name}">
					<span class="absolute inset-x-0 -top-px bottom-0"></span>
					{pascalCase(name)}
				</a>
			</p>
			<p class="mt-1 flex text-xs leading-5 text-gray-500">
				Live data size:
				{prettyBytes(estimateLiveDataSize, { binary: true, maximumFractionDigits: 1 })}
				Memtable size:
				{prettyBytes(sizeAllMemTables, { binary: true, maximumFractionDigits: 1 })}
			</p>
		</div>
	</div>
	<div class="flex shrink-0 items-center gap-x-4">
		<div class="hidden sm:flex sm:flex-col sm:items-end">
			<p class="text-sm leading-6 text-gray-900">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					viewBox="0 0 20 20"
					fill="currentColor"
					class="w-5 h-5 inline"
				>
					<path
						fill-rule="evenodd"
						d="M8 7a5 5 0 113.61 4.804l-1.903 1.903A1 1 0 019 14H8v1a1 1 0 01-1 1H6v1a1 1 0 01-1 1H3a1 1 0 01-1-1v-2a1 1 0 01.293-.707L8.196 8.39A5.002 5.002 0 018 7zm5-3a.75.75 0 000 1.5A1.5 1.5 0 0114.5 7 .75.75 0 0016 7a3 3 0 00-3-3z"
						clip-rule="evenodd"
					/>
				</svg>
				{estimateNumKeys}
				<svg
					xmlns="http://www.w3.org/2000/svg"
					viewBox="0 0 20 20"
					fill="currentColor"
					class="w-5 h-5 inline"
				>
					<path
						d="M3.75 3A1.75 1.75 0 002 4.75v3.26a3.235 3.235 0 011.75-.51h12.5c.644 0 1.245.188 1.75.51V6.75A1.75 1.75 0 0016.25 5h-4.836a.25.25 0 01-.177-.073L9.823 3.513A1.75 1.75 0 008.586 3H3.75zM3.75 9A1.75 1.75 0 002 10.75v4.5c0 .966.784 1.75 1.75 1.75h12.5A1.75 1.75 0 0018 15.25v-4.5A1.75 1.75 0 0016.25 9H3.75z"
					/>
				</svg>
				{prettyBytes(liveSstFilesSize, { binary: true, maximumFractionDigits: 1 })}
			</p>
			<p class="mt-1 text-xs leading-5 text-gray-500"></p>
		</div>
		<svg
			class="h-5 w-5 flex-none text-gray-400"
			viewBox="0 0 20 20"
			fill="currentColor"
			aria-hidden="true"
		>
			<path
				fill-rule="evenodd"
				d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z"
				clip-rule="evenodd"
			/>
		</svg>
	</div>
</li>
