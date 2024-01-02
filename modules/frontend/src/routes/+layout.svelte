<script lang="ts">
	import '../app.css';
	import '@fontsource-variable/inter';
	import { page } from '$app/stores';
	import { onNavigate } from '$app/navigation';

	import NavItem from './nav-item.svelte';

	// https://svelte.dev/blog/view-transitions
	onNavigate((navigation) => {
		if (!document.startViewTransition) return;

		return new Promise((resolve) => {
			document.startViewTransition(async () => {
				resolve();
				await navigation.complete;
			});
		});
	});

	function isHomeRoute(routeId: string | null): boolean {
		return (
			routeId != null &&
			!routeId.startsWith('/_history') &&
			!routeId.startsWith('/metadata') &&
			!routeId.startsWith('/__admin')
		);
	}
</script>

<div class="min-h-full">
	<nav class="border-b border-gray-200 bg-white">
		<div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
			<div class="flex h-16 justify-between">
				<div class="flex space-x-8">
					<NavItem active={isHomeRoute($page.route.id)} id="/" label="Home" />
					<NavItem
						active={$page.route.id?.startsWith('/_history')}
						id="/_history"
						label="History"
					/>
					<NavItem
						active={$page.route.id?.startsWith('/metadata')}
						id="/metadata"
						label="Metadata"
					/>
					<NavItem active={$page.route.id?.startsWith('/__admin')} id="/__admin" label="Admin" />
				</div>
			</div>
		</div>
	</nav>

	<slot />
</div>
