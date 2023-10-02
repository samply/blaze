<script lang="ts">
	import '../app.css';
	import '@fontsource-variable/inter';
	import { page } from '$app/stores';
	import NavItem from './nav-item.svelte';
	import NavItemMobile from './nav-item-mobile.svelte';

	let mobileMenuOpen = false;

	function routeStartsWith(prefix: string): boolean {
		return $page.route.id != null && $page.route.id.startsWith(prefix);
	}
</script>

<div class="min-h-full">
	<nav class="border-b border-gray-200 bg-white">
		<div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
			<div class="flex h-16 justify-between">
				<div class="flex">
					<div class="flex flex-shrink-0 items-center">
						<img
							class="block h-8 w-auto lg:hidden"
							src="https://tailwindui.com/img/logos/mark.svg?color=indigo&shade=600"
							alt="Blaze"
						/>
						<img
							class="hidden h-8 w-auto lg:block"
							src="https://tailwindui.com/img/logos/mark.svg?color=indigo&shade=600"
							alt="Blaze"
						/>
					</div>
					<div class="hidden sm:-my-px sm:ml-6 sm:flex sm:space-x-8">
						<NavItem active={$page.route.id == '/'} id="/" label="Home" />
						<NavItem active={routeStartsWith('/_history')} id="/_history" label="History" />
						<NavItem active={routeStartsWith('/metadata')} id="/metadata" label="Metadata" />
						<NavItem active={routeStartsWith('/__admin')} id="/__admin" label="Admin" />
					</div>
				</div>
			</div>
		</div>

		<!-- Mobile menu, show/hide based on menu state. -->
		<div class="sm:hidden" class:hidden={!mobileMenuOpen} id="mobile-menu">
			<div class="space-y-1 pb-3 pt-2">
				<NavItemMobile active={$page.route.id == '/'} id="/" label="Home" />
				<NavItemMobile active={routeStartsWith('/_history')} id="/_history" label="History" />
				<NavItemMobile active={routeStartsWith('/metadata')} id="/metadata" label="Metadata" />
				<NavItemMobile active={routeStartsWith('/__admin')} id="/__admin" label="Admin" />
			</div>
		</div>
	</nav>

	<slot />
</div>
