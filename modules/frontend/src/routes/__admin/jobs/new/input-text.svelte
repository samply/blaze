<script lang="ts">
  interface Props {
    name: string;
    value: string;
  }

  let { name, value }: Props = $props();
</script>

<div class="relative mt-2 rounded-md shadow-sm">
  <input
    type="text"
    id={name}
    {name}
    {value}
    class="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
  />
</div>
