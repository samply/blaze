<script lang="ts">
	import NavItem from './nav-item.svelte';
	import { page } from '$app/stores';

	export let data;
</script>

<div class="mx-auto max-w-7xl sm:px-6 lg:px-8">
	<div class="sm:hidden">
		<label for="tabs" class="sr-only">Select a tab</label>
		<!-- Use an "onChange" listener to redirect the user to the selected tab URL. -->
		<select
			id="tabs"
			name="tabs"
			class="block w-full rounded-md border-gray-300 py-2 pl-3 pr-10 text-base focus:border-indigo-500 focus:outline-none focus:ring-indigo-500 sm:text-sm"
		>
			<option>My Account</option>
			<option>Company</option>
			<option selected>Team Members</option>
			<option>Billing</option>
		</select>
	</div>
	<div class="hidden sm:block">
		<div class="border-b border-gray-200">
			<nav class="-mb-px flex space-x-8" aria-label="Tabs">
				<NavItem active={$page.route.id == '/__admin'} id="/__admin" label="Overview" />
				<NavItem
					active={$page.route.id?.startsWith('/__admin/dbs')}
					id="/__admin/dbs"
					label="Databases"
				/>
				{#if data.features['cqlExpressionCache'].enabled}
					<NavItem
						active={$page.route.id?.startsWith('/__admin/cql')}
						id="/__admin/cql"
						label="CQL"
					/>
				{/if}
			</nav>
		</div>
	</div>
</div>

<slot />
