<script lang="ts">
  interface Props {
    id: string;
    label: string;
    value?: string;
  }

  let { id, label, value }: Props = $props();
</script>

<div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-6">
  <label for={id} class="block text-sm/6 font-medium text-gray-900 sm:pt-1.5">{label}</label>
  <div class="mt-2 sm:col-span-2 sm:mt-0">
    <input
      type="text"
      name={id}
      {id}
      class="block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:max-w-xs sm:text-sm/6"
      {value}
    />
  </div>
</div>
