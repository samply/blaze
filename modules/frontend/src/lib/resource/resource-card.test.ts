import type {
  ClaimResponse,
  FhirResource,
  Measure,
  Observation,
  Patient,
  Questionnaire
} from 'fhir/r4';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'svelte/server';

const state = vi.hoisted(() => ({ url: new URL('http://localhost/fhir/Patient/0') }));

vi.mock('$app/state', () => ({ page: state }));

const { default: ResourceCard } = await import('./resource-card.svelte');

function renderTab(resource: FhirResource, tab: 'default' | 'json'): string {
  state.url = new URL(`http://localhost/fhir/Patient/0?__tab=${tab}`);
  return render(ResourceCard, { props: { resource } }).body;
}

const extension = [{ url: 'http://example.com/ext', valueString: 'ext-value' }];

describe('resource card test', () => {
  describe('normal view', () => {
    it('formats dates', () => {
      const patient: Patient = { resourceType: 'Patient', birthDate: '2020-01-02' };

      expect(renderTab(patient, 'default')).toContain('<time datetime="2020-01-02"');
    });

    it('names choice-typed elements without type suffix', () => {
      const observation: Observation = {
        resourceType: 'Observation',
        status: 'final',
        code: { text: 'code-text' },
        valueQuantity: { value: 23, unit: 'kg' }
      };

      const body = renderTab(observation, 'default');

      expect(body).toMatch(/>\s*Value\s*</);
      expect(body).not.toContain('ValueQuantity');
      expect(body).toMatch(/23\s*(<!--\[\d*-->)?kg/);
    });

    it('links canonicals to resources of their target type', () => {
      const measure: Measure = {
        resourceType: 'Measure',
        status: 'active',
        library: ['http://example.com/Library/0']
      };

      expect(renderTab(measure, 'default')).toContain(
        '/fhir/Library?_summary=true&amp;url=http%3A%2F%2Fexample.com%2FLibrary%2F0'
      );
    });

    it('marks primitive values with extensions', () => {
      const patient: Patient = {
        resourceType: 'Patient',
        birthDate: '2020-01-02',
        _birthDate: { extension }
      };

      expect(renderTab(patient, 'default')).toContain('(has extensions)');
    });

    it('renders the data absent reason of primitive values without value', () => {
      const patient: Patient = {
        resourceType: 'Patient',
        _birthDate: {
          extension: [
            {
              url: 'http://hl7.org/fhir/StructureDefinition/data-absent-reason',
              valueCode: 'unknown'
            }
          ]
        }
      };

      const body = renderTab(patient, 'default');

      expect(body).toMatch(/>\s*BirthDate\s*</);
      expect(body).toMatch(/title="Data absent reason"[^>]*>\s*absent: unknown\s*</);
      expect(body).not.toContain('(has extensions)');
    });

    it('marks other extensions next to the data absent reason', () => {
      const patient: Patient = {
        resourceType: 'Patient',
        _birthDate: {
          extension: [
            {
              url: 'http://hl7.org/fhir/StructureDefinition/data-absent-reason',
              valueCode: 'masked'
            },
            ...extension
          ]
        }
      };

      const body = renderTab(patient, 'default');

      expect(body).toMatch(/title="Data absent reason"[^>]*>\s*absent: masked\s*</);
      expect(body).toContain('(has extensions)');
    });

    it('renders complex values', () => {
      const patient: Patient = {
        resourceType: 'Patient',
        name: [{ given: ['given-name'], family: 'family-name' }]
      };

      expect(renderTab(patient, 'default')).toContain('given-name family-name');
    });

    it('renders elements defined by content reference as backbone elements', () => {
      const claimResponse: ClaimResponse = {
        resourceType: 'ClaimResponse',
        status: 'active',
        type: { text: 'type-text' },
        use: 'claim',
        patient: { reference: 'Patient/0' },
        created: '2020-01-02',
        insurer: { reference: 'Organization/0' },
        outcome: 'complete',
        adjudication: [{ category: { text: 'category-text' } }]
      };

      const body = renderTab(claimResponse, 'default');

      expect(body).toMatch(/>\s*Adjudication\s*</);
      expect(body).toContain('&lt;backbone-element>');
    });

    it('omits id and narrative', () => {
      const patient: Patient = {
        resourceType: 'Patient',
        id: 'id-value',
        text: { status: 'generated', div: '<div>narrative</div>' }
      };

      const body = renderTab(patient, 'default');

      expect(body).not.toMatch(/>\s*Id\s*</);
      expect(body).not.toMatch(/>\s*Text\s*</);
    });
  });

  describe('json view', () => {
    it('renders extensions of primitive values in arrays', () => {
      const questionnaire: Questionnaire = {
        resourceType: 'Questionnaire',
        status: 'active',
        subjectType: ['Patient', 'Group'],
        _subjectType: [null, { extension }] as Questionnaire['_subjectType']
      };

      const body = renderTab(questionnaire, 'json');

      expect(body).toContain('"_subjectType"');
      expect(body).toContain('null');
      expect(body).toContain('"ext-value"');
    });

    it('renders the id of primitive values', () => {
      const patient: Patient = {
        resourceType: 'Patient',
        birthDate: '2020-01-02',
        _birthDate: { id: 'element-id' }
      };

      expect(renderTab(patient, 'json')).toContain('"element-id"');
    });

    it('renders unknown properties', () => {
      const resource = { resourceType: 'Patient', foo: 'bar' } as unknown as Patient;

      const body = renderTab(resource, 'json');

      expect(body).toContain('"foo"');
      expect(body).toContain('"bar"');
    });

    it('renders properties in JSON order', () => {
      const patient: Patient = { resourceType: 'Patient', gender: 'male', active: true };

      const body = renderTab(patient, 'json');

      expect(body.indexOf('"resourceType"')).toBeLessThan(body.indexOf('"gender"'));
      expect(body.indexOf('"gender"')).toBeLessThan(body.indexOf('"active"'));
    });

    it('renders numbers and booleans unquoted', () => {
      const observation: Observation = {
        resourceType: 'Observation',
        status: 'final',
        code: { text: 'code-text' },
        valueQuantity: { value: 23 },
        _status: { extension: [{ url: 'http://example.com/ext', valueBoolean: false }] }
      };

      const body = renderTab(observation, 'json');

      expect(body).toMatch(/>23</);
      expect(body).toMatch(/>false</);
    });

    it('escapes strings', () => {
      const patient = {
        resourceType: 'Patient',
        gender: 'a"b'
      } as unknown as Patient;

      expect(renderTab(patient, 'json')).toContain('a\\"b');
    });
  });
});
