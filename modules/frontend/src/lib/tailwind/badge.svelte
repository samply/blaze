<script module lang="ts">
  export type Color = 'gray' | 'green' | 'fuchsia' | 'red' | 'orange' | 'yellow' | 'blue';
</script>

<script lang="ts">
  interface Props {
    value: string;
    title?: string;
    color?: Color;
    style?: string;
    class?: string;
  }

  function chooseColor(color?: Color): string {
    switch (color) {
      case 'green':
        return 'bg-green-100 dark:bg-green-800/50 text-green-800 dark:text-green-100';
      case 'fuchsia':
        return 'bg-fuchsia-100 dark:bg-fuchsia-800/50 text-fuchsia-800 dark:text-fuchsia-100';
      case 'red':
        return 'bg-red-100 dark:bg-red-800/50 text-red-800 dark:text-red-100';
      case 'orange':
        return 'bg-orange-100 dark:bg-orange-800/50 text-orange-800 dark:text-orange-100';
      case 'yellow':
        return 'bg-yellow-100 dark:bg-yellow-800/50 text-yellow-800 dark:text-yellow-100';
      case 'blue':
        return 'bg-blue-100 dark:bg-blue-800/50 text-blue-800 dark:text-blue-100';
      default:
        return 'bg-gray-200 dark:bg-gray-700/50 text-gray-800 dark:text-gray-100';
    }
  }

  let { value, title, color, style, class: clss }: Props = $props();
</script>

<span
  role="note"
  {title}
  {style}
  class="rounded-full ml-1 {chooseColor(color)} px-2 py-1 text-xs font-medium {clss}"
>
  {value}
</span>
