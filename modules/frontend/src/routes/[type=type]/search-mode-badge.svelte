<script lang="ts">
	import { SearchEntryMode } from '../../fhir';

	export let searchMode: SearchEntryMode;

	function modeBgColor(mode: SearchEntryMode): string {
		switch (mode) {
			case SearchEntryMode.match:
				return 'bg-green-100';
			case SearchEntryMode.include:
				return 'bg-fuchsia-100';
			default:
				return 'bg-red-100';
		}
	}

	function modeTextColor(mode: SearchEntryMode): string {
		switch (mode) {
			case SearchEntryMode.match:
				return 'text-green-800';
			case SearchEntryMode.include:
				return 'text-fuchsia-800';
			default:
				return 'text-red-800';
		}
	}
</script>

<div class="ml-2 flex flex-shrink-0">
	<p
		class="inline-flex items-center rounded-full {modeBgColor(
			searchMode
		)} px-2.5 py-0.5 text-xs font-medium {modeTextColor(searchMode)}"
	>
		{searchMode}
	</p>
</div>
