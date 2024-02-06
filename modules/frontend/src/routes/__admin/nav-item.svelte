<script lang="ts">
	import { base } from '$app/paths';

	export let active = false;
	export let id: string;
	export let label: string;
</script>

{#if active}
	<a
		href="{base}{id}"
		class="border-indigo-500 text-indigo-600 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
		aria-current="page">{label}</a
	>
{:else}
	<a
		href="{base}{id}"
		class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
		>{label}</a
	>
{/if}
