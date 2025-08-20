<script lang="ts">
  import type { PageProps } from './$types';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';
  import BreadcrumbEntry from '$lib/breadcrumb/entry.svelte';
  import CheckBoxes from '$lib/tailwind/form/check-boxes.svelte';
  import ResultList from '../../result-list.svelte';
  import SubmitButton from '$lib/tailwind/form/button-submit.svelte';
  import CheckBox from '$lib/tailwind/form/check-box.svelte';
  import Section from '$lib/tailwind/form/section.svelte';
  import Form from '$lib/tailwind/form.svelte';
  import TextField from '$lib/tailwind/form/text-field.svelte';

  import { title } from '$lib/resource.js';

  let { data, form, params }: PageProps = $props();
</script>

<svelte:head>
  <title>$validate-code - {title(data.valueSet)} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType type="ValueSet" />
    <BreadcrumbEntryResource type="ValueSet" {...params} resource={data.valueSet} />
    <BreadcrumbEntry>
      <span class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400">$validate-code</span>
    </BreadcrumbEntry>
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
  <h2 class="text-base/7 font-semibold text-gray-900 dark:text-gray-100">
    {title(data.valueSet)}
  </h2>
  {#if data.valueSet.description}
    <p class="mt-1 max-w-2xl text-sm/6 text-gray-600">{data.valueSet.description}</p>
  {/if}

  <Form class="mt-4">
    <Section name="Parameters">
      <TextField id="code" label="Code" value={form?.code} />
      <TextField id="system" label="System" value={form?.system} />
      <TextField id="systemVersion" label="System Version" value={form?.systemVersion} />
      <TextField id="display" label="Display" value={form?.display} />
      <TextField id="displayLanguage" label="Display Language" value={form?.displayLanguage} />
      <CheckBoxes name="Infer System">
        <CheckBox id="inferSystem" label="Infer System" checked={form?.inferSystem ?? false} />
      </CheckBoxes>
    </Section>
    {#snippet buttons()}
      <SubmitButton name="Submit" />
    {/snippet}
  </Form>

  {#if form?.incorrect}
    <p class="text-red-600 dark:text-red-400">{form.msg}</p>
  {/if}

  {#if form?.result}
    <ResultList parameters={form?.result} />
  {/if}
</main>
