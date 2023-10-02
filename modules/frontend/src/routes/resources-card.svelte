<script lang="ts">
	import type { ResourceInfo } from './+layout.js';
	import ResourceRow from './resource-row.svelte';

	export let resources: ResourceInfo[];
</script>

<div class="overflow-hidden rounded-lg bg-white shadow">
	<div class="px-4 py-5 sm:p-6">
		<div class="px-4 sm:px-6 lg:px-8">
			<div class="sm:flex sm:items-center">
				<div class="sm:flex-auto">
					<h1 class="text-base font-semibold leading-6 text-gray-900">Resources</h1>
				</div>
			</div>
			<div class="mt-8 flow-root">
				<div class="-mx-4 -my-2 overflow-x-auto sm:-mx-6 lg:-mx-8">
					<div class="inline-block min-w-full py-2 align-middle sm:px-6 lg:px-8">
						<table class="min-w-full divide-y divide-gray-300">
							<thead>
								<tr>
									<th
										scope="col"
										class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
										>Name</th
									>
									<th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900"
										>Count</th
									>
								</tr>
							</thead>
							<tbody class="divide-y divide-gray-200">
								{#each resources as resource}
									<ResourceRow {...resource} />
								{/each}
							</tbody>
						</table>
					</div>
				</div>
			</div>
		</div>
	</div>
</div>
