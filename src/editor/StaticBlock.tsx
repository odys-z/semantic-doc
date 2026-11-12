import React, { useLayoutEffect, useMemo, useRef } from 'react';
import { generateHTML, type JSONContent } from '@tiptap/core';
import katex from 'katex';
import { rstExtensions } from './rst/marks';
import './styles/theme-roles.css';
import './styles/editor.css';

export interface StaticBlockProps {
  /** Tiptap JSON: one paragraph or one blockMath */
  json: JSONContent;
  onClick?: (e: React.MouseEvent<HTMLDivElement>) => void;
  className?: string;
}

/**
 * Read-only view of one block, looking the same as ParagraphEditor but without
 * an editor instance: many rows of a tree can show it, while only one row
 * mounts a ParagraphEditor. Formulas are rendered with KaTeX after mounting.
 */
export function StaticBlock({ json, onClick, className }: StaticBlockProps) {
  const html = useMemo(() => generateHTML(json, rstExtensions), [json]);
  const ref = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    ref.current?.querySelectorAll<HTMLElement>('[data-latex]').forEach((el) => {
      try {
        katex.render(el.dataset.latex ?? '', el, {
          displayMode: el.dataset.type === 'block-math',
          throwOnError: false,
        });
      } catch {
        el.textContent = el.dataset.latex ?? '';
      }
    });
  }, [html]);

  return (
    <div className={'sd-editor sd-readonly' + (className ? ' ' + className : '')} onClick={onClick}>
      <div className="sd-content">
        <div className="tiptap" ref={ref} dangerouslySetInnerHTML={{ __html: html }} />
      </div>
    </div>
  );
}
