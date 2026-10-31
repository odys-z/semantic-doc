import { getSchema } from '@tiptap/core';
import { Node } from '@tiptap/pm/model';
import { rstExtensions } from '../editor/marks';
import { serializeDoc } from '../editor/rst/serialize';
import type { Case } from './cases/inline';

export interface Result {
  name: string;
  pass: boolean;
  expected: string;
  actual: string;
  error?: string;
}

const schema = getSchema(rstExtensions);

export function run(cases: Case[]): Result[] {
  return cases.map((c) => {
    try {
      const out = serializeDoc(Node.fromJSON(schema, c.doc));
      const rolesOk = JSON.stringify(out.roles) === JSON.stringify(c.roles ?? []);
      return { name: c.name, pass: out.body === c.rst && rolesOk, expected: c.rst, actual: out.body };
    } catch (e) {
      return { name: c.name, pass: false, expected: c.rst, actual: '', error: String(e) };
    }
  });
}
