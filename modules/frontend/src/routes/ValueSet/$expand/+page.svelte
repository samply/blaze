<script lang="ts">
  import type { PageProps } from './$types';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntry from '$lib/breadcrumb/entry.svelte';
  import Form from '$lib/tailwind/form.svelte';
  import Section from '$lib/tailwind/form/section.svelte';
  import TextField from '$lib/tailwind/form/text-field.svelte';
  import CheckBoxes from '$lib/tailwind/form/check-boxes.svelte';
  import CheckBox from '$lib/tailwind/form/check-box.svelte';
  import SubmitButton from '$lib/tailwind/form/button-submit.svelte';

  let { form }: PageProps = $props();
</script>

<svelte:head>
  <title>$expand - ValueSet - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType type="ValueSet" />
    <BreadcrumbEntry>
      <span class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400">$expand</span>
    </BreadcrumbEntry>
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
  <Form class="mt-4">
    <Section name="Parameters">
      <TextField id="url" label="ValueSet URL" value={form?.url} />
      <TextField id="valueSetVersion" label="ValueSet Version" value={form?.valueSetVersion} />
      <TextField id="filter" label="Filter" value={form?.filter} />
      <TextField id="property" label="Property" value={form?.property} />
      <TextField id="displayLanguage" label="Display Language" value={form?.displayLanguage} />
      <TextField id="systemVersion" label="System Version" value={form?.systemVersion} />
      <CheckBoxes name="Options">
        <CheckBox
          id="includeDesignations"
          label="Include Designations"
          checked={form?.includeDesignations ?? false}
        />
        <CheckBox
          id="includeDefinition"
          label="Include Definition"
          checked={form?.includeDefinition ?? false}
        />
        <CheckBox id="activeOnly" label="Active Only" checked={form?.activeOnly ?? false} />
        <CheckBox
          id="excludeNested"
          label="Exclude Nested"
          checked={form?.excludeNested ?? false}
        />
      </CheckBoxes>
    </Section>
    {#snippet buttons()}
      <div class="flex gap-4">
        {#if form?.incorrect}
          <p class="text-sm text-red-600 dark:text-red-400">{form.msg}</p>
        {/if}
        <SubmitButton name="Submit" />
      </div>
    {/snippet}
  </Form>

  {#if form?.valueSet?.expansion?.contains}
    <ul role="list" class="divide-y divide-gray-100 dark:divide-gray-700">
      {#each form.valueSet.expansion.contains as contains (contains.system + '-' + contains.version + '-' + contains.code)}
        <li class="flex flex-wrap items-center justify-between gap-x-6 gap-y-4 py-5 sm:flex-nowrap">
          <div>
            <p class="text-sm/6 font-semibold text-gray-900 dark:text-gray-100">
              {contains.display}
            </p>
            <div class="mt-1 flex items-center gap-x-2 text-xs/5 text-gray-500 dark:text-gray-400">
              <p>{contains.system}</p>
              •
              {#if contains.version}
                <p>{contains.version}</p>
                •
              {/if}
              <p>{contains.code}</p>
            </div>
            {#if contains.designation}
              <ul>
                {#each contains.designation as designation (designation.language + '-' + designation.use + '-' + designation.value)}
                  <li>{designation.value}</li>
                {/each}
              </ul>
            {/if}
          </div>
        </li>
      {/each}
    </ul>
  {/if}
</main>
