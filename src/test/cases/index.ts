import { inlineCases } from './inline';
import { formatCases } from './format';
import { mathCases } from './math';
import { combineCases } from './combine';

export type { Case } from './inline';

/** every case group; npm test and /test.html run all of them */
export const allCases = [...inlineCases, ...formatCases, ...mathCases, ...combineCases];
