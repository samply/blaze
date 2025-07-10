<script lang="ts">
  import type { PageProps } from './$types';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';
  import BreadcrumbEntry from '$lib/breadcrumb/entry.svelte';
  import CheckBoxes from '$lib/tailwind/form/check-boxes.svelte';
  import SubmitButton from '$lib/tailwind/form/button-submit.svelte';
  import CheckBox from '$lib/tailwind/form/check-box.svelte';
  import Section from '$lib/tailwind/form/section.svelte';
  import Form from '$lib/tailwind/form.svelte';
  import TextField from '$lib/tailwind/form/text-field.svelte';

  import { title } from '$lib/resource.js';

  let { data, form }: PageProps = $props();
</script>

<svelte:head>
  <title>$expand - {title(data.valueSet)} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType type="ValueSet" />
    <BreadcrumbEntryResource type="ValueSet" resource={data.valueSet} />
    <BreadcrumbEntry>
      <span class="ml-4 text-sm font-medium text-gray-500">$expand</span>
    </BreadcrumbEntry>
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
  <h2 class="text-base/7 font-semibold text-gray-900">
    {title(data.valueSet)}
  </h2>
  {#if data.valueSet.description}
    <p class="mt-1 max-w-2xl text-sm/6 text-gray-600">{data.valueSet.description}</p>
  {/if}

  <Form class="mt-4">
    <Section name="Parameters">
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
      <SubmitButton name="Submit" />
    {/snippet}
  </Form>

  {#if form?.incorrect}
    <p class="text-red-600">{form.msg}</p>
  {/if}

  {#if form?.valueSet?.expansion?.contains}
    <ul role="list" class="divide-y divide-gray-100">
      {#each form.valueSet.expansion.contains as contains}
        <li class="flex flex-wrap items-center justify-between gap-x-6 gap-y-4 py-5 sm:flex-nowrap">
          <div>
            <p class="text-sm/6 font-semibold text-gray-900">
              {contains.display}
            </p>
            <div class="mt-1 flex items-center gap-x-2 text-xs/5 text-gray-500">
              <p>{contains.system}</p>
              <svg viewBox="0 0 2 2" class="size-0.5 fill-current">
                <circle cx="1" cy="1" r="1" />
              </svg>
              {#if contains.version}
                <p>{contains.version}</p>
                <svg viewBox="0 0 2 2" class="size-0.5 fill-current">
                  <circle cx="1" cy="1" r="1" />
                </svg>
              {/if}
              <p>{contains.code}</p>
            </div>
            {#if contains.designation}
              <ul>
                {#each contains.designation as designation}
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
