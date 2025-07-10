<script lang="ts">
  interface Props {
    method: 'GET' | 'HEAD' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  }

  let { method }: Props = $props();

  function modeBgColor(mode: string): string {
    switch (mode) {
      case 'POST':
        return 'bg-green-100';
      case 'PUT':
        return 'bg-fuchsia-100';
      default:
        return 'bg-red-100';
    }
  }

  function modeTextColor(mode: string): string {
    switch (mode) {
      case 'POST':
        return 'text-green-800';
      case 'PUT':
        return 'text-fuchsia-800';
      default:
        return 'text-red-800';
    }
  }
</script>

<div class="ml-2 flex flex-shrink-0">
  <p
    class="inline-flex items-center rounded-full {modeBgColor(
      method
    )} px-2.5 py-0.5 text-xs font-medium {modeTextColor(method)}"
  >
    {method}
  </p>
</div>
