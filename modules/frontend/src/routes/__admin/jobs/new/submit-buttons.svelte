<script lang="ts">
  import { resolve } from '$app/paths';
</script>

<div class="mt-6 flex items-center justify-end gap-x-6">
  <a
    class="text-sm font-semibold leading-6 text-gray-900 dark:text-gray-100"
    href={resolve('/__admin/jobs')}>Cancel</a
  >
  <button
    type="submit"
    class="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 enabled:cursor-pointer"
    >Submit New Job</button
  >
</div>
