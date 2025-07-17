<script lang="ts">
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
    class="col-start-1 row-start-1 block w-full rounded-md bg-white py-1.5 pr-10 pl-3 text-red-900 outline-1 -outline-offset-1 outline-red-300 placeholder:text-red-300 focus:outline-2 focus:-outline-offset-2 focus:outline-red-600 sm:pr-9 sm:text-sm/6 sm:leading-6"
  />
  <svg
    viewBox="0 0 16 16"
    fill="currentColor"
    data-slot="icon"
    aria-hidden="true"
    class="pointer-events-none col-start-1 row-start-1 mr-3 size-5 self-center justify-self-end text-red-500 sm:size-4"
  >
    <path
      d="M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14ZM8 4a.75.75 0 0 1 .75.75v3a.75.75 0 0 1-1.5 0v-3A.75.75 0 0 1 8 4Zm0 8a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z"
      clip-rule="evenodd"
      fill-rule="evenodd"
    />
  </svg>
</div>
