// Dev page. Uses the widget only through the public API, like a host app would.
import React, { useState } from 'react';
import { ParagraphEditor, type JSONContent, type RstResult } from '../editor';

const t = (text: string, type?: string, attrs?: Record<string, unknown>): JSONContent =>
  type ? { type: 'text', text, marks: [{ type, attrs }] } : { type: 'text', text };

const paragraph: JSONContent = {
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: [
        t('Formats: '), t('strong', 'strong'), t(', '), t('emphasis', 'emphasis'), t(', '),
        t('underline', 'role', { name: 'underline' }), t(', '), t('strike', 'role', { name: 'strike' }), t(', '),
        t('code()', 'literal'), t(', H'), t('2', 'sub'), t('O, x'), t('2', 'sup'), t(', a '),
        t('link', 'link', { href: 'https://docutils.sourceforge.io' }), t(', '),
        t('larger', 'role', { name: 'large' }), t(', '), t('red', 'role', { name: 'red' }), t(', '),
        t('highlight', 'role', { name: 'bg-yellow' }), t(' and a formula '),
        { type: 'inlineMath', attrs: { latex: 'E = mc^2' } },
        t('. Click a formula to edit it, or type $$x^2$$.'),
      ],
    },
  ],
};

const formula: JSONContent = {
  type: 'doc',
  content: [{ type: 'blockMath', attrs: { latex: '\\int_0^1 x^2\\,dx = \\frac{1}{3}' } }],
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

export function App() {
  return (
    <>
      <h2>semantic-doc · paragraph editor</h2>
      <Block title="Paragraph" content={paragraph} />
      <Block title="Block formula" content={formula} />
    </>
  );
}
