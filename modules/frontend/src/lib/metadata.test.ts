import { describe, it, expect, vi } from 'vitest';
import type { Bundle, StructureDefinition } from 'fhir/r4';

// The cache in `$lib/metadata.js` is module state, so every test needs a fresh
// module instance.
async function importMetadata() {
  vi.resetModules();
  return await import('$lib/metadata.js');
}

function structureDefinition(type: string): StructureDefinition {
  return {
    resourceType: 'StructureDefinition',
    url: `http://hl7.org/fhir/StructureDefinition/${type}`,
    name: type,
    status: 'active',
    kind: 'resource',
    abstract: false,
    type: type
  };
}

function bundle(...resources: StructureDefinition[]): Bundle {
  return {
    resourceType: 'Bundle',
    type: 'searchset',
    entry: resources.length === 0 ? undefined : resources.map((resource) => ({ resource }))
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function okFetch(body: unknown) {
  return vi.fn(async () => jsonResponse(body));
}

describe('fetchStructureDefinition test', () => {
  it('requests the canonical URL of the type', async () => {
    const { fetchStructureDefinition } = await importMetadata();
    const fetch = okFetch(bundle(structureDefinition('Patient')));

    await fetchStructureDefinition('Patient', fetch);

    expect(fetch).toHaveBeenCalledWith(
      '/fhir/StructureDefinition?url=http://hl7.org/fhir/StructureDefinition/Patient',
      { headers: { Accept: 'application/fhir+json' } }
    );
  });

  it('returns the single bundle entry', async () => {
    const { fetchStructureDefinition } = await importMetadata();
    const patient = structureDefinition('Patient');

    expect(await fetchStructureDefinition('Patient', okFetch(bundle(patient)))).toEqual(patient);
  });

  it('fetches a type only once', async () => {
    const { fetchStructureDefinition } = await importMetadata();
    const patient = structureDefinition('Patient');
    const fetch = okFetch(bundle(patient));

    await fetchStructureDefinition('Patient', fetch);

    expect(await fetchStructureDefinition('Patient', fetch)).toEqual(patient);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('fetches different types separately', async () => {
    const { fetchStructureDefinition } = await importMetadata();
    const fetch = vi.fn(async (url: URL | RequestInfo) =>
      jsonResponse(
        bundle(structureDefinition(String(url).endsWith('Patient') ? 'Patient' : 'Observation'))
      )
    );

    expect((await fetchStructureDefinition('Patient', fetch)).type).toBe('Patient');
    expect((await fetchStructureDefinition('Observation', fetch)).type).toBe('Observation');
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('shares one request between concurrent callers', async () => {
    const { fetchStructureDefinition } = await importMetadata();
    const patient = structureDefinition('Patient');
    const fetch = okFetch(bundle(patient));

    const results = await Promise.all([
      fetchStructureDefinition('Patient', fetch),
      fetchStructureDefinition('Patient', fetch)
    ]);

    expect(results).toEqual([patient, patient]);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  // The status is repeated inside the body because the StructureDefinitions are
  // loaded while transforming a streamed bundle, whose rejection reaches the
  // `{:catch}` block with the body alone.
  it('fails with the status of an unsuccessful response', async () => {
    const { fetchStructureDefinition } = await importMetadata();
    const fetch = vi.fn(async () => jsonResponse({}, 503));

    await expect(fetchStructureDefinition('Patient', fetch)).rejects.toMatchObject({
      status: 503,
      body: { status: 503, message: 'error while loading the Patient StructureDefinition' }
    });
  });

  it('fails with 404 on a bundle without entries', async () => {
    const { fetchStructureDefinition } = await importMetadata();

    await expect(fetchStructureDefinition('Patient', okFetch(bundle()))).rejects.toMatchObject({
      status: 404,
      body: { status: 404, message: 'expected one bundle entry but found none' }
    });
  });

  it('fails with 404 on a bundle with more than one entry', async () => {
    const { fetchStructureDefinition } = await importMetadata();
    const twoEntries = bundle(structureDefinition('Patient'), structureDefinition('Patient'));

    await expect(fetchStructureDefinition('Patient', okFetch(twoEntries))).rejects.toMatchObject({
      status: 404,
      body: { status: 404, message: 'expected one bundle entry but found 2' }
    });
  });

  it('retries a failed load on the next call', async () => {
    const { fetchStructureDefinition } = await importMetadata();
    const patient = structureDefinition('Patient');
    const fetch = vi
      .fn<() => Promise<Response>>()
      .mockResolvedValueOnce(jsonResponse({}, 503))
      .mockResolvedValueOnce(jsonResponse(bundle(patient)));

    await expect(fetchStructureDefinition('Patient', fetch)).rejects.toMatchObject({ status: 503 });

    expect(await fetchStructureDefinition('Patient', fetch)).toEqual(patient);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('retries a load that failed for concurrent callers', async () => {
    const { fetchStructureDefinition } = await importMetadata();
    const patient = structureDefinition('Patient');
    const fetch = vi
      .fn<() => Promise<Response>>()
      .mockResolvedValueOnce(jsonResponse({}, 503))
      .mockResolvedValueOnce(jsonResponse(bundle(patient)));

    const failures = Promise.allSettled([
      fetchStructureDefinition('Patient', fetch),
      fetchStructureDefinition('Patient', fetch)
    ]);

    expect((await failures).map((result) => result.status)).toEqual(['rejected', 'rejected']);
    expect(await fetchStructureDefinition('Patient', fetch)).toEqual(patient);
    expect(fetch).toHaveBeenCalledTimes(2);
  });
});
