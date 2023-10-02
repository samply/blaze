<script lang="ts">
	import prettyBytes from 'pretty-bytes';

	export let level: number;
	export let size: number;
	export let numFiles: number;
</script>

<tr>
	<td class="py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6">{level}</td>
	<td class="px-3 py-4 text-sm text-gray-500">{numFiles}</td>
	<td class="px-3 py-4 text-sm text-gray-500"
		>{prettyBytes(size, { binary: true, maximumFractionDigits: 1 })}</td
	>
</tr>
