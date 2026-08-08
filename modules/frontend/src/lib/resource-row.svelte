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
    class="py-4 pr-3 pl-4 text-sm font-medium whitespace-nowrap text-gray-900 sm:pl-0 dark:text-gray-100"
    ><a href={resolve('/[type=type]', { type: name })}>{name}</a></td
  >
  <td class="py-4 pr-3 text-right text-sm whitespace-nowrap text-gray-900 dark:text-gray-100"
    >{new Intl.NumberFormat('en').format(total)}</td
  >
</tr>
