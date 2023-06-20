<script lang="ts">
	import { base } from '$app/paths';

	export let active = false;
	export let id: string;
	export let label: string;
</script>

{#if active}
	<a
		href="{base}{id}"
		class="bg-gray-900 text-white rounded-md px-3 py-2 text-sm font-medium"
		aria-current="page">{label}</a
	>
{:else}
	<a
		href="{base}{id}"
		class="text-gray-300 hover:bg-gray-700 hover:text-white rounded-md px-3 py-2 text-sm font-medium"
		>{label}</a
	>
{/if}
