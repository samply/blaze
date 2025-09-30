<script lang="ts">
  import { resolve } from '$app/paths';

  interface Props {
    name: string;
    total: number;
  }

  let { name, total }: Props = $props();
</script>

<tr>
  <td
    class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 dark:text-gray-100 sm:pl-0"
    ><a href={resolve('/[type=type]', { type: name })}>{name}</a></td
  >
  <td class="whitespace-nowrap py-4 pr-3 text-sm text-right text-gray-900 dark:text-gray-100"
    >{new Intl.NumberFormat('en').format(total)}</td
  >
</tr>
