import { getSchema } from '@tiptap/core';
import { Node } from '@tiptap/pm/model';
// pure RST modules only: no React / CSS, so this also runs in Node
import { rstExtensions } from '../editor/rst/marks';
import { serializeDoc } from '../editor/rst/serialize';
import type { Case } from './cases';

export interface Result {
  name: string;
  pass: boolean;
  expected: string;
  actual: string;
  /** complete RST fragment (role declarations + body), for compiling with docutils */
  rst: string;
  error?: string;
}

const schema = getSchema(rstExtensions);

export function run(cases: Case[]): Result[] {
  return cases.map((c) => {
    try {
      const out = serializeDoc(Node.fromJSON(schema, c.doc));
      const rolesOk = JSON.stringify(out.roles) === JSON.stringify(c.roles ?? []);
      const fullOk = c.full === undefined || c.full === out.rst;
      const pass = out.body === c.rst && rolesOk && fullOk;
      // on failure show what differs: the body, else the complete fragment / roles
      const [expected, actual] =
        out.body !== c.rst || pass ? [c.rst, out.body]
        : !fullOk ? [c.full!, out.rst]
        : [`roles ${JSON.stringify(c.roles ?? [])}`, `roles ${JSON.stringify(out.roles)}`];
      return { name: c.name, pass, expected, actual, rst: out.rst };
    } catch (e) {
      return { name: c.name, pass: false, expected: c.rst, actual: '', rst: '', error: String(e) };
    }
  });
}
