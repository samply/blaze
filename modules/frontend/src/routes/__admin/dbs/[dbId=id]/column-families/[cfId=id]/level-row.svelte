<script lang="ts">
	import prettyBytes from 'pretty-bytes';

	export let level: number;
	export let size: number;
	export let numFiles: number;
</script>

<tr>
	<td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-0">{level}</td
	>
	<td class="whitespace-nowrap px-3 py-2 text-right text-sm text-gray-500">{numFiles}</td>
	<td class="whitespace-nowrap px-3 py-2 text-right text-sm text-gray-500"
		>{prettyBytes(size, { binary: true, maximumFractionDigits: 1 })}</td
	>
</tr>
