<script lang="ts">
  interface Props {
    searchMode: 'match' | 'include' | 'outcome' | 'unknown';
  }

  let { searchMode }: Props = $props();

  function modeBgColor(mode: string): string {
    switch (mode) {
      case 'match':
        return 'bg-green-100';
      case 'include':
        return 'bg-fuchsia-100';
      default:
        return 'bg-red-100';
    }
  }

  function modeTextColor(mode: string): string {
    switch (mode) {
      case 'match':
        return 'text-green-800';
      case 'include':
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
