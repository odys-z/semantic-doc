import React, { useState } from 'react';
import type { JSONContent } from '@tiptap/react';
import { ParagraphEditor } from './components/ParagraphEditor';
import type { RstResult } from './rst/serialize';

const sample: JSONContent = {
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: [
        { type: 'text', text: 'Select text and use the toolbar: ' },
        { type: 'text', text: 'strong', marks: [{ type: 'strong' }] },
        { type: 'text', text: ', ' },
        { type: 'text', text: 'emphasis', marks: [{ type: 'emphasis' }] },
        { type: 'text', text: ', ' },
        { type: 'text', text: 'code()', marks: [{ type: 'literal' }] },
        { type: 'text', text: ', a ' },
        { type: 'text', text: 'link', marks: [{ type: 'link', attrs: { href: 'https://docutils.sourceforge.io' } }] },
        { type: 'text', text: ' and ' },
        { type: 'text', text: 'larger text', marks: [{ type: 'role', attrs: { name: 'large' } }] },
        { type: 'text', text: '. Mid-word too: ' },
        { type: 'text', text: 'bold', marks: [{ type: 'strong' }] },
        { type: 'text', text: 'er.' },
      ],
    },
  ],
};

export function App() {
  const [rst, setRst] = useState<RstResult>();
  const [json, setJson] = useState<JSONContent>();

  return (
    <>
      <h2>semantic-doc · paragraph editor</h2>
      <ParagraphEditor
        content={sample}
        onChange={(j, r) => {
          setJson(j);
          setRst(r);
        }}
      />
      <div className="sd-out">
        <h3>RST</h3>
        <pre data-testid="rst">{rst?.rst}</pre>
        <details>
          <summary>Tiptap JSON</summary>
          <pre>{JSON.stringify(json, null, 2)}</pre>
        </details>
      </div>
    </>
  );
}
