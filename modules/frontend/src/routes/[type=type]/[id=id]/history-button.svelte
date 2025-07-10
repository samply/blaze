<script lang="ts">
  import { base } from '$app/paths';
  import { page } from '$app/state';
</script>

<a
  href="{base}/{page.params.type}/{page.params.id}/_history"
  class="flex-none rounded-md bg-white px-2 py-1 text-sm font-semibold text-gray-900 ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
  title="Go to resource history">History</a
>
