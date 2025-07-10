<script lang="ts">
  interface Props {
    name: string;
  }

  let { name }: Props = $props();
</script>

<button
  type="submit"
  class="inline-flex justify-center rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
>
  {name}
</button>
