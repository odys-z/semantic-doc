// Dev page of the doc tree editor, over the stub tier (hard-coded json).
// Uses the widget only through the public API, like a host app would.
import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import 'katex/dist/katex.min.css'; // host app provides KaTeX CSS (see editor/index.ts)
import './demo.css';
import { AnDoctreeditor, type TreeModify } from '../doctree';
import { StubDoctreeTier, describe, stubDocs } from './stub-tier';

const stub = new StubDoctreeTier();

function Doctree() {
  const [docId, setDocId] = useState('doc-a');
  const [log, setLog] = useState<TreeModify[]>([]);
  const [failSaves, setFailSaves] = useState(false);
  stub.failSaves = failSaves;
  stub.onLog = setLog;
  return (
    <>
      <h2>semantic-doc · doc tree</h2>
      <nav className="demo-nav"><a href="index.html">Paragraph editor</a> · Doc tree</nav>
      <section className="demo-block demo-doctree">
        <h3>
          <select value={docId} onChange={(e) => setDocId(e.target.value)}>
            {Object.keys(stubDocs).map((d) => <option key={d}>{d}</option>)}
          </select>
          <label className="demo-toggle">
            <input type="checkbox" checked={failSaves} onChange={(e) => setFailSaves(e.target.checked)} /> simulate save failure
          </label>
        </h3>
        {/* preview: the template in web-dist/, served by the dev server (make-template.py) */}
        <AnDoctreeditor key={docId} docId={docId} doctier={stub} height="70vh" preview="sdoc-template/" />
        <details>
          <summary>Structure requests ({log.length})</summary>
          <pre data-testid="requests">{log.map(describe).join('\n')}</pre>
        </details>
      </section>
    </>
  );
}

createRoot(document.getElementById('root')!).render(<Doctree />);
