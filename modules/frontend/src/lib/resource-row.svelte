<script lang="ts">
  import { base } from '$app/paths';

  interface Props {
    name: string;
    total: number;
  }

  let { name, total }: Props = $props();
</script>

<tr>
  <td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-0"
    ><a href="{base}/{name}">{name}</a></td
  >
  <td class="whitespace-nowrap py-4 pr-3 text-sm text-right text-gray-900"
    >{new Intl.NumberFormat('en').format(total)}</td
  >
</tr>
