<script lang="ts">
  interface Props {
    mode: 'match' | 'include' | 'outcome';
  }

  let { mode }: Props = $props();

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

  function modeTitle(mode: string): string {
    switch (mode) {
      case 'match':
        return 'The resource is being returned because it meets the parameters specified in the search request.';
      case 'include':
        return 'The resource is being returned either because it is referred to from a record in the result set or that the server believes is useful or necessary for the client to process other results.';
      default:
        return 'Information related to the processing of a search. For example, a server MAY use an outcome record to report to a client that a search was unacceptable.';
    }
  }
</script>

<div class="ml-2 flex flex-shrink-0">
  <p
    role="note"
    title={modeTitle(mode)}
    class="inline-flex items-center rounded-full {modeBgColor(mode)}
		px-2.5 py-0.5 text-xs font-medium {modeTextColor(mode)}"
  >
    {mode}
  </p>
</div>
