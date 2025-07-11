<script lang="ts">
  import type { Color } from '$lib/tailwind/badge.svelte';
  import type { SearchMode } from '$lib/fhir';
  import Badge from '$lib/tailwind/badge.svelte';

  interface Props {
    mode: SearchMode | 'unknown';
  }

  let { mode }: Props = $props();

  // See https://www.hl7.org/fhir/R4B/valueset-search-entry-mode.html
  function modeColor(mode: SearchMode | 'unknown'): Color {
    switch (mode) {
      case 'match':
        return 'green';
      case 'include':
        return 'fuchsia';
      default:
        return 'red';
    }
  }

  function modeTitle(mode: SearchMode | 'unknown'): string | undefined {
    switch (mode) {
      case 'match':
        return 'Direct match';
      case 'include':
        return 'Included resource';
      default:
        return undefined;
    }
  }
</script>

<div class="ml-2 flex flex-shrink-0">
  <Badge value={mode} title={modeTitle(mode)} color={modeColor(mode)} />
</div>
