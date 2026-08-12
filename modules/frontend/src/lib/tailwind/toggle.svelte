<script lang="ts">
  interface Props {
    id: string;
    label: string;
    /** One-line explanation of what the switch does. */
    description?: string;
    checked?: boolean;
  }

  let { id, label, description, checked = $bindable(false) }: Props = $props();
</script>

<div class="flex items-start gap-3">
  <div
    class="group relative inline-flex w-11 shrink-0 rounded-full bg-gray-200 p-0.5 inset-ring inset-ring-gray-900/5 outline-offset-2 outline-indigo-600 transition-colors duration-200 ease-in-out has-checked:bg-indigo-600 has-focus-visible:outline-2"
  >
    <span
      class="size-5 rounded-full bg-white shadow-xs ring-1 ring-gray-900/5 transition-transform duration-200 ease-in-out group-has-checked:translate-x-5 dark:bg-gray-800"
    ></span>
    <input
      {id}
      type="checkbox"
      name={id}
      aria-labelledby="{id}-label"
      aria-describedby={description ? `${id}-description` : undefined}
      class="absolute inset-0 appearance-none focus:outline-hidden"
      bind:checked
    />
  </div>
  <div class="text-sm">
    <label id="{id}-label" class="font-medium text-gray-900 dark:text-gray-100" for={id}>
      {label}
    </label>
    {#if description}
      <p id="{id}-description" class="text-gray-500 dark:text-gray-400">
        {description}
      </p>
    {/if}
  </div>
</div>
