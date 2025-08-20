<script lang="ts">
  import { ExclamationCircle } from 'svelte-heros-v2';

  interface Props {
    name: string;
    value: string;
  }

  let { name, value }: Props = $props();
</script>

<div class="mt-2 grid grid-cols-1">
  <input
    type="text"
    id={name}
    {name}
    {value}
    class="col-start-1 row-start-1 block w-full rounded-md bg-white dark:bg-gray-800 py-1.5 pr-10 pl-3 text-red-900 dark:text-red-300 outline-1 -outline-offset-1 outline-red-300 placeholder:text-red-300 focus:outline-2 focus:-outline-offset-2 focus:outline-red-600 focus:dark:outline-red-200 sm:pr-9 sm:text-sm/6 sm:leading-6"
  />
  <ExclamationCircle
    variation="mini"
    class="pointer-events-none col-start-1 row-start-1 mr-3 size-5 self-center justify-self-end text-red-500 dark:text-red-400"
  />
</div>
