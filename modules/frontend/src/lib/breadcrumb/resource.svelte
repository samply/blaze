<script lang="ts">
	import { base } from '$app/paths';
	import { page } from '$app/stores';
	import Entry from './entry.svelte';
</script>

<Entry>
	<a
		href="{base}/{$page.params.type}/{$page.params.id}"
		class="ml-4 text-sm font-medium text-gray-500 hover:text-gray-700">{$page.params.id}</a
	>
</Entry>
