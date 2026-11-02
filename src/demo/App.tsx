// Dev page. Uses the widget only through the public API, like a host app would.
import React, { useState } from 'react';
import { ParagraphEditor, type JSONContent, type RstResult } from '../editor';
import { AnDoctreeditor, type TreeModify } from '../doctree';
import { StubDoctreeTier, describe, stubDocs } from './stub-tier';

type M = string | [string, Record<string, unknown>];
/** text with any number of marks: t('x', 'strong', ['color', { name: 'red' }]) */
const t = (text: string, ...marks: M[]): JSONContent => ({
  type: 'text',
  text,
  ...(marks.length && { marks: marks.map((m) => (typeof m === 'string' ? { type: m } : { type: m[0], attrs: m[1] })) }),
});
const red: M = ['color', { name: 'red' }];
const blue: M = ['color', { name: 'blue' }];
const bgYellow: M = ['background', { name: 'bg-yellow' }];
const large: M = ['size', { name: 'large' }];
const link: M = ['link', { href: 'https://docutils.sourceforge.io' }];

const paragraph: JSONContent = {
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: [
        t('Formats: '), t('strong', 'strong'), t(', '), t('emphasis', 'emphasis'), t(', '),
        t('underline', 'underline'), t(', '), t('strike', 'strike'), t(', '),
        t('code()', 'literal'), t(', H'), t('2', 'sub'), t('O, x'), t('2', 'sup'), t(', a '),
        t('link', link), t(', '), t('larger', large), t(', '), t('red', red), t(', '),
        t('highlight', bgYellow), t(' and a formula '),
        { type: 'inlineMath', attrs: { latex: 'E = mc^2' } },
        t('. Combined: '), t('struck red on yellow', 'strike', red, bgYellow), t(', '),
        t('bold italic underlined', 'strong', 'emphasis', 'underline'), t(', '),
        t('blue bold link', link, 'strong', blue), t(', a coloured formula '),
        { type: 'inlineMath', attrs: { latex: '\\frac{a}{b}' }, marks: [{ type: 'color', attrs: { name: 'red' } }, { type: 'background', attrs: { name: 'bg-yellow' } }] },
        t('. Click a formula to edit it, or type $$x^2$$.'),
      ],
    },
  ],
};

const formula: JSONContent = {
  type: 'doc',
  content: [{ type: 'blockMath', attrs: { latex: '\\int_0^1 x^2\\,dx = \\frac{1}{3}', color: 'blue' } }],
};

function Block({ title, content }: { title: string; content: JSONContent }) {
  const [rst, setRst] = useState<RstResult>();
  const [json, setJson] = useState<JSONContent>();
  const [editable, setEditable] = useState(true);
  return (
    <section className="demo-block">
      <h3>
        {title}{' '}
        <label className="demo-toggle">
          <input type="checkbox" checked={editable} onChange={(e) => setEditable(e.target.checked)} /> editable
        </label>
      </h3>
      <ParagraphEditor content={content} editable={editable} onChange={(j, r) => { setJson(j); setRst(r); }} />
      <pre className="demo-rst" data-testid="rst">{rst?.rst}</pre>
      <details>
        <summary>Tiptap JSON</summary>
        <pre>{JSON.stringify(json, null, 2)}</pre>
      </details>
    </section>
  );
}

const stub = new StubDoctreeTier();

/** the doc tree editor over the stub tier (hard-coded json) */
function Doctree() {
  const [docId, setDocId] = useState('doc-a');
  const [log, setLog] = useState<TreeModify[]>([]);
  stub.onLog = setLog;
  return (
    <section className="demo-block demo-doctree">
      <h3>
        Doc tree{' '}
        <select value={docId} onChange={(e) => setDocId(e.target.value)}>
          {Object.keys(stubDocs).map((d) => <option key={d}>{d}</option>)}
        </select>
      </h3>
      <AnDoctreeditor key={docId} docId={docId} doctier={stub} />
      <details>
        <summary>Structure requests ({log.length})</summary>
        <pre data-testid="requests">{log.map(describe).join('\n')}</pre>
      </details>
    </section>
  );
}

export function App() {
  return (
    <>
      <h2>semantic-doc · doc tree</h2>
      <Doctree />
      <h2>semantic-doc · paragraph editor</h2>
      <Block title="Paragraph" content={paragraph} />
      <Block title="Block formula" content={formula} />
    </>
  );
}
