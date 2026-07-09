<script lang="ts">
  interface Props {
    id: string;
    label: string;
    checked?: boolean;
    onchange?: (checked: boolean) => void;
  }

  let { id, label, checked = $bindable(false), onchange }: Props = $props();
</script>

<div class="flex items-center gap-3">
  <div
    class="group relative inline-flex w-11 shrink-0 rounded-full bg-gray-200 p-0.5 inset-ring inset-ring-gray-900/5 outline-offset-2 outline-indigo-600 transition-colors duration-200 ease-in-out has-checked:bg-indigo-600 has-focus-visible:outline-2"
  >
    <span
      class="size-5 rounded-full bg-white dark:bg-gray-800 shadow-xs ring-1 ring-gray-900/5 transition-transform duration-200 ease-in-out group-has-checked:translate-x-5"
    ></span>
    <input
      {id}
      type="checkbox"
      name={id}
      aria-labelledby="{id}-label"
      class="absolute inset-0 appearance-none focus:outline-hidden"
      bind:checked
      onchange={() => onchange?.(checked)}
    />
  </div>
  <div class="text-sm">
    <label id="{id}-label" class="font-medium text-gray-900 dark:text-gray-100" for={id}>
      {label}
    </label>
  </div>
</div>
