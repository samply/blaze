<script lang="ts">
	import { base } from '$app/paths';
	import Entry from './entry.svelte';

	interface Props {
		url: string;
	}

	let { url }: Props = $props();
</script>

<Entry>
	<a href="{base}/{url}" class="ml-4 text-sm font-medium text-gray-500 hover:text-gray-700"
		>History</a
	>
</Entry>
