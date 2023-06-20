<script lang="ts">
	import '../app.css';
	import '@fontsource/inter/variable.css';
	import { page } from '$app/stores';
	import NavItem from './nav-item.svelte';

	export let data;

	const version = data.capabilityStatement?.software?.version;
</script>

<div class="min-h-full">
	<nav class="fixed w-full bg-gray-800 z-10">
		<div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
			<div class="flex h-16 items-center justify-between">
				<div class="flex items-center">
					<div class="flex-shrink-0 text-white">
						{data.capabilityStatement?.software?.name ?? 'Blaze'}
						{#if version}v{version}{/if}
					</div>
					<div class="hidden md:block">
						<div class="ml-10 flex items-baseline space-x-4">
							<NavItem
								active={$page.route.id != '/_history' && $page.route.id != '/metadata'}
								id="/"
								label="Home"
							/>
							<NavItem active={$page.route.id == '/_history'} id="/_history" label="History" />
							<NavItem active={$page.route.id == '/metadata'} id="/metadata" label="Metadata" />
							<NavItem active={$page.route.id == '/__admin'} id="/__admin" label="Admin" />
						</div>
					</div>
				</div>
			</div>
		</div>
	</nav>

	<div class="pt-16">
		<slot />
	</div>
</div>
