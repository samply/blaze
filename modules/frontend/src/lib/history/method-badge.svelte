<script lang="ts">
  import type { HttpMethod } from '$lib/fhir';
  import type { Color } from '$lib/tailwind/badge.svelte';
  import Badge from '$lib/tailwind/badge.svelte';

  interface Props {
    method: HttpMethod;
  }

  let { method }: Props = $props();

  function methodColor(method: HttpMethod): Color {
    switch (method) {
      case 'POST':
        return 'green';
      case 'PUT':
        return 'fuchsia';
      default:
        return 'red';
    }
  }
</script>

<div class="ml-2 flex flex-shrink-0">
  <Badge value={method} color={methodColor(method)} title="Request method" />
</div>
