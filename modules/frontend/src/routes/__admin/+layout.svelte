<script lang="ts">
	import NavItem from './nav-item.svelte';
	import { page } from '$app/stores';
</script>

<div class="mx-auto max-w-7xl sm:px-6 lg:px-8">
	<div class="border-b border-gray-200">
		<nav class="-mb-px flex space-x-8" aria-label="Tabs">
			<NavItem active={$page.route.id === '/__admin'} id="/__admin" label="Overview" />
			<NavItem
				active={$page.route.id?.startsWith('/__admin/dbs')}
				id="/__admin/dbs"
				label="Databases"
			/>
			<NavItem
				active={$page.route.id?.startsWith('/__admin/jobs')}
				id="/__admin/jobs"
				label="Jobs"
			/>
		</nav>
	</div>
</div>

<slot />
